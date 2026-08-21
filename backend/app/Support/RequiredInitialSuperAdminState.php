<?php

namespace App\Support;

use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;
use RuntimeException;

final class RequiredInitialSuperAdminState
{
    public const TABLE = 'safa_required_superadmin_state';

    public static function completed(): bool
    {
        try {
            return Schema::hasTable(self::TABLE)
                && DB::table(self::TABLE)
                    ->where('id', 1)
                    ->whereNotNull('completed_at')
                    ->exists();
        } catch (\Throwable $e) {
            report($e);
            return false;
        }
    }

    public static function markCompleted(): void
    {
        if (!Schema::hasTable(self::TABLE)) {
            throw new RuntimeException('Required SuperAdmin provisioning state table is unavailable.');
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
