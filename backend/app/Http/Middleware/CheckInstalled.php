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
        if (app()->environment('testing') && !(bool) config('safa.enforce_update_checks_in_tests', false)) {
            return $next($request);
        }

        // Setup/recovery and the exact public presentation assets must remain
        // reachable even before the installation marker exists. The setup
        // controller independently enforces one-time ownership and SuperAdmin
        // authorization rules; no arbitrary filesystem path is exposed here.
        if ($request->is('install*')
            || $request->is('index*')
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
                return response()->json(['status' => 'error', 'message' => 'System installation required.'], 503);
            }

            return response()->json(['status' => 'error', 'message' => 'SAFA is not installed.'], 503);
        }

        // These routes must remain reachable while an update is pending. Guests
        // need the login page before the authenticated SuperAdmin update screen.
        if ($request->is('system/update*')
            || $request->is('login')
            || $request->is('logout')) {
            return $next($request);
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
