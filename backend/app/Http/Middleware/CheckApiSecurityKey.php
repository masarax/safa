<?php

namespace App\Http\Middleware;

use App\Models\SafaApiKey;
use Closure;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Log;

class CheckApiSecurityKey
{
    /**
     * Validate HMAC-SHA256 signature, API key, timestamp expiration,
     * and replay-attack nonces.
     */
    public function handle(Request $request, Closure $next)
    {
        $apiKey = $request->header('X-SAFA-API-KEY');
        $signature = $request->header('X-SAFA-SIGNATURE');
        $timestamp = $request->header('X-SAFA-TIMESTAMP');
        $nonce = $request->header('X-SAFA-NONCE');

        if (!$apiKey || !$signature || !$timestamp || !$nonce) {
            return response()->json(['message' => 'Unauthorized. Missing required security headers.'], 401);
        }

        if (!is_numeric($timestamp) || abs(time() - (int) $timestamp) > 300) {
            return response()->json(['message' => 'Unauthorized. Request timestamp expired or invalid.'], 401);
        }

        if (!is_string($nonce) || strlen($nonce) < 8 || strlen($nonce) > 128) {
            return response()->json(['message' => 'Unauthorized. Invalid nonce format.'], 401);
        }

        $keyRecord = SafaApiKey::where('api_key', $apiKey)
            ->where('is_active', true)
            ->first();

        if (!$keyRecord) {
            return response()->json(['message' => 'Unauthorized. Invalid API Key.'], 401);
        }

        $secret = (string) $keyRecord->api_secret;
        if ($secret === '') {
            Log::error('SAFA API key has no configured secret.', [
                'api_key_id' => $keyRecord->id,
                'client_name' => $keyRecord->client_name,
            ]);
            return response()->json(['message' => 'Unauthorized. Server misconfigured.'], 500);
        }

        $method = strtoupper($request->method());
        $path = '/' . ltrim($request->path(), '/');
        $body = $request->getContent();
        $payload = $method . $path . $timestamp . $nonce . $body;
        $expectedSignature = hash_hmac('sha256', $payload, $secret);

        if (!hash_equals($expectedSignature, (string) $signature)) {
            Log::warning("API Signature mismatch for endpoint: {$path} from IP: " . $request->ip());
            return response()->json(['message' => 'Unauthorized. Signature mismatch.'], 401);
        }

        // Cache::add is atomic on supported cache stores, closing the small
        // check-then-put race that allowed the same nonce to be accepted twice.
        $nonceKey = 'safa_hmac_nonce:' . hash('sha256', $apiKey . ':' . $nonce);
        if (!Cache::add($nonceKey, true, 300)) {
            return response()->json(['message' => 'Unauthorized. Replay attack detected.'], 401);
        }

        return $next($request);
    }
}
