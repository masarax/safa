<?php

namespace App\Http\Middleware;

use App\Http\Controllers\DatabaseUpdateController;
use App\Support\FirstRunSetupState;
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

        // Legacy installer/recovery endpoints stay permanently retired. The new
        // first-run surface uses a separate state-gated URL and cannot be reused
        // for ordinary future migrations.
        if ($this->isRetiredInstallerPath($path)) {
            return $this->notFound($request);
        }

        if ($this->isPublicAssetPath($path)) {
            return $next($request);
        }

        if (FirstRunSetupState::databaseInitializationRequired()) {
            if ($path === 'setup/database') {
                return $next($request);
            }

            return $this->setupRequired($request, 'database');
        }

        if (FirstRunSetupState::adminCompletionRequired()) {
            // The migration action is hard-closed immediately after migrations
            // complete. Only the first-admin completion step remains available.
            if ($path === 'setup/database') {
                return $this->notFound($request);
            }
            if ($path === 'setup/admin') {
                return $next($request);
            }

            return $this->setupRequired($request, 'admin');
        }

        // Once installation is complete, first-run URLs are indistinguishable
        // from routes that never existed and can never become public again merely
        // because a future release has pending migrations.
        if ($path === 'setup/database' || $path === 'setup/admin' || str_starts_with($path, 'setup/')) {
            return $this->notFound($request);
        }

        // Login/logout and the canonical authenticated update endpoint must remain
        // reachable while a release has pending forward migrations.
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
            ], 503);
        }

        return redirect()->route($phase === 'database' ? 'setup.database.show' : 'setup.admin.show');
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
