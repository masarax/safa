<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\DB;
use Symfony\Component\HttpFoundation\Response;

class CheckInstalled
{
    /**
     * Handle an incoming request.
     *
     * @param  \Illuminate\Http\Request  $request
     * @param  \Closure(\Illuminate\Http\Request): (\Symfony\Component\HttpFoundation\Response)  $next
     * @return \Symfony\Component\HttpFoundation\Response
     */
    public function handle(Request $request, Closure $next): Response
    {
        $isInstalled = file_exists(storage_path('installed')) || env('APP_INSTALLED') == true || env('APP_INSTALLED') === 'true';

        if (!$isInstalled) {
            if ($request->is('api/*')) {
                return response()->json([
                    'status' => 'error',
                    'message' => 'System installation required. Please visit /install to complete setup.'
                ], 503);
            }

            if (!$request->expectsJson() && !$request->is('install*')) {
                return redirect('/install');
            }
        } else {
            // Auto-heal / Auto-migrate new tables when new code is pushed (Zero Data Loss)
            $this->autoMigrateIfPending();
        }

        return $next($request);
    }

    /**
     * Safely checks for un-executed migration files and runs migrate --force without dropping data.
     */
    protected function autoMigrateIfPending(): void
    {
        try {
            $migrationFiles = glob(database_path('migrations/*.php'));
            if (empty($migrationFiles)) {
                return;
            }

            if (!DB::schema()->hasTable('migrations')) {
                return;
            }

            $executedMigrations = DB::table('migrations')->pluck('migration')->toArray();
            $hasPending = false;

            foreach ($migrationFiles as $file) {
                $name = basename($file, '.php');
                if (!in_array($name, $executedMigrations)) {
                    $hasPending = true;
                    break;
                }
            }

            if ($hasPending) {
                Artisan::call('migrate', ['--force' => true]);
            }
        } catch (\Throwable $e) {
            // Silently ignore during runtime if DB connection is unavailable
        }
    }
}
