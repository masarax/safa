<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Models\User;
use App\Models\DeviceBinding;
use App\Models\AuthSession;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\Validator;
use Illuminate\Support\Str;

class AuthJWTController extends Controller
{
    /**
     * Authenticate user and issue 5 security tokens.
     */
    public function login(Request $request)
    {
        $validator = Validator::make($request->all(), [
            'email' => 'required_without:username|string',
            'username' => 'required_without:email|string',
            'password' => 'required|string',
        ]);

        if ($validator->fails()) {
            return response()->json([
                'status' => 'error',
                'message' => 'Validation failed.',
                'errors' => $validator->errors()
            ], 422);
        }

        $email = $request->input('email') ?? $request->input('username');
        $password = $request->input('password');

        $user = User::where('email', $email)->first();

        if (!$user || !Hash::check($password, $user->password)) {
            return response()->json([
                'status' => 'error',
                'message' => 'Invalid credentials.'
            ], 401);
        }

        $deviceUuid = $request->input('device_uuid') ?? $request->header('X-SAFA-DEVICE-TOKEN');
        $fingerprintHash = $request->input('fingerprint_hash') ?? $request->header('X-SAFA-FINGERPRINT-TOKEN');
        $deviceModel = $request->input('device_model') ?? $request->header('X-SAFA-DEVICE-MODEL', 'Unknown Device');

        if (empty($deviceUuid) || empty($fingerprintHash)) {
            return response()->json([
                'status' => 'error',
                'message' => 'Device UUID (X-SAFA-DEVICE-TOKEN) and Fingerprint Hash (X-SAFA-FINGERPRINT-TOKEN) are required.'
            ], 422);
        }

        // Check device binding
        $deviceBinding = DeviceBinding::where('user_id', $user->id)
            ->where('device_uuid', $deviceUuid)
            ->first();

        if ($deviceBinding) {
            if (!$deviceBinding->is_active) {
                return response()->json([
                    'status' => 'error',
                    'message' => 'Device is inactive or revoked for this account.'
                ], 403);
            }

            if ($deviceBinding->fingerprint_hash !== $fingerprintHash) {
                $deviceBinding->fingerprint_hash = $fingerprintHash;
                $deviceBinding->save();
            }
        } else {
            // Register new device binding
            $deviceBinding = DeviceBinding::create([
                'user_id' => $user->id,
                'device_uuid' => $deviceUuid,
                'device_model' => $deviceModel,
                'fingerprint_hash' => $fingerprintHash,
                'is_active' => true,
                'bound_at' => now(),
            ]);
        }

        // Generate 5 tokens
        $sessionToken = Str::random(64);
        $refreshToken = Str::random(64);

        $payload = [
            'iss' => config('app.url', 'safa-backend'),
            'sub' => $user->id,
            'user_id' => $user->id,
            'device_uuid' => $deviceUuid,
            'session_token' => $sessionToken,
            'iat' => time(),
            'exp' => time() + (24 * 3600), // 24 hours validity
        ];

        $accessToken = static::generateJwt($payload);

        AuthSession::create([
            'user_id' => $user->id,
            'device_uuid' => $deviceUuid,
            'access_token' => $accessToken,
            'refresh_token' => $refreshToken,
            'session_token' => $sessionToken,
            'expires_at' => now()->addDays(30),
            'is_revoked' => false,
        ]);

        return response()->json([
            'status' => 'success',
            'message' => 'Login successful.',
            'user' => [
                'id' => $user->id,
                'name' => $user->name,
                'email' => $user->email,
            ],
            'tokens' => [
                'access_token' => $accessToken,
                'refresh_token' => $refreshToken,
                'device_token' => $deviceUuid,
                'session_token' => $sessionToken,
                'fingerprint_token' => $fingerprintHash,
            ]
        ]);
    }

