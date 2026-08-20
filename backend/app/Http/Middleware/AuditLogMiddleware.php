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
                $exactSensitive = [
                    'api_key', 'authorization', 'fingerprint_hash',
                ];

                $isSensitiveKey = static function (string $key) use ($exactSensitive): bool {
                    $normalized = strtolower((string) preg_replace('/[^a-z0-9]+/i', '_', trim($key)));
                    $normalized = trim($normalized, '_');
                    if (in_array($normalized, $exactSensitive, true)) return true;

                    return preg_match(
                        '/(?:^|_)(?:password|passcode|pin|secret|token|authorization)(?:_|$)/',
                        $normalized,
                    ) === 1;
                };

                $redact = function ($value) use (&$redact, $isSensitiveKey) {
                    if (!is_array($value)) return $value;
                    $result = [];
                    foreach ($value as $key => $item) {
                        $result[$key] = $isSensitiveKey((string) $key)
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
