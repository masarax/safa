<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Models\User;
use App\Models\DeviceBinding;
use App\Models\AuthSession;
use App\Models\UserAccountShare;
use App\Models\Account;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\Validator;
use Illuminate\Support\Facades\Schema;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

class AuthJWTController extends Controller

{
    /**
     * Activate SuperAdmin account (First-time setup: Name, Email, Mobile, New PIN, Security Questions).
     */
    public function activateSuperAdmin(Request $request)
    {
        $validator = Validator::make($request->all(), [
            'name'               => 'required|string|max:255',
            'email'              => 'required|email|max:255',
            'mobile'             => 'required|string|max:30',
            'pin'                => 'required_without:new_pin|string|min:4|max:10',
            'new_pin'            => 'required_without:pin|string|min:4|max:10',
            'security_questions' => 'nullable',
        ]);

        if ($validator->fails()) {
            return response()->json([
                'status'  => 'error',
                'message' => 'Validation failed.',
                'errors'  => $validator->errors()
            ], 422);
        }

        $mobile = $request->input('mobile');
        $pin = $request->input('pin') ?? $request->input('new_pin');
        $securityQuestions = $request->input('security_questions');

        // Find SuperAdmin user by mobile, email, or role superadmin
        $superAdmin = User::where('mobile', $mobile)
            ->orWhere('email', $request->input('email'))
            ->orWhere('role', 'superadmin')
            ->first();

        if (!$superAdmin) {
            $superAdmin = new User();
        }

        if ($superAdmin->is_activated && $superAdmin->role === 'superadmin') {
            return response()->json([
                'status'  => 'error',
                'message' => 'SuperAdmin account is already activated.'
            ], 400);
        }

        $allPermissions = User::defaultPermissions(true);

        $superAdmin->name = $request->input('name');
        $superAdmin->email = $request->input('email');
        $superAdmin->mobile = $mobile;
        $superAdmin->role = 'superadmin';
        $superAdmin->pin_hash = Hash::make($pin);
        $superAdmin->password = Hash::make($pin);
        $superAdmin->is_activated = true;

        if ($securityQuestions) {
            $allPermissions['_security_questions'] = $securityQuestions;
        }

        $superAdmin->permissions = $allPermissions;
        $superAdmin->save();

        if (Schema::hasTable('operator_accounts')) {
            DB::table('operator_accounts')->updateOrInsert(
                ['mobile' => $superAdmin->mobile],
                [
                    'user_id'      => $superAdmin->id,
                    'name'         => $superAdmin->name,
                    'email'        => $superAdmin->email,
                    'mobile'       => $superAdmin->mobile,
                    'role'         => 'superadmin',
                    'pin_hash'     => $superAdmin->pin_hash,
                    'is_activated' => true,
                    'permissions'  => json_encode($superAdmin->getFormattedPermissions()),
                    'updated_at'   => now(),
                    'created_at'   => now(),
                ]
            );
        }

        // Issue tokens for immediate login
        $deviceUuid = $request->input('device_uuid') ?? $request->header('X-SAFA-DEVICE-TOKEN') ?? ('SUPERADMIN_DEVICE_' . $superAdmin->id);
        $fingerprintHash = $request->input('fingerprint_hash') ?? $request->header('X-SAFA-FINGERPRINT-TOKEN') ?? ('SUPERADMIN_FP_' . $superAdmin->id);
        $deviceModel = $request->input('device_model') ?? $request->header('X-SAFA-DEVICE-MODEL', 'Primary Device');

        DeviceBinding::updateOrCreate(
            ['user_id' => $superAdmin->id, 'device_uuid' => $deviceUuid],
            ['device_model' => $deviceModel, 'fingerprint_hash' => $fingerprintHash, 'is_active' => true, 'bound_at' => now()]
        );

        $sessionToken = Str::random(64);
        $refreshToken = Str::random(64);

        $payload = [
            'iss'           => config('app.url', 'safa-backend'),
            'sub'           => $superAdmin->id,
            'user_id'       => $superAdmin->id,
            'device_uuid'   => $deviceUuid,
            'session_token' => $sessionToken,
            'iat'           => time(),
            'exp'           => time() + (24 * 3600),
        ];

        $accessToken = static::generateJwt($payload);

        AuthSession::create([
            'user_id'       => $superAdmin->id,
            'device_uuid'   => $deviceUuid,
            'access_token'  => $accessToken,
            'refresh_token' => $refreshToken,
            'session_token' => $sessionToken,
            'expires_at'    => now()->addDays(30),
            'is_revoked'    => false,
        ]);

        $formattedPermissions = $superAdmin->getFormattedPermissions();

        return response()->json([
            'status'               => 'success',
            'message'              => 'SuperAdmin account activated successfully.',
            'is_activated'         => true,
            'requires_activation'  => false,
            'user'                 => [
                'id'           => $superAdmin->id,
                'name'         => $superAdmin->name,
                'email'        => $superAdmin->email,
                'mobile'       => $superAdmin->mobile,
                'role'         => $superAdmin->role,
                'is_activated' => true,
                'permissions'  => $formattedPermissions,
            ],
            'permissions'          => $formattedPermissions,
            'tokens'               => [
                'access_token'      => $accessToken,
                'refresh_token'     => $refreshToken,
                'device_token'      => $deviceUuid,
                'session_token'     => $sessionToken,
                'fingerprint_token' => $fingerprintHash,
            ],
            'access_token'         => $accessToken,
            'refresh_token'        => $refreshToken,
            'device_token'         => $deviceUuid,
            'session_token'        => $sessionToken,
            'fingerprint_token'    => $fingerprintHash,
        ], 200);
    }

