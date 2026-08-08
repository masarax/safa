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

            if ($request->is('install/update*')) {
                if (!empty($pending)) {
                    return $next($request);
                }
                return redirect()->route('home')->with('info', 'Database is already up to date.');
            }

            if ($request->is('install')) {
                if (!empty($pending)) {
                    return redirect()->route('install.update-view');
                }
                return redirect()->route('home')->with('info', 'System is already installed and up to date.');
            }

            if ($request->is('install/success')) {
                return $next($request);
            }

            if ($request->expectsJson() || $request->is('api/*')) {
                return response()->json([
                    'status' => 'error',
                    'message' => 'System already installed and up to date.'
                ], 403);
            }

            return redirect()->route('home');
        }

        return $next($request);
    }
}

