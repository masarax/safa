<?php

namespace App\Http\Controllers;

use App\Models\AuthSession;
use App\Models\DeviceBinding;
use App\Models\User;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Schema;

class SecureAuthController extends Controller
{
    public function session(Request $request)
    {
        $user = $request->user() ?? $request->attributes->get('user');
        $accessToken = $request->bearerToken();
        if (!$user || !$accessToken) return response()->json(['status' => 'error', 'message' => 'Authenticated session required.'], 401);

        $session = AuthSession::findActiveByAccessToken($accessToken, (int) $user->id);

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
            $session = AuthSession::findActiveByRefreshToken($refreshToken, $deviceUuid, true);

            if (!$session || ($session->expires_at && $session->expires_at->isPast())) return null;
            $user = User::find($session->user_id);
            if (!$user || !$user->is_activated) return null;

            $binding = DeviceBinding::query()->where('user_id', $user->id)->where('device_uuid', $deviceUuid)->where('is_active', true)->first();
            if (!$binding || !hash_equals((string) $binding->fingerprint_hash, $fingerprint)) return null;

            $newRefreshToken = AuthSession::newOpaqueToken();
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
        $user = $request->user() ?? $request->attributes->get('user');
        $session = AuthSession::findActiveByAccessToken($accessToken, $user ? (int) $user->id : null);
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

    /**
     * Change the authenticated user's six-digit PIN without trusting local
     * credential state. The current session remains usable; every other
     * session is revoked so a stolen token cannot survive a credential change.
     */
    public function changePin(Request $request)
    {
        $user = $request->user() ?? $request->attributes->get('user');
        $session = $request->attributes->get('auth_session');
        if (!$user || !$session) {
            return response()->json(['status' => 'error', 'message' => 'Authenticated session required.'], 401);
        }

        $currentPin = $this->normalizeDigits(trim((string) $request->input('current_pin')));
        $newPin = $this->normalizeDigits(trim((string) $request->input('new_pin')));
        if (!preg_match('/^\d{6}$/', $currentPin) || !preg_match('/^\d{6}$/', $newPin)) {
            return response()->json([
                'status' => 'error',
                'message' => 'Both current and new PIN must contain exactly six digits.',
                'errors' => ['pin' => ['A six-digit current PIN and new PIN are required.']],
            ], 422);
        }
        if (hash_equals($currentPin, $newPin)) {
            return response()->json([
                'status' => 'error',
                'message' => 'The new PIN must be different from the current PIN.',
                'errors' => ['new_pin' => ['Choose a different six-digit PIN.']],
            ], 422);
        }

        $changed = DB::transaction(function () use ($user, $session, $currentPin, $newPin): bool {
            $lockedUser = User::query()->whereKey($user->id)->lockForUpdate()->first();
            if (!$lockedUser || !$lockedUser->is_activated) return false;

            $currentPinValid = false;
            foreach (array_filter([$lockedUser->pin_hash, $lockedUser->password]) as $hash) {
                try {
                    if (Hash::check($currentPin, $hash)) {
                        $currentPinValid = true;
                        break;
                    }
                } catch (\Throwable) {
                    // A malformed legacy hash is never accepted as a credential.
                }
            }
            if (!$currentPinValid) return false;

            $newHash = Hash::make($newPin);
            $lockedUser->pin_hash = $newHash;
            $lockedUser->password = $newHash;
            $lockedUser->save();

            AuthSession::query()
                ->where('user_id', $lockedUser->id)
                ->where('id', '!=', $session->id)
                ->update(['is_revoked' => true]);

            if (Schema::hasTable('operator_accounts')) {
                DB::table('operator_accounts')
                    ->where(function ($query) use ($lockedUser) {
                        $query->where('user_id', $lockedUser->id);
                        if ($lockedUser->mobile) $query->orWhere('mobile', $lockedUser->mobile);
                    })
                    ->update(['pin_hash' => $newHash, 'updated_at' => now()]);
            }

            return true;
        });

        if (!$changed) {
            return response()->json(['status' => 'error', 'message' => 'Current PIN is incorrect.'], 401);
        }

        return response()->json(['status' => 'success', 'message' => 'PIN changed successfully.']);
    }

    private function normalizeDigits(string $value): string
    {
        return strtr($value, [
            '٠'=>'0','١'=>'1','٢'=>'2','٣'=>'3','٤'=>'4','٥'=>'5','٦'=>'6','٧'=>'7','٨'=>'8','٩'=>'9',
            '۰'=>'0','۱'=>'1','۲'=>'2','۳'=>'3','۴'=>'4','۵'=>'5','۶'=>'6','۷'=>'7','۸'=>'8','۹'=>'9',
            '০'=>'0','১'=>'1','২'=>'2','৩'=>'3','৪'=>'4','৫'=>'5','৬'=>'6','৭'=>'7','৮'=>'8','৯'=>'9',
        ]);
    }
}
