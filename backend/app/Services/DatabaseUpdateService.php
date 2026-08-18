<?php

namespace App\Services;

use App\Http\Controllers\DatabaseUpdateController;
use App\Models\User;
use App\Support\ProductionMigrationSafety;
use Database\Seeders\ReleaseDataUpdateSeeder;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\File;
use Illuminate\Support\Facades\Log;
use RuntimeException;

class DatabaseUpdateService
{
    public const LOCK_FILE = 'framework/safa-database-update.lock';

    /**
     * @return array{busy: bool, migrated: int}
     */
    public function run(User $actor): array
    {
        $lockPath = storage_path(self::LOCK_FILE);
        File::ensureDirectoryExists(dirname($lockPath));

        $handle = @fopen($lockPath, 'c+');
        if ($handle === false) {
            throw new RuntimeException('Unable to open the database update lock.');
        }

        $locked = false;

        try {
            $locked = flock($handle, LOCK_EX | LOCK_NB);
            if (!$locked) {
                return ['busy' => true, 'migrated' => 0];
            }

            $pendingBefore = DatabaseUpdateController::pendingMigrations();
            ProductionMigrationSafety::assertPendingMigrationsAreSafe($pendingBefore);

            Log::info('SAFA database update started.', [
                'user_id' => (int) $actor->id,
                'pending_migrations' => count($pendingBefore),
            ]);

            if ($pendingBefore) {
                if (Artisan::call('migrate', ['--force' => true]) !== 0) {
                    throw new RuntimeException('Forward migration command failed.');
                }
            }

            if (Artisan::call('db:seed', [
                '--class' => ReleaseDataUpdateSeeder::class,
                '--force' => true,
            ]) !== 0) {
                throw new RuntimeException('Release data update failed.');
            }

            if (Artisan::call('optimize:clear') !== 0) {
                throw new RuntimeException('Application cache clear failed.');
            }

            $pendingAfter = DatabaseUpdateController::pendingMigrations();
            if ($pendingAfter) {
                throw new RuntimeException('Database update completed with pending migrations remaining.');
            }

            Log::info('SAFA database update completed.', [
                'user_id' => (int) $actor->id,
                'migrations_applied' => count($pendingBefore),
            ]);

            return [
                'busy' => false,
                'migrated' => count($pendingBefore),
            ];
        } catch (\Throwable $e) {
            Log::error('SAFA database update failed.', [
                'user_id' => (int) $actor->id,
                'exception' => $e::class,
            ]);
            report($e);

            throw $e;
        } finally {
            if ($locked) {
                flock($handle, LOCK_UN);
            }
            fclose($handle);
        }
    }
}
