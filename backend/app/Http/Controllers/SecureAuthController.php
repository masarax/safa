<?php

namespace App\Http\Controllers;

use App\Models\AuthSession;
use App\Models\DeviceBinding;
use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

class SecureAuthController extends Controller
{
    public function session(Request $request)
    {
        $user = $request->user() ?? $request->attributes->get('user');
        $accessToken = $request->bearerToken();
        if (!$user || !$accessToken) return response()->json(['status' => 'error', 'message' => 'Authenticated session required.'], 401);

        $session = AuthSession::query()
            ->where('access_token_hash', AuthSession::tokenHash($accessToken))
            ->where('user_id', $user->id)
            ->where('is_revoked', false)
            ->first();

        if (!$session || ($session->expires_at && $session->expires_at->isPast())) return response()->json(['status' => 'error', 'message' => 'Session expired.'], 401);

        $deviceUuid = trim((string) ($request->header('X-SAFA-DEVICE-TOKEN') ?? ''));
        if ($deviceUuid !== '' && !hash_equals((string) $session->device_uuid, $deviceUuid)) return response()->json(['status' => 'error', 'message' => 'Device session mismatch.'], 401);

        return response()->json([
            'status' => 'success',
            'message' => 'Session is active.',
            'user' => [
                'id' => $user->id,
                'name' => $user->name,
                'email' => $user->email,
                'mobile' => $user->mobile,
                'role' => $user->role,
                'is_activated' => (bool) $user->is_activated,
                'permissions' => $user->getFormattedPermissions(),
            ],
            'session' => ['expires_at' => optional($session->expires_at)->toIso8601String(), 'device_uuid' => $session->device_uuid],
        ]);
    }

    public function refresh(Request $request)
    {
        $refreshToken = trim((string) ($request->input('refresh_token') ?? $request->header('X-SAFA-REFRESH-TOKEN')));
        $deviceUuid = trim((string) ($request->input('device_token') ?? $request->input('device_uuid') ?? $request->header('X-SAFA-DEVICE-TOKEN')));
        $fingerprint = trim((string) ($request->input('fingerprint_token') ?? $request->input('fingerprint_hash') ?? $request->header('X-SAFA-FINGERPRINT-TOKEN')));

        if ($refreshToken === '' || $deviceUuid === '' || $fingerprint === '') return response()->json(['status' => 'error', 'message' => 'Missing refresh security credentials.'], 400);

        $result = DB::transaction(function () use ($refreshToken, $deviceUuid, $fingerprint) {
            $session = AuthSession::query()
                ->where('refresh_token_hash', AuthSession::tokenHash($refreshToken))
                ->where('device_uuid', $deviceUuid)
                ->where('is_revoked', false)
                ->lockForUpdate()
                ->first();

            if (!$session || ($session->expires_at && $session->expires_at->isPast())) return null;
            $user = User::find($session->user_id);
            if (!$user || !$user->is_activated) return null;

            $binding = DeviceBinding::query()->where('user_id', $user->id)->where('device_uuid', $deviceUuid)->where('is_active', true)->first();
            if (!$binding || !hash_equals((string) $binding->fingerprint_hash, $fingerprint)) return null;

            $newRefreshToken = Str::random(64);
            $newAccessToken = AuthJWTController::generateJwt([
                'iss' => config('app.url', 'safa-backend'),
                'sub' => $user->id,
                'user_id' => $user->id,
                'device_uuid' => $deviceUuid,
                'session_token' => $session->session_token,
                'iat' => time(),
                'exp' => time() + (24 * 3600),
            ]);

            $session->update(['access_token' => $newAccessToken, 'refresh_token' => $newRefreshToken]);

            return [
                'access_token' => $newAccessToken,
                'refresh_token' => $newRefreshToken,
                'device_token' => $deviceUuid,
                'session_token' => $session->session_token,
                'fingerprint_token' => $binding->fingerprint_hash,
                'permissions' => $user->getFormattedPermissions(),
            ];
        });

        if ($result === null) return response()->json(['status' => 'error', 'message' => 'Invalid or expired refresh session.'], 401);

        return response()->json([
            'status' => 'success',
            'message' => 'Access token refreshed successfully.',
            'permissions' => $result['permissions'],
            'tokens' => [
                'access_token' => $result['access_token'],
                'refresh_token' => $result['refresh_token'],
                'device_token' => $result['device_token'],
                'session_token' => $result['session_token'],
                'fingerprint_token' => $result['fingerprint_token'],
            ],
        ]);
    }

    public function logout(Request $request)
    {
        $accessToken = $request->bearerToken();
        if (!$accessToken) return response()->json(['status' => 'error', 'message' => 'Authenticated session required.'], 401);
        $session = AuthSession::query()->where('access_token_hash', AuthSession::tokenHash($accessToken))->where('is_revoked', false)->first();
        if (!$session) return response()->json(['status' => 'success', 'message' => 'Session already ended.']);
        $session->update(['is_revoked' => true]);
        return response()->json(['status' => 'success', 'message' => 'Logged out successfully.']);
    }

    public function logoutAll(Request $request)
    {
        $user = $request->user() ?? $request->attributes->get('user');
        if (!$user) return response()->json(['status' => 'error', 'message' => 'Authenticated user required.'], 401);
        AuthSession::where('user_id', $user->id)->update(['is_revoked' => true]);
        return response()->json(['status' => 'success', 'message' => 'All active sessions have been revoked.']);
    }
}