    /**
     * Master endpoint handler for operators management (GET, POST, PUT, DELETE).
     */
    public function operators(Request $request, $id = null)
    {
        $superAdmin = $this->getAuthenticatedSuperAdmin($request);
        if (!$superAdmin) {
            return response()->json([
                'status'  => 'error',
                'message' => 'Forbidden. SuperAdmin access required.'
            ], 403);
        }

        $method = strtoupper($request->method());

        switch ($method) {
            case 'GET':
                return $this->getOperators($request);
            case 'POST':
                return $this->createOperator($request);
            case 'PUT':
            case 'PATCH':
                return $this->updateOperator($request, $id);
            case 'DELETE':
                return $this->deleteOperator($request, $id);
            default:
                return response()->json(['status' => 'error', 'message' => 'Method not allowed.'], 405);
        }
    }

    /**
     * List all operators.
     */
    public function getOperators(Request $request)
    {
        $superAdmin = $this->getAuthenticatedSuperAdmin($request);
        if (!$superAdmin) {
            return response()->json(['status' => 'error', 'message' => 'Forbidden. SuperAdmin access required.'], 403);
        }

        $operators = User::whereIn('role', ['manager', 'staff'])
            ->get()
            ->map(function ($op) {
                return [
                    'id'           => $op->id,
                    'name'         => $op->name,
                    'email'        => $op->email,
                    'mobile'       => $op->mobile,
                    'role'         => $op->role,
                    'is_activated' => (bool)$op->is_activated,
                    'permissions'  => $op->getFormattedPermissions(),
                    'created_at'   => $op->created_at?->toIso8601String(),
                    'updated_at'   => $op->updated_at?->toIso8601String(),
                ];
            });

        return response()->json([
            'status'    => 'success',
            'operators' => $operators
        ], 200);
    }

