<?php

namespace App\Support;

use App\Services\RequiredInitialSuperAdminService;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;
use RuntimeException;

final class OneTimeFrontendMigrationState
{
    public const TABLE = 'safa_frontend_migration_state';

    public static function required(): bool
    {
        // Existing test suites opt into install/update middleware for their own
        // contracts. Keep this gate isolated unless a test explicitly asks to
        // exercise the one-time frontend migration lifecycle.
        if (app()->environment('testing') && !config('safa.enforce_frontend_migration_in_tests', false)) {
            return false;
        }

        try {
            $originalMigrationIncomplete = !Schema::hasTable(self::TABLE)
                || !DB::table(self::TABLE)
                    ->where('id', 1)
                    ->whereNotNull('completed_at')
                    ->exists();

            if ($originalMigrationIncomplete) {
                return true;
            }

            // PR #208 shipped after some installations had already consumed the
            // old migration marker without an owner row. A separate durable repair
            // generation lets only those broken installs reopen this page once.
            if (RequiredInitialSuperAdminState::completed()) {
                return false;
            }

            $requiredAdmin = app(RequiredInitialSuperAdminService::class);
            if ($requiredAdmin->needsProvisioning()) {
                return true;
            }

            // If the exact owner already exists and the repair table has already
            // been migrated, consume the repair generation without showing UI.
            if (Schema::hasTable(RequiredInitialSuperAdminState::TABLE)) {
                RequiredInitialSuperAdminState::markCompleted();
            }

            return false;
        } catch (\Throwable $e) {
            report($e);
            return true;
        }
    }

    public static function markCompleted(): void
    {
        if (!Schema::hasTable(self::TABLE)) {
            throw new RuntimeException('One-time frontend migration state table is unavailable.');
        }

        $now = now();
        DB::table(self::TABLE)->updateOrInsert(
            ['id' => 1],
            [
                'completed_at' => $now,
                'created_at' => $now,
                'updated_at' => $now,
            ]
        );
    }
}
