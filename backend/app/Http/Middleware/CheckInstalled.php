<?php

namespace App\Http\Middleware;

use App\Support\ReleaseUpdateState;
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

        $updateRequired = ReleaseUpdateState::required();
        if ($updateRequired) {
            if ($this->isReleaseUpdatePath($path)) {
                return $next($request);
            }

            if ($request->expectsJson() || $request->is('api/*') || $request->is('app/api/*')) {
                return response()->json([
                    'status' => 'update_required',
                    'message' => 'System update required.',
                    'update_path' => '/update',
                ], 503);
            }

            return redirect()->route('release.update.show');
        }

        if ($this->isReleaseUpdatePath($path)) {
            return $this->notFound($request);
        }

        return $next($request);
    }

    private function isReleaseUpdatePath(string $path): bool
    {
        return $path === 'update' || $path === 'update/run';
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
            if ($path === $asset) {
                return true;
            }
        }

        return str_starts_with($path, 'storage/logos/');
    }

    private function isRetiredInstallerPath(string $path): bool
    {
        return $path === 'index'
            || $path === 'install'
            || str_starts_with($path, 'install/')
            || $path === 'update-db'
            || $path === 'data-migration'
            || str_starts_with($path, 'data-migration/')
            || $path === 'setup'
            || str_starts_with($path, 'setup/')
            || $path === 'api/setup/status'
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
