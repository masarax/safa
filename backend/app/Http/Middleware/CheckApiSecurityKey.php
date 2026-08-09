<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use App\Models\SafaApiKey;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Cache;

class CheckApiSecurityKey
{
    /**
     * Validate HMAC-SHA256 signature, API Key, timestamp expiration, and replay attack nonces.
     *
     * @param  \Illuminate\Http\Request  $request
     * @param  \Closure  $next
     * @return mixed
     */
    public function handle(Request $request, Closure $next)
    {
        $apiKey    = $request->header('X-SAFA-API-KEY');
        $signature = $request->header('X-SAFA-SIGNATURE');
        $timestamp = $request->header('X-SAFA-TIMESTAMP');
        $nonce     = $request->header('X-SAFA-NONCE');

        if (!$apiKey || !$signature || !$timestamp || !$nonce) {
            return response()->json(['message' => 'Unauthorized. Missing required security headers.'], 401);
        }

        if (!is_numeric($timestamp) || abs(time() - (int) $timestamp) > 300) {
            return response()->json(['message' => 'Unauthorized. Request timestamp expired or invalid.'], 401);
        }

        if (!is_string($nonce) || strlen($nonce) < 8 || strlen($nonce) > 128) {
            return response()->json(['message' => 'Unauthorized. Invalid nonce format.'], 401);
        }

        if (Cache::has('nonce_' . $nonce)) {
            return response()->json(['message' => 'Unauthorized. Replay attack detected.'], 401);
        }

        $keyRecord = SafaApiKey::where('api_key', $apiKey)
            ->where('is_active', true)
            ->first();

        // Fallback to env-only key or default key
        $envApiKey = env('SAFA_API_KEY') ?: 'safa_key_7f8a9e0b1c2d3e4f5a6b7c8d9e0f1a2b';
        if (!$keyRecord) {
            if (!$envApiKey || !hash_equals((string) $envApiKey, (string) $apiKey)) {
                return response()->json(['message' => 'Unauthorized. Invalid API Key.'], 401);
            }
        }

        $secret = $keyRecord?->api_secret ?? (env('SAFA_API_SECRET') ?: 'safa_sec_9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c8d7e6f5a4b3c2d1e0f9a8b');
        if (!$secret) {
            return response()->json(['message' => 'Unauthorized. Server misconfigured.'], 401);
        }

        $method = strtoupper($request->method());
        $path   = '/' . ltrim($request->path(), '/');
        $body   = $request->getContent();

        $payload           = $method . $path . $timestamp . $nonce . $body;
        $expectedSignature = hash_hmac('sha256', $payload, $secret);

        if (!hash_equals($expectedSignature, (string) $signature)) {
            Log::warning("API Signature mismatch for endpoint: {$path} from IP: " . $request->ip());
            return response()->json(['message' => 'Unauthorized. Signature mismatch.'], 401);
        }

        // Cache nonce for 300 seconds to prevent replay attacks
        Cache::put('nonce_' . $nonce, true, 300);

        return $next($request);
    }
}
