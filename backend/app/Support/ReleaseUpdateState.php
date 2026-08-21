<?php

namespace App\Support;

use App\Http\Controllers\DatabaseUpdateController;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;
use RuntimeException;

final class ReleaseUpdateState
{
    public const TABLE = 'safa_release_update_state';

    public static function required(): bool
    {
        if (app()->environment('testing') && !config('safa.enforce_release_update_in_tests', false)) {
            return false;
        }

        try {
            if (DatabaseUpdateController::pendingMigrations() !== []) {
                return true;
            }

            if (!Schema::hasTable(self::TABLE) || !Schema::hasColumn(self::TABLE, 'release_fingerprint')) {
                return true;
            }

            $applied = DB::table(self::TABLE)->where('id', 1)->value('release_fingerprint');

            return !is_string($applied) || !hash_equals(self::fingerprint(), $applied);
        } catch (\Throwable $e) {
            report($e);
            return true;
        }
    }

    public static function markApplied(): void
    {
        if (!Schema::hasTable(self::TABLE) || !Schema::hasColumn(self::TABLE, 'release_fingerprint')) {
            throw new RuntimeException('Release update state table is unavailable.');
        }

        $now = now();
        DB::table(self::TABLE)->updateOrInsert(
            ['id' => 1],
            [
                'release_fingerprint' => self::fingerprint(),
                'applied_at' => $now,
                'created_at' => $now,
                'updated_at' => $now,
            ]
        );
    }

    public static function fingerprint(): string
    {
        $files = array_merge(
            glob(database_path('migrations/*.php')) ?: [],
            glob(database_path('seeders/*.php')) ?: [],
        );
        sort($files, SORT_STRING);

        $hash = hash_init('sha256');
        foreach ($files as $file) {
            hash_update($hash, str_replace(base_path() . DIRECTORY_SEPARATOR, '', $file));
            hash_update($hash, "\0");
            hash_update($hash, (string) file_get_contents($file));
            hash_update($hash, "\0");
        }

        return hash_final($hash);
    }
}