    /**
     * Create a new operator account with granular JSON permissions.
     */
    public function createOperator(Request $request)
    {
        $superAdmin = $this->getAuthenticatedSuperAdmin($request);
        if (!$superAdmin) {
            return response()->json(['status' => 'error', 'message' => 'Forbidden. SuperAdmin access required.'], 403);
        }

        $validator = Validator::make($request->all(), [
            'name'        => 'required|string|max:255',
            'mobile'      => 'required|string|max:30|unique:users,mobile',
            'email'       => 'nullable|email|max:255',
            'role'        => 'required|string|in:manager,staff',
            'pin'         => 'required|string|min:4|max:10',
            'permissions' => 'nullable|array',
        ]);

        if ($validator->fails()) {
            return response()->json([
                'status'  => 'error',
                'message' => 'Validation failed.',
                'errors'  => $validator->errors()
            ], 422);
        }

        $role = $request->input('role');
        $inputPermissions = $request->input('permissions') ?? [];

        $defaultPerms = User::defaultPermissions($role === 'manager');
        if (is_array($inputPermissions)) {
            foreach ($defaultPerms as $k => $v) {
                if (array_key_exists($k, $inputPermissions)) {
                    $defaultPerms[$k] = (bool)$inputPermissions[$k];
                }
            }
        }

        $pin = $request->input('pin');
        $pinHash = Hash::make($pin);

        $operator = User::create([
            'name'         => $request->input('name'),
            'email'        => $request->input('email') ?? ($request->input('mobile') . '@safa.local'),
            'mobile'       => $request->input('mobile'),
            'role'         => $role,
            'pin_hash'     => $pinHash,
            'password'     => $pinHash,
            'is_activated' => $request->has('is_activated') ? (bool)$request->input('is_activated') : true,
            'permissions'  => $defaultPerms,
        ]);

        if (Schema::hasTable('operator_accounts')) {
            DB::table('operator_accounts')->updateOrInsert(
                ['mobile' => $operator->mobile],
                [
                    'user_id'      => $operator->id,
                    'name'         => $operator->name,
                    'email'        => $operator->email,
                    'mobile'       => $operator->mobile,
                    'role'         => $operator->role,
                    'pin_hash'     => $operator->pin_hash,
                    'is_activated' => $operator->is_activated,
                    'permissions'  => json_encode($operator->getFormattedPermissions()),
                    'updated_at'   => now(),
                    'created_at'   => now(),
                ]
            );
        }

        return response()->json([
            'status'   => 'success',
            'message'  => 'Operator account created successfully.',
            'operator' => [
                'id'           => $operator->id,
                'name'         => $operator->name,
                'email'        => $operator->email,
                'mobile'       => $operator->mobile,
                'role'         => $operator->role,
                'is_activated' => (bool)$operator->is_activated,
                'permissions'  => $operator->getFormattedPermissions(),
                'created_at'   => $operator->created_at?->toIso8601String(),
                'updated_at'   => $operator->updated_at?->toIso8601String(),
            ]
        ], 201);
    }

