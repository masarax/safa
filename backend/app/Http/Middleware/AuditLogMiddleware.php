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

        if (in_array($request->method(), ['POST', 'PUT', 'PATCH', 'DELETE'], true)) {
            try {
                $sensitive = [
                    'password', 'pin', 'api_secret', 'api_key', 'token', 'access_token',
                    'refresh_token', 'device_token', 'session_token', 'fingerprint_token',
                    'fingerprint_hash', 'authorization',
                ];

                $redact = function ($value) use (&$redact, $sensitive) {
                    if (!is_array($value)) return $value;
                    $result = [];
                    foreach ($value as $key => $item) {
                        $normalized = strtolower((string) $key);
                        $result[$key] = in_array($normalized, $sensitive, true)
                            ? '[REDACTED]'
                            : (is_array($item) ? $redact($item) : $item);
                    }
                    return $result;
                };

                AuditLog::create([
                    'user_id' => Auth::id(),
                    'account_id' => $request->attributes->get('active_account_id'),
                    'action' => $request->method(),
                    'endpoint' => $request->path(),
                    'payload' => $redact($request->all()),
                    'ip_address' => $request->ip(),
                ]);
            } catch (\Throwable $e) {
                // Auditing must never turn a successful business request into a failure.
                report($e);
            }
        }

        return $response;
    }
}
