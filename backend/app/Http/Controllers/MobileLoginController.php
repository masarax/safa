<?php

namespace App\Http\Controllers;

use App\Models\AuthSession;
use App\Models\DeviceBinding;
use App\Models\User;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Str;

/**
 * Dedicated mobile authentication endpoint.
 *
 * Login authenticates an existing User record only. A successful first-factor
 * login creates a device-bound resumable session. Biometric quick unlock is
 * deliberately a client-side unlock of that already authenticated session;
 * it never replaces the mobile + PIN first factor.
 */
class MobileLoginController extends Controller
{
    public function login(Request $request): JsonResponse
    {
        $mobile = $this->normalizeDigits(trim((string) ($request->input('mobile') ?? $request->input('email') ?? $request->input('username'))));
        $pin = $this->normalizeDigits(trim((string) ($request->input('pin') ?? $request->input('password'))));

        if ($mobile === '') {
            return response()->json(['status' => 'error', 'message' => 'Mobile number is required for login.'], 422);
        }

        if ($pin === '' || !preg_match('/^\d{6}$/', $pin)) {
            return response()->json(['status' => 'error', 'message' => '6-Digit PIN is required for login.'], 422);
        }

        $user = $this->findUserByMobile($mobile);

        if (!$user) {
            return response()->json(['status' => 'error', 'message' => 'Mobile number or PIN is incorrect.'], 401);
        }

        if (!(bool) $user->is_activated) {
            return response()->json(['status' => 'error', 'message' => 'This account is inactive. Please contact an administrator.'], 403);
        }

        $hashes = array_filter([$user->pin_hash, $user->password]);
        $pinValid = false;

        foreach ($hashes as $hash) {
            try {
                if (Hash::check($pin, $hash)) {
                    $pinValid = true;
                    break;
                }
            } catch (\Throwable) {
                // Ignore malformed legacy hashes and try the next supported hash.
            }
        }

        if (!$pinValid) {
            return response()->json(['status' => 'error', 'message' => 'Mobile number or PIN is incorrect.'], 401);
        }

        $deviceUuid = trim((string) ($request->input('device_uuid')
            ?? $request->input('device_token')
            ?? $request->header('X-SAFA-DEVICE-TOKEN')
            ?? ''));
        $fingerprintHash = trim((string) ($request->input('fingerprint_hash')
            ?? $request->input('fingerprint_token')
            ?? $request->header('X-SAFA-FINGERPRINT-TOKEN')
            ?? ''));
        $deviceModel = trim((string) ($request->input('device_model')
            ?? $request->header('X-SAFA-DEVICE-MODEL')
            ?? 'Unknown Device'));

        if ($deviceUuid === '') {
            return response()->json(['status' => 'error', 'message' => 'Device identity is required.'], 400);
        }
        if ($fingerprintHash === '') {
            return response()->json(['status' => 'error', 'message' => 'Device fingerprint is required.'], 400);
        }

        $result = DB::transaction(function () use ($user, $deviceUuid, $fingerprintHash, $deviceModel) {
            $binding = DeviceBinding::query()
                ->where('user_id', $user->id)
                ->where('device_uuid', $deviceUuid)
                ->lockForUpdate()
                ->first();

            if ($binding && !$binding->is_active) {
                return ['error' => response()->json([
                    'status' => 'error',
                    'message' => 'Device is inactive or revoked for this account.',
                ], 403)];
            }

            // A known device must keep the same device fingerprint. A normal
            // PIN login on a genuinely new device creates a new binding; it
            // must not silently replace the security identity of an existing
            // active binding.
            if ($binding && !hash_equals((string) $binding->fingerprint_hash, $fingerprintHash)) {
                return ['error' => response()->json([
                    'status' => 'error',
                    'message' => 'Device security identity changed. Re-bind this device before continuing.',
                ], 403)];
            }

            if (!$binding) {
                DeviceBinding::create([
                    'user_id' => $user->id,
                    'device_uuid' => $deviceUuid,
                    'device_model' => $deviceModel,
                    'fingerprint_hash' => $fingerprintHash,
                    'is_active' => true,
                    'bound_at' => now(),
                ]);
            } else {
                $binding->update(['device_model' => $deviceModel]);
            }

            // One active session per account/device keeps the refresh-token
            // surface small and makes repeated first-factor logins deterministic.
            AuthSession::query()
                ->where('user_id', $user->id)
                ->where('device_uuid', $deviceUuid)
                ->where('is_revoked', false)
                ->update(['is_revoked' => true]);

            $sessionToken = Str::random(64);
            $refreshToken = Str::random(64);
            $accessToken = AuthJWTController::generateJwt([
                'iss' => config('app.url', 'safa-backend'),
                'sub' => $user->id,
                'user_id' => $user->id,
                'device_uuid' => $deviceUuid,
                'session_token' => $sessionToken,
                'iat' => time(),
                'exp' => time() + (24 * 3600),
            ]);

            AuthSession::create([
                'user_id' => $user->id,
                'device_uuid' => $deviceUuid,
                'access_token' => $accessToken,
                'refresh_token' => $refreshToken,
                'session_token' => $sessionToken,
                'expires_at' => now()->addDays(30),
                'is_revoked' => false,
            ]);

            return [
                'user' => $user,
                'access_token' => $accessToken,
                'refresh_token' => $refreshToken,
                'device_token' => $deviceUuid,
                'session_token' => $sessionToken,
                'fingerprint_token' => $fingerprintHash,
            ];
        });

        if (isset($result['error'])) {
            return $result['error'];
        }

        /** @var User $authenticatedUser */
        $authenticatedUser = $result['user'];
        $permissions = $authenticatedUser->getFormattedPermissions();

        return response()->json([
            'status' => 'success',
            'message' => 'Login successful.',
            'user' => [
                'id' => $authenticatedUser->id,
                'name' => $authenticatedUser->name,
                'email' => $authenticatedUser->email,
                'mobile' => $authenticatedUser->mobile,
                'role' => $authenticatedUser->role,
                'is_activated' => (bool) $authenticatedUser->is_activated,
                'permissions' => $permissions,
            ],
            'permissions' => $permissions,
            'tokens' => [
                'access_token' => $result['access_token'],
                'refresh_token' => $result['refresh_token'],
                'device_token' => $result['device_token'],
                'session_token' => $result['session_token'],
                'fingerprint_token' => $result['fingerprint_token'],
            ],
            'access_token' => $result['access_token'],
            'refresh_token' => $result['refresh_token'],
            'device_token' => $result['device_token'],
            'session_token' => $result['session_token'],
            'fingerprint_token' => $result['fingerprint_token'],
        ], 200);
    }

    private function findUserByMobile(string $mobile): ?User
    {
        $user = User::where('mobile', $mobile)->first();
        if ($user) {
            return $user;
        }

        $normalized = preg_replace('/\D+/', '', $mobile) ?? '';
        if ($normalized === '') {
            return null;
        }

        return User::whereRaw(
            "REPLACE(REPLACE(REPLACE(REPLACE(mobile, ' ', ''), '-', ''), '(', ''), ')', '') = ?",
            [$normalized]
        )->first();
    }

    private function normalizeDigits(string $value): string
    {
        return strtr($value, [
            '٠' => '0', '١' => '1', '٢' => '2', '٣' => '3', '٤' => '4',
            '٥' => '5', '٦' => '6', '٧' => '7', '٨' => '8', '٩' => '9',
            '۰' => '0', '۱' => '1', '۲' => '2', '۳' => '3', '۴' => '4',
            '۵' => '5', '۶' => '6', '۷' => '7', '۸' => '8', '۹' => '9',
        ]);
    }
}
