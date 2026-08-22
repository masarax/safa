<?php

namespace App\Http\Middleware;

use App\Models\AuditLog;
use Closure;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;

class AuditLogMiddleware
{
    public function handle(Request $request, Closure $next)
    {
        $response = $next($request);

        if (in_array($request->method(), ['POST', 'PUT', 'PATCH', 'DELETE'], true)) {
            try {
                AuditLog::create([
                    'user_id' => Auth::id(),
                    'account_id' => $request->attributes->get('active_account_id'),
                    'action' => $request->method(),
                    // Store the route template instead of the concrete URL so a
                    // mobile number, UUID or other identifier can never leak via
                    // a path parameter.
                    'endpoint' => $request->route()?->uri() ?: $request->path(),
                    'payload' => $this->eventMetadata($request, $response->getStatusCode()),
                    // Security investigations benefit from a stable source
                    // correlation, but raw client IP is unnecessary business PII.
                    'ip_address' => $this->pseudonymousIp($request->ip()),
                ]);
            } catch (\Throwable $e) {
                // Auditing must never turn a successful business request into a failure.
                report($e);
            }
        }

        return $response;
    }

    /** @return array<string, mixed> */
    private function eventMetadata(Request $request, int $statusCode): array
    {
        $metadata = [
            'route' => $request->route()?->getName(),
            'status_code' => $statusCode,
            'result' => $statusCode >= 200 && $statusCode < 400 ? 'success' : 'rejected',
        ];

        // Numeric resource IDs are operational metadata, not user-entered
        // request payload. Never copy arbitrary route values into the audit row.
        $resourceIds = [];
        foreach (($request->route()?->parameters() ?? []) as $name => $value) {
            if ((is_int($value) || (is_string($value) && ctype_digit($value))) && (int) $value > 0) {
                $resourceIds[(string) $name] = (int) $value;
            }
        }
        if ($resourceIds !== []) $metadata['resource_ids'] = $resourceIds;

        return array_filter($metadata, static fn ($value) => $value !== null && $value !== '');
    }

    private function pseudonymousIp(?string $ip): ?string
    {
        $ip = trim((string) $ip);
        if ($ip === '') return null;

        $key = (string) config('app.key');
        if ($key === '') return null;

        // 32-byte HMAC encoded base64url without padding is 43 chars, fitting
        // the existing VARCHAR(45) without a data migration.
        return rtrim(strtr(base64_encode(hash_hmac('sha256', $ip, $key, true)), '+/', '-_'), '=');
    }
}
