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
        // The migration/update endpoints must remain usable when the database
        // is completely empty. They must never depend on the sessions table.
        if ($request->is('install/update*') || $request->is('install*')) {
            return $next($request);
        }

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

            if (!$request->expectsJson()) {
                return redirect('/install');
            }
        } else {
            try {
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
            } catch (\Throwable $e) {
                report($e);
                // Never block the migration/installer flow because migration
                // inspection itself encountered a database problem.
            }
        }

        return $next($request);
    }
}
