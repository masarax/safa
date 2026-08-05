<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use App\Models\AuditLog;
use Illuminate\Support\Facades\Auth;

class AuditLogMiddleware
{
    public function handle(Request $request, Closure $next)
    {
        $response = $next($request);

        if (in_array($request->method(), ['POST', 'PUT', 'PATCH', 'DELETE'])) {
            try {
                // Strip sensitive fields before persisting to audit log
                $safePayload = collect($request->all())
                    ->except(['password', 'api_secret', 'api_key', 'token', 'pin'])
                    ->toArray();

                AuditLog::create([
                    'user_id'    => Auth::id() ?? 0,
                    'action'     => $request->method(),
                    'endpoint'   => $request->path(),
                    'payload'    => $safePayload,
                    'ip_address' => $request->ip(),
                ]);
            } catch (\Exception $e) {
                // Fail silently so audit never breaks the response
            }
        }

        return $response;
    }
}