    /**
     * Refresh Access Token if Refresh Token and Device Token match.
     */
    public function refreshToken(Request $request)
    {
        $refreshToken = $request->input('refresh_token') ?? $request->header('X-SAFA-REFRESH-TOKEN');
        $deviceUuid = $request->input('device_token') ?? $request->input('device_uuid') ?? $request->header('X-SAFA-DEVICE-TOKEN');

        if (empty($refreshToken) || empty($deviceUuid)) {
            return response()->json([
                'status' => 'error',
                'message' => 'Missing refresh token or device token.'
            ], 400);
        }

        $session = AuthSession::where('refresh_token', $refreshToken)
            ->where('device_uuid', $deviceUuid)
            ->where('is_revoked', false)
            ->first();

        if (!$session) {
            return response()->json([
                'status' => 'error',
                'message' => 'Invalid or revoked refresh session.'
            ], 401);
        }

        if ($session->expires_at && $session->expires_at->isPast()) {
            return response()->json([
                'status' => 'error',
                'message' => 'Session expired. Please log in again.'
            ], 401);
        }

        // Verify Device Binding
        $deviceBinding = DeviceBinding::where('user_id', $session->user_id)
            ->where('device_uuid', $deviceUuid)
            ->where('is_active', true)
            ->first();

        if (!$deviceBinding) {
            return response()->json([
                'status' => 'error',
                'message' => 'Device is not bound or inactive.'
            ], 403);
        }

        // Issue new Access Token
        $payload = [
            'iss' => config('app.url', 'safa-backend'),
            'sub' => $session->user_id,
            'user_id' => $session->user_id,
            'device_uuid' => $deviceUuid,
            'session_token' => $session->session_token,
            'iat' => time(),
            'exp' => time() + (24 * 3600),
        ];

        $newAccessToken = static::generateJwt($payload);

        $session->access_token = $newAccessToken;
        $session->save();

        return response()->json([
            'status' => 'success',
            'message' => 'Access token refreshed successfully.',
            'tokens' => [
                'access_token' => $newAccessToken,
                'refresh_token' => $session->refresh_token,
                'device_token' => $deviceUuid,
                'session_token' => $session->session_token,
                'fingerprint_token' => $deviceBinding->fingerprint_hash,
            ]
        ]);
    }

    /**
     * Bind a new hardware device UUID + Fingerprint hash.
     */
    public function bindDevice(Request $request)
    {
        $user = $request->user();

        if (!$user && $request->has('email') && $request->has('password')) {
            if (Auth::attempt($request->only('email', 'password'))) {
                $user = Auth::user();
            }
        }

        if (!$user) {
            return response()->json([
                'status' => 'error',
                'message' => 'Unauthorized or invalid credentials.'
            ], 401);
        }

        $deviceUuid = $request->input('device_uuid') ?? $request->header('X-SAFA-DEVICE-TOKEN');
        $fingerprintHash = $request->input('fingerprint_hash') ?? $request->header('X-SAFA-FINGERPRINT-TOKEN');
        $deviceModel = $request->input('device_model') ?? $request->header('X-SAFA-DEVICE-MODEL', 'Hardware Device');

        if (empty($deviceUuid) || empty($fingerprintHash)) {
            return response()->json([
                'status' => 'error',
                'message' => 'Missing device_uuid or fingerprint_hash.'
            ], 422);
        }

        $binding = DeviceBinding::updateOrCreate(
            [
                'user_id' => $user->id,
                'device_uuid' => $deviceUuid,
            ],
            [
                'device_model' => $deviceModel,
                'fingerprint_hash' => $fingerprintHash,
                'is_active' => true,
                'bound_at' => now(),
            ]
        );

        return response()->json([
            'status' => 'success',
            'message' => 'Device bound successfully.',
            'device_binding' => $binding
        ]);
    }

    /**
     * Helper to generate HS256 JWT Token.
     */
    public static function generateJwt(array $payload): string
    {
        $header = json_encode(['alg' => 'HS256', 'typ' => 'JWT']);
        $base64Header = static::base64UrlEncode($header);
        $base64Payload = static::base64UrlEncode(json_encode($payload));

        $secret = config('app.key', 'safa_secret_jwt_key_2026');
        $signature = hash_hmac('sha256', $base64Header . '.' . $base64Payload, $secret, true);
        $base64Signature = static::base64UrlEncode($signature);

        return $base64Header . '.' . $base64Payload . '.' . $base64Signature;
    }

    /**
     * Helper to decode and verify HS256 JWT Token.
     */
    public static function verifyJwt(string $jwt): ?array
    {
        $parts = explode('.', $jwt);
        if (count($parts) !== 3) {
            return null;
        }

        [$base64Header, $base64Payload, $base64Signature] = $parts;

        $secret = config('app.key', 'safa_secret_jwt_key_2026');
        $expectedSignature = static::base64UrlEncode(hash_hmac('sha256', $base64Header . '.' . $base64Payload, $secret, true));

        if (!hash_equals($expectedSignature, $base64Signature)) {
            return null;
        }

        $payloadStr = static::base64UrlDecode($base64Payload);
        $payload = json_decode($payloadStr, true);

        if (!$payload || !is_array($payload)) {
            return null;
        }

        if (isset($payload['exp']) && time() > $payload['exp']) {
            return null;
        }

        return $payload;
    }

    public static function base64UrlEncode(string $data): string
    {
        return rtrim(strtr(base64_encode($data), '+/', '-_'), '=');
    }

    public static function base64UrlDecode(string $data): string
    {
        $remainder = strlen($data) % 4;
        if ($remainder) {
            $data .= str_repeat('=', 4 - $remainder);
        }
        return base64_decode(strtr($data, '-_', '+/'));
    }
}
