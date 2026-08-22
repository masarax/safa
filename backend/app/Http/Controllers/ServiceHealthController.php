<?php

namespace App\Http\Controllers;

use App\Support\RuntimeSchemaContract;
use Illuminate\Http\JsonResponse;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

class ServiceHealthController extends Controller
{
    public function __invoke(): JsonResponse
    {
        $build = $this->buildIdentity();
        $databaseReady = $this->databaseReady();
        $checks = [
            'runtime' => PHP_VERSION_ID >= 80300
                && extension_loaded('pdo')
                && extension_loaded('mbstring')
                && extension_loaded('openssl'),
            'database' => $databaseReady,
            'schema' => $databaseReady && $this->schemaReady(),
            'cache' => $databaseReady && $this->cacheAndSessionStoresReady(),
            'storage' => $this->storageReady(),
            'build' => preg_match('/^[a-f0-9]{40}$/', $build) === 1
                || (!app()->environment('production') && $build === 'development'),
        ];
        $ready = !in_array(false, $checks, true);

        return response()->json([
            'status' => $ready ? 'ok' : 'degraded',
            'service' => 'SAFA API',
            'build' => $build,
            'checks' => $checks,
        ], $ready ? 200 : 503)->header('Cache-Control', 'no-store');
    }

    private function buildIdentity(): string
    {
        $metadata = json_decode((string) @file_get_contents(base_path('bootstrap/safa-build.json')), true);
        $commit = is_array($metadata) ? (string) ($metadata['commit'] ?? '') : '';
        if ($commit === 'development') return $commit;
        return preg_match('/^[a-f0-9]{40}$/', $commit) === 1 ? $commit : 'unknown';
    }

    private function databaseReady(): bool
    {
        try {
            DB::connection()->getPdo();
            return true;
        } catch (\Throwable) {
            return false;
        }
    }

    private function schemaReady(): bool
    {
        try {
            foreach (RuntimeSchemaContract::requiredColumns() as $table => $columns) {
                if (!Schema::hasTable($table) || !Schema::hasColumns($table, $columns)) return false;
            }

            foreach (RuntimeSchemaContract::requiredUniqueIndexes() as $table => $requiredIndexes) {
                $actualIndexes = Schema::getIndexes($table);
                foreach ($requiredIndexes as $requiredColumns) {
                    $found = false;
                    foreach ($actualIndexes as $index) {
                        $columns = array_values(array_map('strval', $index['columns'] ?? []));
                        $unique = (bool) ($index['unique'] ?? false) || (bool) ($index['primary'] ?? false);
                        if ($unique && $columns === $requiredColumns) {
                            $found = true;
                            break;
                        }
                    }
                    if (!$found) return false;
                }
            }

            return true;
        } catch (\Throwable) {
            return false;
        }
    }

    private function cacheAndSessionStoresReady(): bool
    {
        try {
            if ((string) config('cache.default') === 'database') {
                $table = (string) config('cache.stores.database.table', 'cache');
                $lockTable = (string) (config('cache.stores.database.lock_table') ?: 'cache_locks');
                if ($table === '' || $lockTable === '' || !Schema::hasTable($table) || !Schema::hasTable($lockTable)) return false;
            }
            if ((string) config('session.driver') === 'database') {
                $table = (string) config('session.table', 'sessions');
                if ($table === '' || !Schema::hasTable($table)) return false;
            }
            return true;
        } catch (\Throwable) {
            return false;
        }
    }

    private function storageReady(): bool
    {
        foreach ([storage_path('framework'), storage_path('logs'), base_path('bootstrap/cache')] as $path) {
            if (!is_dir($path) || !is_writable($path)) return false;
        }
        return true;
    }
}
