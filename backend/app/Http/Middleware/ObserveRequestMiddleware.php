<?php

namespace App\Http\Middleware;

use App\Support\OperationalMetrics;
use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

class ObserveRequestMiddleware
{
    public function handle(Request $request, Closure $next): Response
    {
        $started = hrtime(true);
        $requestId = $this->requestId($request);
        $request->attributes->set('safa_request_id', $requestId);

        try {
            $response = $next($request);
        } catch (\Throwable $error) {
            OperationalMetrics::recordRequest($this->route($request), 500, $this->elapsedMs($started));
            throw $error;
        }

        OperationalMetrics::recordRequest($this->route($request), $response->getStatusCode(), $this->elapsedMs($started));
        $response->headers->set('X-SAFA-REQUEST-ID', $requestId);
        return $response;
    }

    private function route(Request $request): string
    {
        $uri = $request->route()?->uri();
        return strtoupper($request->method()) . ':' . ($uri ?: 'unmatched');
    }

    private function requestId(Request $request): string
    {
        $provided = trim((string) $request->header('X-SAFA-REQUEST-ID', ''));
        if ($provided !== '' && preg_match('/^[A-Za-z0-9-]{12,64}$/', $provided) === 1) return $provided;
        return bin2hex(random_bytes(16));
    }

    private function elapsedMs(int $started): float
    {
        return max(0.0, (hrtime(true) - $started) / 1_000_000);
    }
}