    /**
     * Update an existing operator.
     */
    public function updateOperator(Request $request, $id = null)
    {
        $superAdmin = $this->getAuthenticatedSuperAdmin($request);
        if (!$superAdmin) {
            return response()->json(['status' => 'error', 'message' => 'Forbidden. SuperAdmin access required.'], 403);
        }

        $opId = $id ?? $request->input('id') ?? $request->input('operator_id');
        $operator = User::find($opId);

        if (!$operator || $operator->role === 'superadmin') {
            return response()->json(['status' => 'error', 'message' => 'Operator not found.'], 404);
        }

        $validator = Validator::make($request->all(), [
            'name'         => 'nullable|string|max:255',
            'mobile'       => 'nullable|string|max:30|unique:users,mobile,' . $operator->id,
            'email'        => 'nullable|email|max:255',
            'role'         => 'nullable|string|in:manager,staff',
            'pin'          => 'nullable|string|min:4|max:10',
            'permissions'  => 'nullable|array',
            'is_activated' => 'nullable|boolean',
        ]);

        if ($validator->fails()) {
            return response()->json([
                'status'  => 'error',
                'message' => 'Validation failed.',
                'errors'  => $validator->errors()
            ], 422);
        }

        if ($request->has('name')) $operator->name = $request->input('name');
        if ($request->has('email')) $operator->email = $request->input('email');
        if ($request->has('mobile')) $operator->mobile = $request->input('mobile');
        if ($request->has('role')) $operator->role = $request->input('role');
        if ($request->has('is_activated')) $operator->is_activated = (bool)$request->input('is_activated');

        if ($request->filled('pin')) {
            $pin = $request->input('pin');
            $pinHash = Hash::make($pin);
            $operator->pin_hash = $pinHash;
            $operator->password = $pinHash;
        }

        if ($request->has('permissions')) {
            $inputPerms = $request->input('permissions');
            if (is_array($inputPerms)) {
                $currentPerms = $operator->getFormattedPermissions();
                foreach ($currentPerms as $k => $v) {
                    if (array_key_exists($k, $inputPerms)) {
                        $currentPerms[$k] = (bool)$inputPerms[$k];
                    }
                }
                $operator->permissions = $currentPerms;
            }
        }

        $operator->save();

        if (Schema::hasTable('operator_accounts')) {
            DB::table('operator_accounts')->updateOrInsert(
                ['mobile' => $operator->mobile],
                [
                    'user_id'      => $operator->id,
                    'name'         => $operator->name,
                    'email'        => $operator->email,
                    'mobile'       => $operator->mobile,
                    'role'         => $operator->role,
                    'pin_hash'     => $operator->pin_hash,
                    'is_activated' => $operator->is_activated,
                    'permissions'  => json_encode($operator->getFormattedPermissions()),
                    'updated_at'   => now(),
                ]
            );
        }

        return response()->json([
            'status'   => 'success',
            'message'  => 'Operator updated successfully.',
            'operator' => [
                'id'           => $operator->id,
                'name'         => $operator->name,
                'email'        => $operator->email,
                'mobile'       => $operator->mobile,
                'role'         => $operator->role,
                'is_activated' => (bool)$operator->is_activated,
                'permissions'  => $operator->getFormattedPermissions(),
                'created_at'   => $operator->created_at?->toIso8601String(),
                'updated_at'   => $operator->updated_at?->toIso8601String(),
            ]
        ], 200);
    }

