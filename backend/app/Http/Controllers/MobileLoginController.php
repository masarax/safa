<?php

namespace App\Http\Controllers;

use App\Models\AuthSession;
use App\Models\DeviceBinding;
use App\Models\User;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Str;

/**
 * Dedicated mobile authentication endpoint.
 *
 * Login is intentionally kept independent from operator provisioning and
 * activation flows: the mobile app authenticates an existing User record only.
 */
class MobileLoginController extends Controller
{
    public function login(Request $request): JsonResponse
    {
        $mobile = trim((string) ($request->input('mobile') ?? $request->input('email') ?? $request->input('username')));
        $pin = trim((string) ($request->input('pin') ?? $request->input('password')));

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
            $deviceUuid = 'DEVICE_' . $user->id;
        }
        if ($fingerprintHash === '') {
            $fingerprintHash = 'FINGERPRINT_' . $user->id;
        }

        $binding = DeviceBinding::where('user_id', $user->id)
            ->where('device_uuid', $deviceUuid)
            ->first();

        if ($binding && !$binding->is_active) {
            return response()->json(['status' => 'error', 'message' => 'Device is inactive or revoked for this account.'], 403);
        }

        if ($binding) {
            $binding->update([
                'device_model' => $deviceModel,
                'fingerprint_hash' => $fingerprintHash,
            ]);
        } else {
            DeviceBinding::create([
                'user_id' => $user->id,
                'device_uuid' => $deviceUuid,
                'device_model' => $deviceModel,
                'fingerprint_hash' => $fingerprintHash,
                'is_active' => true,
                'bound_at' => now(),
            ]);
        }

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

        $permissions = $user->getFormattedPermissions();

        return response()->json([
            'status' => 'success',
            'message' => 'Login successful.',
            'user' => [
                'id' => $user->id,
                'name' => $user->name,
                'email' => $user->email,
                'mobile' => $user->mobile,
                'role' => $user->role,
                'is_activated' => (bool) $user->is_activated,
                'permissions' => $permissions,
            ],
            'permissions' => $permissions,
            'tokens' => [
                'access_token' => $accessToken,
                'refresh_token' => $refreshToken,
                'device_token' => $deviceUuid,
                'session_token' => $sessionToken,
                'fingerprint_token' => $fingerprintHash,
            ],
            'access_token' => $accessToken,
            'refresh_token' => $refreshToken,
            'device_token' => $deviceUuid,
            'session_token' => $sessionToken,
            'fingerprint_token' => $fingerprintHash,
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
}
