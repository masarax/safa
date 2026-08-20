<?php

namespace App\Http\Controllers;

use App\Models\AuthSession;
use App\Models\DeviceBinding;
use App\Models\User;
use App\Support\CredentialVerifier;
use App\Support\MobileNumber;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

/**
 * Canonical first-factor mobile authentication endpoint.
 *
 * The users model is the only credential authority. Legacy operator data must
 * be migrated explicitly with safa:migrate-legacy-operators before login.
 */
class MobileLoginController extends Controller
{
    public function login(Request $request): JsonResponse
    {
        $mobile = MobileNumber::normalize((string) ($request->input('mobile') ?? $request->input('email') ?? $request->input('username')));
        $pin = $this->normalizeDigits(trim((string) ($request->input('pin') ?? $request->input('password'))));

        if ($mobile === '') return $this->error('MOBILE_REQUIRED', 'Mobile number is required for login.', 422);
        if (!MobileNumber::isValid($mobile)) return $this->error('MOBILE_INVALID', 'Invalid mobile number.', 422);
        if ($pin === '' || !preg_match('/^\d{6}$/', $pin)) return $this->error('PIN_INVALID', '6-Digit PIN is required for login.', 422);

        $user = $this->findUserByMobile($mobile);
        $credentialHash = $user?->pin_hash ?: $user?->password;
        $pinValid = CredentialVerifier::verify($pin, $credentialHash);

        // Unknown identity, wrong PIN and inactive account intentionally share
        // one public failure contract. Activation is evaluated only after the
        // same bcrypt verification work has already happened.
        if (!$user || !$pinValid || !(bool) $user->is_activated) {
            return $this->error('INVALID_CREDENTIALS', 'Mobile number or PIN is incorrect.', 401);
        }

        $deviceUuid = trim((string) ($request->input('device_uuid') ?? $request->input('device_token') ?? $request->header('X-SAFA-DEVICE-TOKEN') ?? ''));
        $fingerprintHash = trim((string) ($request->input('fingerprint_hash') ?? $request->input('fingerprint_token') ?? $request->header('X-SAFA-FINGERPRINT-TOKEN') ?? ''));
        $deviceModel = trim((string) ($request->input('device_model') ?? $request->header('X-SAFA-DEVICE-MODEL') ?? 'Unknown Device'));

        if ($deviceUuid === '') return $this->error('DEVICE_REQUIRED', 'Device identity is required.', 400);
        if ($fingerprintHash === '') return $this->error('FINGERPRINT_REQUIRED', 'Device fingerprint is required.', 400);

        $result = DB::transaction(function () use ($user, $deviceUuid, $fingerprintHash, $deviceModel) {
            $binding = DeviceBinding::query()->where('user_id', $user->id)->where('device_uuid', $deviceUuid)->lockForUpdate()->first();
            if ($binding && !$binding->is_active) {
                return ['error' => $this->error('DEVICE_REVOKED', 'Device is inactive or revoked for this account.', 403)];
            }
            if (!$binding) {
                DeviceBinding::create(['user_id' => $user->id, 'device_uuid' => $deviceUuid, 'device_model' => $deviceModel, 'fingerprint_hash' => $fingerprintHash, 'is_active' => true, 'bound_at' => now()]);
            } else {
                $binding->update(['device_model' => $deviceModel, 'fingerprint_hash' => $fingerprintHash, 'is_active' => true]);
            }

            AuthSession::query()->where('user_id', $user->id)->where('device_uuid', $deviceUuid)->where('is_revoked', false)->update(['is_revoked' => true]);
            $sessionToken = AuthSession::newOpaqueToken();
            $refreshToken = AuthSession::newOpaqueToken();
            $accessToken = AuthJWTController::generateJwt(['iss' => config('app.url', 'safa-backend'), 'sub' => $user->id, 'user_id' => $user->id, 'device_uuid' => $deviceUuid, 'session_token' => $sessionToken, 'iat' => time(), 'exp' => time() + (24 * 3600)]);
            AuthSession::create(['user_id' => $user->id, 'device_uuid' => $deviceUuid, 'access_token' => $accessToken, 'refresh_token' => $refreshToken, 'session_token' => $sessionToken, 'expires_at' => now()->addDays(30), 'is_revoked' => false]);
            return ['user' => $user, 'access_token' => $accessToken, 'refresh_token' => $refreshToken, 'device_token' => $deviceUuid, 'session_token' => $sessionToken, 'fingerprint_token' => $fingerprintHash];
        });

        if (isset($result['error'])) return $result['error'];
        $authenticatedUser = $result['user'];
        $permissions = $authenticatedUser->getFormattedPermissions();
        $tokens = [
            'access_token' => $result['access_token'],
            'refresh_token' => $result['refresh_token'],
            'device_token' => $result['device_token'],
            'session_token' => $result['session_token'],
            'fingerprint_token' => $result['fingerprint_token'],
        ];

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
            'tokens' => $tokens,
            // Backward-compatible aliases retained while deployed clients move
            // to the canonical nested tokens object.
            'access_token' => $tokens['access_token'],
            'refresh_token' => $tokens['refresh_token'],
            'device_token' => $tokens['device_token'],
            'session_token' => $tokens['session_token'],
            'fingerprint_token' => $tokens['fingerprint_token'],
        ], 200);
    }

    private function findUserByMobile(string $mobile): ?User
    {
        $normalized = MobileNumber::normalize($mobile);
        if ($normalized === '') return null;
        $query = User::query()->where('mobile', $normalized);
        $count = $query->count();
        if ($count > 1) abort($this->error('AMBIGUOUS_MOBILE', 'Multiple accounts match this mobile number. Please contact an administrator.', 409));
        if ($count === 1) return $query->first();
        $fallback = User::query()->whereRaw("REPLACE(REPLACE(REPLACE(REPLACE(mobile, ' ', ''), '-', ''), '(', ''), ')', '') = ?", [$normalized]);
        $fallbackCount = $fallback->count();
        if ($fallbackCount > 1) abort($this->error('AMBIGUOUS_MOBILE', 'Multiple accounts match this mobile number. Please contact an administrator.', 409));
        return $fallbackCount === 1 ? $fallback->first() : null;
    }

    private function error(string $code, string $message, int $status, array $details = []): JsonResponse
    {
        $payload = ['status' => 'error', 'message' => $message, 'error' => ['code' => $code, 'message' => $message]];
        if ($details !== []) { $payload['errors'] = $details; $payload['error']['details'] = $details; }
        return response()->json($payload, $status);
    }

    private function normalizeDigits(string $value): string
    {
        return strtr($value, ['٠'=>'0','١'=>'1','٢'=>'2','٣'=>'3','٤'=>'4','٥'=>'5','٦'=>'6','٧'=>'7','٨'=>'8','٩'=>'9','۰'=>'0','۱'=>'1','۲'=>'2','۳'=>'3','۴'=>'4','۵'=>'5','۶'=>'6','۷'=>'7','۸'=>'8','۹'=>'9','০'=>'0','১'=>'1','২'=>'2','৩'=>'3','৪'=>'4','৫'=>'5','৬'=>'6','৭'=>'7','৮'=>'8','৯'=>'9']);
    }
}
