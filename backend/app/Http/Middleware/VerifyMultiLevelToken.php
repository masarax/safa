<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;
use App\Http\Controllers\AuthJWTController;
use App\Models\AuthSession;
use App\Models\DeviceBinding;
use App\Models\User;
use Illuminate\Support\Facades\Auth;

class VerifyMultiLevelToken
{
    /**
     * Handle an incoming request by validating all 5 security tokens.
     */
    public function handle(Request $request, Closure $next): Response
    {
        // 1. Retrieve all 5 security tokens from headers
        $accessToken = $request->bearerToken();
        $refreshToken = $request->header('X-SAFA-REFRESH-TOKEN') ?? $request->header('x-safa-refresh-token');
        $deviceUuid = $request->header('X-SAFA-DEVICE-TOKEN') ?? $request->header('x-safa-device-token');
        $sessionToken = $request->header('X-SAFA-SESSION-TOKEN') ?? $request->header('x-safa-session-token');
        $fingerprintHash = $request->header('X-SAFA-FINGERPRINT-TOKEN') ?? $request->header('x-safa-fingerprint-token');

        if (!$accessToken || !$refreshToken || !$deviceUuid || !$sessionToken || !$fingerprintHash) {
            return response()->json([
                'status' => 'error',
                'message' => 'Unauthorized: Missing required security tokens. Multi-level verification requires Access, Refresh, Device, Session, and Fingerprint tokens.'
            ], 401);
        }

        // 2. Validate Access Token (JWT)
        $payload = AuthJWTController::verifyJwt($accessToken);
        if (!$payload) {
            return response()->json([
                'status' => 'error',
                'message' => 'Unauthorized: Invalid or expired access token (JWT).'
            ], 401);
        }

        $userId = $payload['user_id'] ?? $payload['sub'] ?? null;
        $jwtDeviceUuid = $payload['device_uuid'] ?? null;
        $jwtSessionToken = $payload['session_token'] ?? null;

        if (!$userId || $jwtDeviceUuid !== $deviceUuid || $jwtSessionToken !== $sessionToken) {
            return response()->json([
                'status' => 'error',
                'message' => 'Forbidden: JWT token claims mismatch with device or session headers.'
            ], 403);
        }

        // 3. Validate AuthSession
        $session = AuthSession::where('user_id', $userId)
            ->where('device_uuid', $deviceUuid)
            ->where('refresh_token', $refreshToken)
            ->where('session_token', $sessionToken)
            ->where('is_revoked', false)
            ->first();

        if (!$session) {
            return response()->json([
                'status' => 'error',
                'message' => 'Unauthorized: Auth session invalid or revoked.'
            ], 401);
        }

        if ($session->expires_at && $session->expires_at->isPast()) {
            return response()->json([
                'status' => 'error',
                'message' => 'Unauthorized: Auth session has expired.'
            ], 401);
        }

        // 4. Validate Hardware Device Binding
        $binding = DeviceBinding::where('user_id', $userId)
            ->where('device_uuid', $deviceUuid)
            ->where('is_active', true)
            ->first();

        if (!$binding) {
            return response()->json([
                'status' => 'error',
                'message' => 'Forbidden: Unbound or inactive device.'
            ], 403);
        }

        if (!hash_equals((string) $binding->fingerprint_hash, (string) $fingerprintHash)) {
            return response()->json([
                'status' => 'error',
                'message' => 'Forbidden: Hardware fingerprint verification failed.'
            ], 403);
        }

        // 5. Retrieve & Set User in Request / Auth Context
        $user = User::find($userId);
        if (!$user) {
            return response()->json([
                'status' => 'error',
                'message' => 'Unauthorized: User account not found.'
            ], 401);
        }

        Auth::setUser($user);
        $request->setUserResolver(fn () => $user);
        $request->attributes->set('user', $user);
        $request->attributes->set('active_account_id', $payload['account_id'] ?? 1);
        $request->attributes->set('owner_user_id', $payload['owner_user_id'] ?? $userId);

        return $next($request);

    }
}
