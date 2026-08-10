<?php

namespace App\Http\Middleware;

use App\Models\AuthSession;
use App\Models\DeviceBinding;
use App\Models\User;
use Closure;
use Illuminate\Http\Request;
use Illuminate\Support\Str;
use Symfony\Component\HttpFoundation\Response;

class VerifyRefreshRequest
{
    /**
     * Validate the refresh session, active user, device and fingerprint and
     * rotate the refresh token before the controller issues a new access token.
     */
    public function handle(Request $request, Closure $next): Response
    {
        $refreshToken = $request->input('refresh_token') ?? $request->header('X-SAFA-REFRESH-TOKEN');
        $deviceUuid = $request->input('device_token') ?? $request->input('device_uuid') ?? $request->header('X-SAFA-DEVICE-TOKEN');
        $fingerprint = $request->input('fingerprint_token')
            ?? $request->input('fingerprint_hash')
            ?? $request->header('X-SAFA-FINGERPRINT-TOKEN');

        if (!$refreshToken || !$deviceUuid || !$fingerprint) {
            return response()->json([
                'status' => 'error',
                'message' => 'Missing refresh security credentials.'
            ], 400);
        }

        $session = AuthSession::where('refresh_token', $refreshToken)
            ->where('device_uuid', $deviceUuid)
            ->where('is_revoked', false)
            ->first();

        if (!$session || ($session->expires_at && $session->expires_at->isPast())) {
            return response()->json([
                'status' => 'error',
                'message' => 'Invalid or expired refresh session.'
            ], 401);
        }

        $user = User::find($session->user_id);
        if (!$user || !$user->is_activated) {
            return response()->json([
                'status' => 'error',
                'message' => 'User account is inactive or unavailable.'
            ], 401);
        }

        $binding = DeviceBinding::where('user_id', $user->id)
            ->where('device_uuid', $deviceUuid)
            ->where('is_active', true)
            ->first();

        if (!$binding || !hash_equals((string) $binding->fingerprint_hash, (string) $fingerprint)) {
            return response()->json([
                'status' => 'error',
                'message' => 'Device fingerprint verification failed.'
            ], 403);
        }

        // Refresh tokens are single-use. The controller receives the rotated
        // token and therefore cannot accidentally persist the old one.
        $rotatedRefreshToken = Str::random(64);
        $session->refresh_token = $rotatedRefreshToken;
        $session->save();
        $request->merge(['refresh_token' => $rotatedRefreshToken]);

        return $next($request);
    }
}
