<?php

namespace App\Http\Middleware;

use App\Http\Controllers\DatabaseUpdateController;
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

        // First installation is CLI-owned. Public installer/recovery surfaces are
        // permanently retired and must remain indistinguishable from missing URLs.
        if ($this->isRetiredInstallerPath($path)) {
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

    private function isUpdateExemptPath(string $path): bool
    {
        if ($path === 'login' || $path === 'logout' || $path === 'update' || str_starts_with($path, 'update/')) {
            return true;
        }

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
