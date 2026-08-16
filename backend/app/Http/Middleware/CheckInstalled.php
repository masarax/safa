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

        if ($request->is('install*')) {
            return $next($request);
        }

        $isInstalled = file_exists(storage_path('installed')) || env('APP_INSTALLED') === true || env('APP_INSTALLED') === 'true';
        if (!$isInstalled) {
            if ($this->expectsApiResponse($request)) {
                return response()->json(['status' => 'error', 'message' => 'System installation required.'], 503);
            }

            return response()->json(['status' => 'error', 'message' => 'SAFA is not installed.'], 503);
        }

        // These routes must remain reachable while an update is pending. Guests
        // need the login page before the authenticated SuperAdmin update screen,
        // and the update page needs its public static assets without redirect loops.
        if ($request->is('system/update*')
            || $request->is('login')
            || $request->is('logout')
            || $request->is('safa-logo.png')
            || $request->is('favicon.svg')
            || $request->is('safa-web.css')
            || $request->is('safa-web.js')
            || $request->is('safa-web-events.js')
            || $request->is('storage/logos/*')) {
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
