<?php

namespace App\Http\Middleware;

use App\Http\Controllers\DatabaseUpdateController;
use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

class CheckInstalled
{
    /**
     * Handle an incoming request.
     *
     * @param Closure(Request): (Response) $next
     */
    public function handle(Request $request, Closure $next): Response
    {
        $isInstalled = file_exists(storage_path('installed'))
            || env('APP_INSTALLED') == true
            || env('APP_INSTALLED') === 'true';

        if (!$isInstalled) {
            if ($request->is('api/*')) {
                return response()->json([
                    'status' => 'error',
                    'message' => 'System installation required. Please visit /install to complete setup.',
                ], 503);
            }

            if (!$request->expectsJson() && !$request->is('install*')) {
                return redirect('/install');
            }
        } else {
            // Never call the old InstallerController::getPendingMigrations() helper.
            // DatabaseUpdateController owns the current migration detection/healing logic.
            if (!$request->is('install/update*')) {
                $pending = DatabaseUpdateController::pendingMigrations();

                if (!empty($pending)) {
                    if ($request->is('api/*')) {
                        return response()->json([
                            'status' => 'update_required',
                            'message' => 'Database update required. Please visit /install/update to execute migrations.',
                            'pending_count' => count($pending),
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
