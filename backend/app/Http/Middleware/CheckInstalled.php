<?php

namespace App\Http\Middleware;

use App\Http\Controllers\DatabaseUpdateController;
use App\Support\FirstRunSetupState;
use App\Support\OneTimeFrontendMigrationState;
use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

class CheckInstalled
{
    public function handle(Request $request, Closure $next): Response
    {
        if (app()->environment('testing') && !config('safa.enforce_update_checks_in_tests', false)) {
            return $next($request);
        }

        $path = ltrim($request->path(), '/');

        if ($this->isRetiredInstallerPath($path)) {
            return $this->notFound($request);
        }

        if ($this->isPublicAssetPath($path)) {
            return $next($request);
        }

        // The owner's requested first visible production step is a one-time
        // frontend migration action. Its availability is independent of whether
        // identity/business tables already contain rows; only its dedicated
        // durable completion marker can consume it.
        if (OneTimeFrontendMigrationState::required()) {
            if ($path === 'data-migration') {
                return $next($request);
            }

            if ($request->expectsJson() || $request->is('api/*') || $request->is('app/api/*')) {
                return response()->json([
                    'status' => 'data_migration_required',
                    'message' => 'One-time data migration is required.',
                    'migration_path' => '/data-migration',
                ], 503);
            }

            return redirect()->route('frontend.migration.show');
        }

        // Once consumed, the one-time migration surface is permanently hard
        // closed even when a later release introduces ordinary pending migrations.
        if ($path === 'data-migration') {
            return $this->notFound($request);
        }

        if ($path === 'api/setup/status') {
            return $next($request);
        }

        if (FirstRunSetupState::databaseInitializationRequired()) {
            if ($path === 'setup' || $path === 'setup/database') {
                return $next($request);
            }

            return $this->setupRequired($request, 'database');
        }

        if (FirstRunSetupState::adminCompletionRequired()) {
            if ($path === 'setup/database') {
                return $this->notFound($request);
            }
            if ($path === 'setup' || $path === 'setup/admin') {
                return $next($request);
            }

            return $this->setupRequired($request, 'admin');
        }

        if ($path === 'setup' || str_starts_with($path, 'setup/')) {
            return $this->notFound($request);
        }

        if ($this->isUpdateExemptPath($path)) {
            return $next($request);
        }

        try {
            $pending = DatabaseUpdateController::pendingMigrations();
        } catch (\Throwable $e) {
            report($e);
            $pending = [];
        }

        if ($pending !== []) {
            if ($request->expectsJson() || $request->is('api/*') || $request->is('app/api/*')) {
                return response()->json([
                    'status' => 'update_required',
                    'message' => 'Database update required.',
                    'pending_count' => count($pending),
                ], 503);
            }

            return redirect()->route('system.update.show');
        }

        return $next($request);
    }

    private function setupRequired(Request $request, string $phase): Response
    {
        if ($request->expectsJson() || $request->is('api/*') || $request->is('app/api/*')) {
            return response()->json([
                'status' => 'setup_required',
                'message' => 'First-run setup is required.',
                'phase' => $phase,
                'setup_path' => '/setup',
            ], 503);
        }

        return redirect()->route('setup.index');
    }

    private function isUpdateExemptPath(string $path): bool
    {
        return $path === 'login'
            || $path === 'logout'
            || $path === 'update'
            || str_starts_with($path, 'update/');
    }

    private function isPublicAssetPath(string $path): bool
    {
        foreach ([
            'safa-logo.png',
            'favicon.svg',
            'safa-web.css',
            'safa-web-product.css',
            'safa-web.js',
            'safa-web-events.js',
            'safa-web-product.js',
        ] as $asset) {
            if ($path === $asset) return true;
        }

        return str_starts_with($path, 'storage/logos/');
    }

    private function isRetiredInstallerPath(string $path): bool
    {
        return $path === 'index'
            || $path === 'install'
            || str_starts_with($path, 'install/')
            || $path === 'update-db'
            || $path === 'system/update'
            || str_starts_with($path, 'system/update/');
    }

    private function notFound(Request $request): Response
    {
        if ($request->expectsJson()) {
            return response()->json(['status' => 'not_found'], 404);
        }

        abort(404);
    }
}
