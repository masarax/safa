<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use App\Http\Controllers\InstallerController;
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
            $pending = InstallerController::getPendingMigrations();

            // Allow /install/update only if there are pending migrations
            if (!empty($pending) && $request->is('install/update*')) {
                return $next($request);
            }

            if ($request->is('install/success')) {
                return $next($request);
            }

            // Strict Security Lockdown: Pretend installer endpoints do not exist (HTTP 404)
            if ($request->expectsJson() || $request->is('api/*')) {
                return response()->json([
                    'status' => 'error',
                    'message' => 'System already installed and up to date.'
                ], 403);
            }

            abort(404);
        }

        return $next($request);
    }
}
