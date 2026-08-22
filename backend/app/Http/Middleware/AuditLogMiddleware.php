<?php

namespace App\Http\Middleware;

use App\Models\AuditLog;
use Closure;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Str;

class AuditLogMiddleware
{
    private const SENSITIVE_FIELD_PATTERN = '/(?:^|_)(?:password|passcode|pin|secret|token|authorization|fingerprint|api_key)(?:_|$)/';
    private const BULK_FIELD_NAMES = ['logo', 'image', 'file', 'base64', 'logo_base64'];

    public function handle(Request $request, Closure $next)
    {
        $correlationId = $this->correlationId($request);
        $response = $next($request);
        $response->headers->set('X-SAFA-Request-ID', $correlationId);

        if (in_array($request->method(), ['POST', 'PUT', 'PATCH', 'DELETE'], true)) {
            try {
                AuditLog::create([
                    'user_id' => Auth::id(),
                    'account_id' => $request->attributes->get('active_account_id'),
                    'action' => $request->method(),
                    'endpoint' => $request->path(),
                    'payload' => $this->safeEventMetadata($request, $response->getStatusCode(), $correlationId),
                    'ip_address' => $this->maskIp($request->ip()),
                ]);
            } catch (\Throwable $e) {
                // Auditing must never turn a successful business request into a failure.
                report($e);
            }
        }

        return $response;
    }

    /**
     * Audit only what is needed to establish who performed which operation and
     * whether it succeeded. Raw request values are deliberately never copied.
     */
    private function safeEventMetadata(Request $request, int $status, string $correlationId): array
    {
        $metadata = [
            'resource' => $this->resourceName($request),
            'result_status' => $status,
            'correlation_id' => $correlationId,
            'changed_fields' => $this->changedFieldNames($request),
        ];

        if ($resourceId = $this->numericResourceId($request)) $metadata['resource_id'] = $resourceId;
        if ($localId = $this->positiveInteger($request->input('local_id'))) $metadata['local_id'] = $localId;
        if ($serverId = $this->positiveInteger($request->input('server_id'))) $metadata['server_id'] = $serverId;

        $mutationId = trim((string) data_get($request->all(), '_sync.mutation_id', $request->input('mutation_id', '')));
        if ($mutationId !== '' && preg_match('/^[A-Za-z0-9._:-]{8,128}$/', $mutationId) === 1) {
            $metadata['mutation_id'] = $mutationId;
        }

        $operation = strtoupper(trim((string) data_get($request->all(), '_sync.operation', $request->input('operation', ''))));
        if (in_array($operation, ['CREATE', 'UPDATE', 'DELETE', 'RECOVER'], true)) {
            $metadata['operation'] = $operation;
        }

        return $metadata;
    }

    private function changedFieldNames(Request $request): array
    {
        $fields = [];
        foreach (array_keys($request->all()) as $key) {
            $normalized = strtolower((string) preg_replace('/[^a-z0-9]+/i', '_', trim((string) $key)));
            $normalized = trim($normalized, '_');
            if ($normalized === '' || in_array($normalized, self::BULK_FIELD_NAMES, true)) continue;
            if (preg_match(self::SENSITIVE_FIELD_PATTERN, $normalized) === 1) continue;
            $fields[] = $normalized;
        }

        sort($fields);
        return array_slice(array_values(array_unique($fields)), 0, 50);
    }

    private function resourceName(Request $request): string
    {
        $segments = array_values(array_filter(explode('/', trim($request->path(), '/'))));
        foreach (array_reverse($segments) as $segment) {
            if (ctype_digit($segment)) continue;
            $normalized = strtolower((string) preg_replace('/[^a-z0-9_-]+/i', '_', $segment));
            if ($normalized !== '' && !in_array($normalized, ['api', 'app', 'v1', 'mobile'], true)) {
                return substr($normalized, 0, 64);
            }
        }
        return 'request';
    }

    private function numericResourceId(Request $request): ?int
    {
        $route = $request->route();
        if ($route) {
            foreach (array_reverse($route->parameters()) as $value) {
                if ($id = $this->positiveInteger($value)) return $id;
            }
        }

        return $this->positiveInteger($request->input('id'));
    }

    private function positiveInteger(mixed $value): ?int
    {
        if (!is_numeric($value)) return null;
        $id = (int) $value;
        return $id > 0 ? $id : null;
    }

    private function correlationId(Request $request): string
    {
        $incoming = trim((string) ($request->header('X-SAFA-Request-ID') ?? $request->header('X-Request-ID') ?? ''));
        if ($incoming !== '' && preg_match('/^[A-Za-z0-9._:-]{8,64}$/', $incoming) === 1) return $incoming;
        return (string) Str::uuid();
    }

    private function maskIp(?string $ip): ?string
    {
        $value = trim((string) $ip);
        if ($value === '') return null;

        if (filter_var($value, FILTER_VALIDATE_IP, FILTER_FLAG_IPV4)) {
            $parts = explode('.', $value);
            $parts[3] = '0';
            return implode('.', $parts);
        }

        if (filter_var($value, FILTER_VALIDATE_IP, FILTER_FLAG_IPV6)) {
            $packed = inet_pton($value);
            if ($packed === false) return null;
            return inet_ntop(substr($packed, 0, 8) . str_repeat("\0", 8)) ?: null;
        }

        return null;
    }
}
