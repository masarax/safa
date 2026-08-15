<?php

namespace App\Http\Middleware;

use App\Http\Controllers\AuthJWTController;
use App\Models\AuthSession;
use App\Models\DeviceBinding;
use App\Models\SafaApiKey;
use App\Models\User;
use Closure;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Auth;
use Symfony\Component\HttpFoundation\Response;

class VerifyMultiLevelToken
{
    public function handle(Request $request, Closure $next): Response
    {
        // API-key-only requests are permitted only inside the isolated test
        // environment so sync/domain tests do not need to manufacture mobile
        // credentials. Production always requires the full token stack.
        if (app()->environment('testing') && $request->header('X-SAFA-API-KEY') && !$request->bearerToken()) {
            $key = SafaApiKey::query()
                ->where('api_key', $request->header('X-SAFA-API-KEY'))
                ->where('is_active', true)
                ->first();
            if ($key) {
                if ($key->account_id) {
                    $request->attributes->set('active_account_id', (int) $key->account_id);
                }
                return $next($request);
            }
        }

        $accessToken = $request->bearerToken();
        $refreshToken = $request->header('X-SAFA-REFRESH-TOKEN') ?? $request->header('x-safa-refresh-token');
        $deviceUuid = $request->header('X-SAFA-DEVICE-TOKEN') ?? $request->header('x-safa-device-token');
        $sessionToken = $request->header('X-SAFA-SESSION-TOKEN') ?? $request->header('x-safa-session-token');
        $fingerprintHash = $request->header('X-SAFA-FINGERPRINT-TOKEN') ?? $request->header('x-safa-fingerprint-token');

        if (!$accessToken || !$refreshToken || !$deviceUuid || !$sessionToken || !$fingerprintHash) {
            return response()->json(['status' => 'error', 'message' => 'Unauthorized: missing required security tokens.'], 401);
        }

        $payload = AuthJWTController::verifyJwt($accessToken);
        if (!$payload) {
            return response()->json(['status' => 'error', 'message' => 'Unauthorized: invalid or expired access token.'], 401);
        }

        $userId = (int) ($payload['user_id'] ?? $payload['sub'] ?? 0);
        if ($userId <= 0 || (string) ($payload['device_uuid'] ?? '') !== (string) $deviceUuid) {
            return response()->json(['status' => 'error', 'message' => 'Forbidden: token/device mismatch.'], 403);
        }

        $session = AuthSession::findActiveByTokenStack(
            $userId,
            (string) $deviceUuid,
            (string) $accessToken,
            (string) $refreshToken,
            (string) $sessionToken
        );

        if (!$session) {
            return response()->json(['status' => 'error', 'message' => 'Unauthorized: auth session invalid or revoked.'], 401);
        }

        if ($session->expires_at && $session->expires_at->isPast()) {
            return response()->json(['status' => 'error', 'message' => 'Unauthorized: auth session has expired.'], 401);
        }

        $binding = DeviceBinding::query()
            ->where('user_id', $userId)
            ->where('device_uuid', $deviceUuid)
            ->where('is_active', true)
            ->first();

        if (!$binding || !hash_equals((string) $binding->fingerprint_hash, (string) $fingerprintHash)) {
            return response()->json(['status' => 'error', 'message' => 'Forbidden: device fingerprint verification failed.'], 403);
        }

        $user = User::find($userId);
        if (!$user || !$user->is_activated) {
            return response()->json(['status' => 'error', 'message' => 'Unauthorized: user account is unavailable.'], 401);
        }

        Auth::setUser($user);
        $request->setUserResolver(fn () => $user);
        $request->attributes->set('user', $user);
        $request->attributes->set('auth_session', $session);

        if (isset($payload['account_id'])) {
            $request->attributes->set('active_account_id', (int) $payload['account_id']);
        }
        if (isset($payload['owner_user_id'])) {
            $request->attributes->set('owner_user_id', (int) $payload['owner_user_id']);
        }

        return $next($request);
    }
}
