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
        if (app()->environment('testing')) return $next($request);
        if ($request->is('install/update*') || $request->is('install*')) return $next($request);

        $isInstalled = file_exists(storage_path('installed')) || env('APP_INSTALLED') === true || env('APP_INSTALLED') === 'true';
        if (!$isInstalled) {
            if ($request->is('api/*')) return response()->json(['status' => 'error', 'message' => 'System installation required.'], 503);
            return response()->json(['status' => 'error', 'message' => 'SAFA is not installed.'], 503);
        }

        try {
            $pending = DatabaseUpdateController::pendingMigrations();
            if (!empty($pending)) return response()->json(['status' => 'update_required', 'message' => 'Database update required. Deploy migrations before serving this request.', 'pending_count' => count($pending)], 503);
        } catch (\Throwable $e) { report($e); }
        return $next($request);
    }
}
