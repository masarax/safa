<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\DB;
use Symfony\Component\HttpFoundation\Response;

class EnsureNotInstalled
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

        if ($isInstalled) {
            if ($request->is('install/success')) {
                return $next($request);
            }

            // If there are new migration files, allow accessing update endpoint
            if ($request->is('install/update-db') || $this->hasPendingMigrations()) {
                return $next($request);
            }

            if ($request->expectsJson() || $request->is('api/*')) {
                return response()->json([
                    'status' => 'error',
                    'message' => 'System already installed and database is up to date.'
                ], 403);
            }

            return redirect('/');
        }

        return $next($request);
    }

    /**
     * Checks if there are unexecuted migration files.
     */
    protected function hasPendingMigrations(): bool
    {
        try {
            $migrationFiles = glob(database_path('migrations/*.php'));
            if (empty($migrationFiles)) {
                return false;
            }

            if (!DB::schema()->hasTable('migrations')) {
                return false;
            }

            $executedMigrations = DB::table('migrations')->pluck('migration')->toArray();
            foreach ($migrationFiles as $file) {
                $name = basename($file, '.php');
                if (!in_array($name, $executedMigrations)) {
                    return true;
                }
            }
        } catch (\Throwable $e) {
            return false;
        }
        return false;
    }
}
