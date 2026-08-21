<?php

namespace App\Support;

use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;
use RuntimeException;

final class OneTimeFrontendMigrationState
{
    public const TABLE = 'safa_frontend_migration_state';

    public static function required(): bool
    {
        // Existing test suites opt into install/update middleware for their own
        // contracts. Keep this new gate isolated unless a test explicitly asks
        // to exercise the one-time frontend migration lifecycle.
        if (app()->environment('testing') && !config('safa.enforce_frontend_migration_in_tests', false)) {
            return false;
        }

        try {
            if (!Schema::hasTable(self::TABLE)) {
                return true;
            }

            return !DB::table(self::TABLE)
                ->where('id', 1)
                ->whereNotNull('completed_at')
                ->exists();
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
