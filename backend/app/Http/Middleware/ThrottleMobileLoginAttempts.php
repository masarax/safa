<?php

namespace App\Http\Middleware;

use App\Support\MobileNumber;
use Closure;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\RateLimiter;
use Symfony\Component\HttpFoundation\Response;

/**
 * Rate-limit failed mobile/PIN authentication without penalizing successful
 * sign-ins. This keeps logout -> immediate login usable while preserving the
 * brute-force boundary for invalid credentials.
 */
class ThrottleMobileLoginAttempts
{
    private const MAX_ATTEMPTS = 5;
    private const DECAY_SECONDS = 60;

    public function handle(Request $request, Closure $next): Response
    {
        $key = $this->attemptKey($request);

        if (RateLimiter::tooManyAttempts($key, self::MAX_ATTEMPTS)) {
            $retryAfter = max(1, RateLimiter::availableIn($key));

            return response()->json([
                'status' => 'error',
                'message' => 'Too many login attempts. Please wait and try again.',
                'error' => [
                    'code' => 'LOGIN_THROTTLED',
                    'message' => 'Too many login attempts. Please wait and try again.',
                ],
            ], 429)->header('Retry-After', (string) $retryAfter);
        }

        $response = $next($request);
        $status = $response->getStatusCode();

        if ($status >= 200 && $status < 300) {
            RateLimiter::clear($key);
        } elseif ($status === 401) {
            RateLimiter::hit($key, self::DECAY_SECONDS);
        }

        return $response;
    }

    private function attemptKey(Request $request): string
    {
        $rawIdentity = (string) (
            $request->input('mobile')
            ?? $request->input('email')
            ?? $request->input('username')
            ?? ''
        );
        $normalized = MobileNumber::normalize($rawIdentity);
        $identity = $normalized !== '' ? $normalized : trim($rawIdentity);
        $ip = (string) ($request->ip() ?? 'unknown');

        return 'safa-login:' . hash('sha256', $ip . '|' . $identity);
    }
}
