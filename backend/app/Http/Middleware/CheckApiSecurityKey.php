<?php

namespace App\Http\Middleware;

use App\Models\SafaApiKey;
use Closure;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Log;

/**
 * Authenticate the mobile API client identifier.
 *
 * An Android APK is an untrusted client, so it cannot safely contain a
 * server-side secret. The API key is therefore only a public client id.
 * Confidentiality and authorization come from TLS, user credentials,
 * short-lived JWTs and active server-side sessions.
 */
class CheckApiSecurityKey
{
    public function handle(Request $request, Closure $next)
    {
        $apiKey = trim((string) $request->header('X-SAFA-API-KEY'));

        if ($apiKey === '') {
            return response()->json(['message' => 'Unauthorized. Missing API client key.'], 401);
        }

        $keyRecord = SafaApiKey::where('api_key', $apiKey)
            ->where('is_active', true)
            ->first();

        if (!$keyRecord) {
            Log::warning('SAFA API client rejected.', [
                'ip' => $request->ip(),
                'client' => $request->header('X-SAFA-CLIENT'),
            ]);
            return response()->json(['message' => 'Unauthorized. Invalid API client key.'], 401);
        }

        return $next($request);
    }
}
