<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;
use App\Http\Controllers\InstallerController;

class CheckInstalled
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

        if (!$isInstalled) {
            if ($request->is('api/*')) {
                return response()->json([
                    'status' => 'error',
                    'message' => 'System installation required. Please visit /install to complete setup.'
                ], 503);
            }

            if (!$request->expectsJson() && !$request->is('install*')) {
                return redirect('/install');
            }
        } else {
            // System is installed: Check if database updates / migrations are pending
            if (!$request->is('install/update*')) {
                $pending = InstallerController::getPendingMigrations();
                if (!empty($pending)) {
                    if ($request->is('api/*')) {
                        return response()->json([
                            'status' => 'update_required',
                            'message' => 'Database update required. Please visit /install/update to execute migrations.',
                            'pending_count' => count($pending)
                        ], 503);
                    }

                    if (!$request->expectsJson()) {
                        return redirect()->route('install.update-view');
                    }
                }
            }
        }

        return $next($request);
    }
}

