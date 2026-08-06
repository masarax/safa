<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use App\Models\SafaApiKey;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Cache;

class CheckApiSecurityKey
{
    public function handle(Request $request, Closure $next)
    {
        $apiKey = $request->header('X-SAFA-API-KEY');
        $signature = $request->header('X-SAFA-SIGNATURE');
        $timestamp = $request->header('X-SAFA-TIMESTAMP');
        $nonce = $request->header('X-SAFA-NONCE');

        if (!$apiKey || !$signature || !$timestamp || !$nonce) {
            return response()->json(['message' => 'Unauthorized. Missing security headers.'], 401);
        }

        if (abs(time() - $timestamp) > 300) {
            return response()->json(['message' => 'Unauthorized. Request expired.'], 401);
        }

        if (Cache::has('nonce_' . $nonce)) {
            return response()->json(['message' => 'Unauthorized. Replay attack detected.'], 401);
        }
        Cache::put('nonce_' . $nonce, true, 300);

        $keyRecord = SafaApiKey::where('api_key', $apiKey)->where('is_active', true)->first();

        // Fallback to env-only key (no hardcoded default - fail closed if not configured)
        $envApiKey = env('SAFA_API_KEY');
        if (! $keyRecord) {
            if (! $envApiKey || ! hash_equals($envApiKey, $apiKey)) {
                return response()->json(['message' => 'Unauthorized. Invalid API Key.'], 401);
            }
        }

        $secret = $keyRecord?->api_secret ?? env('SAFA_API_SECRET');
        if (! $secret) {
            return response()->json(['message' => 'Unauthorized. Server misconfigured.'], 401);
        }

        $method = $request->method();
        $path = '/' . ltrim($request->path(), '/');
        $body = $request->getContent();

        $payload = $method . $path . $timestamp . $nonce . $body;
        $expectedSignature = hash_hmac('sha256', $payload, $secret);

        if (!hash_equals($expectedSignature, $signature)) {
            Log::warning("API Signature mismatch");
            return response()->json(['message' => 'Unauthorized. Signature mismatch.'], 401);
        }

        return $next($request);
    }
}