    /**
     * Delete an operator account.
     */
    public function deleteOperator(Request $request, $id = null)
    {
        $superAdmin = $this->getAuthenticatedSuperAdmin($request);
        if (!$superAdmin) {
            return response()->json(['status' => 'error', 'message' => 'Forbidden. SuperAdmin access required.'], 403);
        }

        $opId = $id ?? $request->input('id') ?? $request->input('operator_id');
        $operator = User::find($opId);

        if (!$operator) {
            return response()->json(['status' => 'error', 'message' => 'Operator not found.'], 404);
        }

        if ($operator->id === $superAdmin->id) {
            return response()->json(['status' => 'error', 'message' => 'Forbidden. SuperAdmin cannot delete their own account.'], 400);
        }

        // Revoke active sessions, device bindings, and account shares
        AuthSession::where('user_id', $operator->id)->delete();
        DeviceBinding::where('user_id', $operator->id)->delete();
        UserAccountShare::where('owner_user_id', $operator->id)->orWhere('shared_with_user_id', $operator->id)->delete();

        if (Schema::hasTable('operator_accounts')) {
            DB::table('operator_accounts')->where('user_id', $operator->id)->orWhere('mobile', $operator->mobile)->delete();
        }

        $operator->delete();

        return response()->json([
            'status'  => 'success',
            'message' => 'Operator deleted successfully.'
        ], 200);
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
                'status'  => 'error',
                'message' => 'Missing refresh token or device token.'
            ], 400);
        }

        $session = AuthSession::where('refresh_token', $refreshToken)
            ->where('device_uuid', $deviceUuid)
            ->where('is_revoked', false)
            ->first();

        if (!$session) {
            return response()->json([
                'status'  => 'error',
                'message' => 'Invalid or revoked refresh session.'
            ], 401);
        }

        if ($session->expires_at && $session->expires_at->isPast()) {
            return response()->json([
                'status'  => 'error',
                'message' => 'Session expired. Please log in again.'
            ], 401);
        }

        $deviceBinding = DeviceBinding::where('user_id', $session->user_id)
            ->where('device_uuid', $deviceUuid)
            ->where('is_active', true)
            ->first();

        if (!$deviceBinding) {
            return response()->json([
                'status'  => 'error',
                'message' => 'Device is not bound or inactive.'
            ], 403);
        }

        $user = User::find($session->user_id);
        $formattedPermissions = $user ? $user->getFormattedPermissions() : User::defaultPermissions(false);

        $payload = [
            'iss'           => config('app.url', 'safa-backend'),
            'sub'           => $session->user_id,
            'user_id'       => $session->user_id,
            'device_uuid'   => $deviceUuid,
            'session_token' => $session->session_token,
            'iat'           => time(),
            'exp'           => time() + (24 * 3600),
        ];

        $newAccessToken = static::generateJwt($payload);

        $session->access_token = $newAccessToken;
        $session->save();

        return response()->json([
            'status'  => 'success',
            'message' => 'Access token refreshed successfully.',
            'permissions' => $formattedPermissions,
            'tokens' => [
                'access_token'      => $newAccessToken,
                'refresh_token'     => $session->refresh_token,
                'device_token'      => $deviceUuid,
                'session_token'     => $session->session_token,
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

        if (!$user && $request->has('mobile') && ($request->has('pin') || $request->has('password'))) {
            $mobileUser = User::where('mobile', $request->input('mobile'))->first();
            $cred = $request->input('pin') ?? $request->input('password');
            if ($mobileUser && (($mobileUser->pin_hash && Hash::check($cred, $mobileUser->pin_hash)) || ($mobileUser->password && Hash::check($cred, $mobileUser->password)))) {
                $user = $mobileUser;
            }
        }

        if (!$user) {
            return response()->json([
                'status'  => 'error',
                'message' => 'Unauthorized or invalid credentials.'
            ], 401);
        }

        $deviceUuid = $request->input('device_uuid') ?? $request->header('X-SAFA-DEVICE-TOKEN');
        $fingerprintHash = $request->input('fingerprint_hash') ?? $request->header('X-SAFA-FINGERPRINT-TOKEN');
        $deviceModel = $request->input('device_model') ?? $request->header('X-SAFA-DEVICE-MODEL', 'Hardware Device');

        if (empty($deviceUuid) || empty($fingerprintHash)) {
            return response()->json([
                'status'  => 'error',
                'message' => 'Missing device_uuid or fingerprint_hash.'
            ], 422);
        }

        $binding = DeviceBinding::updateOrCreate(
            [
                'user_id'     => $user->id,
                'device_uuid' => $deviceUuid,
            ],
            [
                'device_model'     => $deviceModel,
                'fingerprint_hash' => $fingerprintHash,
                'is_active'        => true,
                'bound_at'         => now(),
            ]
        );

        return response()->json([
            'status'         => 'success',
            'message'        => 'Device bound successfully.',
            'device_binding' => $binding
        ]);
    }

    /**
     * Helper to authenticate requesting user.
     */
    protected function getAuthenticatedUser(Request $request): ?User
    {
        $user = $request->user() ?? Auth::user();
        if (!$user) {
            $token = $request->bearerToken() ?? $request->header('X-SAFA-ACCESS-TOKEN') ?? $request->input('access_token');
            if ($token) {
                $payload = static::verifyJwt($token);
                if ($payload && isset($payload['user_id'])) {
                    $user = User::find($payload['user_id']);
                }
            }
        }

        if (!$user && $request->header('X-Superadmin-Mobile')) {
            $user = User::where('mobile', $request->header('X-Superadmin-Mobile'))->where('role', 'superadmin')->first();
        }

        return $user;
    }

    /**
     * Helper to authenticate superadmin caller.
     */
    protected function getAuthenticatedSuperAdmin(Request $request): ?User
    {
        $user = $this->getAuthenticatedUser($request);

        if (!$user) {
            return null;
        }

        return ($user->role === 'superadmin') ? $user : null;
    }

    /**
     * Share account access with another user's mobile number.
     */
    public function shareAccount(Request $request)
    {
        $owner = $this->getAuthenticatedUser($request);
        if (!$owner) {
            return response()->json([
                'status'  => 'error',
                'message' => 'Unauthorized access.'
            ], 401);
        }

        $validator = Validator::make($request->all(), [
            'mobile'               => 'required|string',
            'account_id'           => 'nullable|integer',
            'permissions'          => 'nullable|array',
            'permissions_override' => 'nullable|array',
        ]);

        if ($validator->fails()) {
            return response()->json([
                'status'  => 'error',
                'message' => 'Validation failed.',
                'errors'  => $validator->errors()
            ], 422);
        }

        $mobile = $request->input('mobile');
        $targetUser = User::where('mobile', $mobile)->first();

        if (!$targetUser) {
            return response()->json([
                'status'  => 'error',
                'message' => 'User with specified mobile number not found.'
            ], 404);
        }

        if ($owner->id === $targetUser->id) {
            return response()->json([
                'status'  => 'error',
                'message' => 'Cannot share account with yourself.'
            ], 400);
        }

        $accountId = $request->input('account_id') ?? 1;
        $permissionsOverride = $request->input('permissions') ?? $request->input('permissions_override') ?? null;

        $share = UserAccountShare::updateOrCreate(
            [
                'owner_user_id'       => $owner->id,
                'shared_with_user_id' => $targetUser->id,
                'account_id'          => $accountId,
            ],
            [
                'permissions_override' => $permissionsOverride,
            ]
        );

        return response()->json([
            'status'  => 'success',
            'message' => 'Account access shared successfully.',
            'share'   => [
                'id'                   => $share->id,
                'owner_user_id'        => $owner->id,
                'shared_with_user_id'  => $targetUser->id,
                'account_id'           => $accountId,
                'permissions_override' => $share->permissions_override,
                'shared_with_user'     => [
                    'id'     => $targetUser->id,
                    'name'   => $targetUser->name,
                    'mobile' => $targetUser->mobile,
                    'role'   => $targetUser->role,
                ],
            ]
        ], 200);
    }

    /**
     * Get list of all accounts shared with current user.
     */
    public function getSharedAccounts(Request $request)
    {
        $currentUser = $this->getAuthenticatedUser($request);
        if (!$currentUser) {
            return response()->json([
                'status'  => 'error',
                'message' => 'Unauthorized access.'
            ], 401);
        }

        $shares = UserAccountShare::with(['owner', 'account'])
            ->where('shared_with_user_id', $currentUser->id)
            ->get();

        $sharedAccountsList = $shares->map(function ($share) {
            $owner = $share->owner;
            return [
                'share_id'             => $share->id,
                'account_id'           => $share->account_id ?? 1,
                'owner_user_id'        => $share->owner_user_id,
                'owner_name'           => $owner?->name ?? 'Unknown Owner',
                'owner_mobile'         => $owner?->mobile,
                'owner_role'           => $owner?->role,
                'permissions_override' => $share->permissions_override,
                'is_owner'             => false,
                'created_at'           => $share->created_at?->toIso8601String(),
            ];
        });

        $ownAccount = [
            'account_id'    => 1,
            'owner_user_id' => $currentUser->id,
            'owner_name'    => $currentUser->name,
            'owner_mobile'  => $currentUser->mobile,
            'owner_role'    => $currentUser->role,
            'permissions'   => $currentUser->getFormattedPermissions(),
            'is_owner'      => true,
        ];

        return response()->json([
            'status'          => 'success',
            'own_account'     => $ownAccount,
            'shared_accounts' => $sharedAccountsList,
        ]);
    }

    /**
     * Switch active token context to selected shared account ID and issue fresh 5 tokens.
     */
    public function switchAccount(Request $request)
    {
        $currentUser = $this->getAuthenticatedUser($request);
        if (!$currentUser) {
            return response()->json([
                'status'  => 'error',
                'message' => 'Unauthorized access.'
            ], 401);
        }

        $validator = Validator::make($request->all(), [
            'account_id'    => 'nullable|integer',
            'owner_user_id' => 'nullable|integer',
            'share_id'      => 'nullable|integer',
        ]);

        if ($validator->fails()) {
            return response()->json([
                'status'  => 'error',
                'message' => 'Validation failed.',
                'errors'  => $validator->errors()
            ], 422);
        }

        $shareId = $request->input('share_id');
        $targetAccountId = $request->input('account_id');
        $targetOwnerId = $request->input('owner_user_id');

        $share = null;
        $isOwner = false;

        if ($shareId) {
            $share = UserAccountShare::where('id', $shareId)
                ->where('shared_with_user_id', $currentUser->id)
                ->first();
        }

        if (!$share && $targetOwnerId && $targetOwnerId != $currentUser->id) {
            $share = UserAccountShare::where('owner_user_id', $targetOwnerId)
                ->where('shared_with_user_id', $currentUser->id)
                ->first();
        }

        if (!$share && $targetAccountId && $targetAccountId != 1 && $targetAccountId != $currentUser->id) {
            $share = UserAccountShare::where('account_id', $targetAccountId)
                ->where('shared_with_user_id', $currentUser->id)
                ->first();
        }

        if ($share) {
            $activeAccountId = $share->account_id ?? 1;
            $activeOwnerId = $share->owner_user_id;

            $basePermissions = $currentUser->getFormattedPermissions();
            if (is_array($share->permissions_override)) {
                foreach ($share->permissions_override as $k => $v) {
                    if (array_key_exists($k, $basePermissions)) {
                        $basePermissions[$k] = (bool)$v;
                    }
                }
            }
            $effectivePermissions = $basePermissions;
        } else {
            $isOwner = true;
            $activeAccountId = $targetAccountId ?? 1;
            $activeOwnerId = $currentUser->id;
            $effectivePermissions = $currentUser->getFormattedPermissions();
        }

        $deviceUuid = $request->input('device_uuid') ?? $request->header('X-SAFA-DEVICE-TOKEN') ?? ('DEVICE_' . $currentUser->id);
        $fingerprintHash = $request->input('fingerprint_hash') ?? $request->header('X-SAFA-FINGERPRINT-TOKEN') ?? ('FINGERPRINT_' . $currentUser->id);
        $deviceModel = $request->input('device_model') ?? $request->header('X-SAFA-DEVICE-MODEL', 'Hardware Device');

        DeviceBinding::updateOrCreate(
            ['user_id' => $currentUser->id, 'device_uuid' => $deviceUuid],
            ['device_model' => $deviceModel, 'fingerprint_hash' => $fingerprintHash, 'is_active' => true, 'bound_at' => now()]
        );

        $sessionToken = Str::random(64);
        $refreshToken = Str::random(64);

        $payload = [
            'iss'           => config('app.url', 'safa-backend'),
            'sub'           => $currentUser->id,
            'user_id'       => $currentUser->id,
            'account_id'    => $activeAccountId,
            'owner_user_id' => $activeOwnerId,
            'device_uuid'   => $deviceUuid,
            'session_token' => $sessionToken,
            'iat'           => time(),
            'exp'           => time() + (24 * 3600),
        ];

        $accessToken = static::generateJwt($payload);

        AuthSession::create([
            'user_id'       => $currentUser->id,
            'device_uuid'   => $deviceUuid,
            'access_token'  => $accessToken,
            'refresh_token' => $refreshToken,
            'session_token' => $sessionToken,
            'expires_at'    => now()->addDays(30),
            'is_revoked'    => false,
        ]);

        return response()->json([
            'status'  => 'success',
            'message' => 'Switched active account context successfully.',
            'active_context' => [
                'account_id'    => $activeAccountId,
                'owner_user_id' => $activeOwnerId,
                'is_owner'      => $isOwner,
            ],
            'user' => [
                'id'           => $currentUser->id,
                'name'         => $currentUser->name,
                'email'        => $currentUser->email,
                'mobile'       => $currentUser->mobile,
                'role'         => $currentUser->role,
                'is_activated' => (bool)$currentUser->is_activated,
                'permissions'  => $effectivePermissions,
            ],
            'permissions' => $effectivePermissions,
            'tokens' => [
                'access_token'      => $accessToken,
                'refresh_token'     => $refreshToken,
                'device_token'      => $deviceUuid,
                'session_token'     => $sessionToken,
                'fingerprint_token' => $fingerprintHash,
            ]
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
