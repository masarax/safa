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
        if ($request->is('index') || $request->is('index/*')) {
            return response()->json(['status' => 'not_found'], 404);
        }

        if (app()->environment('testing') && !(bool) config('safa.enforce_update_checks_in_tests', false)) {
            return $next($request);
        }

        // Maintenance, authentication, and exact public presentation assets must
        // remain reachable while an empty database is being recovered. The
        // maintenance controller independently protects every write operation.
        if ($request->is('install*')
            || $request->is('system/update*')
            || $request->is('login')
            || $request->is('logout')
            || $request->is('safa-logo.png')
            || $request->is('favicon.svg')
            || $request->is('safa-web.css')
            || $request->is('safa-web-product.css')
            || $request->is('safa-web.js')
            || $request->is('safa-web-events.js')
            || $request->is('safa-web-product.js')
            || $request->is('storage/logos/*')) {
            return $next($request);
        }

        $isInstalled = file_exists(storage_path('installed')) || (bool) config('safa.installed', false);
        if (!$isInstalled) {
            if ($this->expectsApiResponse($request)) {
                return response()->json([
                    'status' => 'maintenance_required',
                    'message' => 'System maintenance is required before this request can be served.',
                ], 503);
            }

            return redirect()->route('system.update.show');
        }

        try {
            $pending = DatabaseUpdateController::pendingMigrations();
            if ($pending) {
                if ($this->expectsApiResponse($request)) {
                    return response()->json([
                        'status' => 'update_required',
                        'message' => 'A system update is required before this request can be served.',
                        'pending_count' => count($pending),
                    ], 503);
                }

                return redirect()->route('system.update.show');
            }
        } catch (\Throwable $e) {
            report($e);
        }

        return $next($request);
    }

    private function expectsApiResponse(Request $request): bool
    {
        return $request->is('api/*') || $request->is('app/api/*') || $request->expectsJson();
    }
}
