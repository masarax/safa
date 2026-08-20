<?php

namespace App\Http\Controllers;

use App\Models\Account;
use App\Models\AuthSession;
use App\Models\DeviceBinding;
use App\Models\User;
use App\Support\MobileNumber;
use Illuminate\Database\QueryException;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Schema;
use Illuminate\Support\Facades\Validator;

class UserManagementController extends Controller
{
    public function index(Request $request): JsonResponse
    {
        $actor = $this->getManager($request);
        if (!$actor) return $this->forbidden();

        $roles = $this->manageableRoles($actor);
        $users = User::query()
            ->whereIn('role', array_values(array_unique(array_merge($roles, in_array(User::ROLE_USER, $roles, true) ? ['staff'] : []))))
            ->orderBy('id')
            ->get()
            ->filter(fn (User $user) => $actor->canManageRole((string) $user->role))
            ->map(fn (User $user) => $this->serializeUser($user))
            ->values();

        return response()->json(['status' => 'success', 'users' => $users]);
    }

    public function store(Request $request): JsonResponse
    {
        $actor = $this->getManager($request);
        if (!$actor) return $this->forbidden();

        $mobile = MobileNumber::normalize((string) $request->input('mobile'));
        $request->merge(['mobile' => $mobile]);
        $roles = $this->manageableRoles($actor);
        $validator = Validator::make($request->all(), [
            'name' => ['required', 'string', 'max:255'],
            'mobile' => ['required', 'string', 'max:30', 'unique:users,mobile'],
            'email' => ['nullable', 'email', 'max:255', 'unique:users,email'],
            'role' => ['required', 'in:' . implode(',', $roles)],
            'pin' => ['required', 'digits:6'],
            'is_activated' => ['nullable', 'boolean'],
        ]);
        if ($validator->fails()) {
            return response()->json(['status' => 'error', 'message' => 'Validation failed.', 'errors' => $validator->errors()], 422);
        }
        if (!MobileNumber::isValid($mobile)) return $this->invalidMobile();
        if ($this->canonicalMobileConflict($mobile)) return $this->duplicateMobile();

        $role = User::normalizeRole((string) $request->input('role'));
        if (!$actor->canManageRole($role)) return $this->forbidden();

        $hash = Hash::make((string) $request->input('pin'));
        try {
            $user = DB::transaction(function () use ($request, $hash, $role, $mobile) {
                $user = User::create([
                    'name' => trim($request->string('name')->toString()),
                    'email' => $request->filled('email') ? strtolower(trim((string) $request->input('email'))) : null,
                    'mobile' => $mobile,
                    'role' => $role,
                    'pin_hash' => $hash,
                    'password' => $hash,
                    'is_activated' => $request->has('is_activated') ? (bool) $request->boolean('is_activated') : true,
                    'permissions' => User::permissionsForRole($role),
                ]);
                $this->syncOperatorAccount($user);
                return $user;
            });
        } catch (QueryException $e) {
            if ($this->canonicalMobileConflict($mobile)) return $this->duplicateMobile();
            throw $e;
        }

        $serialized = $this->serializeUser($user);
        return response()->json([
            'status' => 'success',
            'message' => 'User account created successfully.',
            'user' => $serialized,
            'operator' => $serialized,
        ], 201);
    }

    public function update(Request $request, int $id): JsonResponse
    {
        if (!in_array(strtoupper($request->method()), ['PUT', 'PATCH'], true)) {
            return response()->json(['status' => 'error', 'message' => 'Method not allowed.'], 405);
        }

        $actor = $this->getManager($request);
        if (!$actor) return $this->forbidden();

        $user = User::find($id);
        if (!$user) {
            return response()->json(['status' => 'error', 'message' => 'User not found.'], 404);
        }
        if ((int) $user->id === (int) $actor->id) {
            return response()->json(['status' => 'error', 'message' => 'Administrators cannot modify their own managed account through this endpoint.'], 400);
        }
        if (!$actor->canManageRole((string) $user->role)) {
            return response()->json(['status' => 'error', 'message' => 'User not found.'], 404);
        }

        $mobile = null;
        if ($request->has('mobile')) {
            $mobile = MobileNumber::normalize((string) $request->input('mobile'));
            $request->merge(['mobile' => $mobile]);
        }

        $roles = $this->manageableRoles($actor);
        $validator = Validator::make($request->all(), [
            'name' => ['sometimes', 'string', 'max:255'],
            'mobile' => ['sometimes', 'required', 'string', 'max:30', 'unique:users,mobile,' . $user->id],
            'email' => ['sometimes', 'nullable', 'email', 'max:255', 'unique:users,email,' . $user->id],
            'role' => ['sometimes', 'in:' . implode(',', $roles)],
            'pin' => ['sometimes', 'digits:6'],
            'password' => ['sometimes', 'string', 'min:6', 'max:255'],
            'is_activated' => ['sometimes', 'boolean'],
        ]);
        if ($validator->fails()) {
            return response()->json(['status' => 'error', 'message' => 'Validation failed.', 'errors' => $validator->errors()], 422);
        }
        if ($mobile !== null && !MobileNumber::isValid($mobile)) return $this->invalidMobile();
        if ($mobile !== null && $this->canonicalMobileConflict($mobile, (int) $user->id)) return $this->duplicateMobile();

        $targetRole = $request->has('role')
            ? User::normalizeRole((string) $request->input('role'))
            : User::normalizeRole((string) $user->role);
        if (!$actor->canManageRole($targetRole)) return $this->forbidden();

        try {
            DB::transaction(function () use ($request, $user, $targetRole, $mobile) {
                if ($request->has('name')) $user->name = trim((string) $request->input('name'));
                if ($request->has('email')) $user->email = $request->filled('email') ? strtolower(trim((string) $request->input('email'))) : null;
                if ($mobile !== null) $user->mobile = $mobile;
                if ($request->has('role')) $user->role = $targetRole;
                if ($request->has('is_activated')) $user->is_activated = (bool) $request->boolean('is_activated');

                $secret = $request->input('pin') ?? $request->input('password');
                if ($secret !== null && $secret !== '') {
                    $hash = Hash::make((string) $secret);
                    $user->pin_hash = $hash;
                    $user->password = $hash;
                    AuthSession::where('user_id', $user->id)->update(['is_revoked' => true]);
                    DeviceBinding::where('user_id', $user->id)->update(['is_active' => false]);
                }

                $user->permissions = User::permissionsForRole($targetRole);
                $user->save();
                $this->syncOperatorAccount($user);
            });
        } catch (QueryException $e) {
            if ($mobile !== null && $this->canonicalMobileConflict($mobile, (int) $user->id)) return $this->duplicateMobile();
            throw $e;
        }

        return response()->json([
            'status' => 'success',
            'message' => 'User updated successfully.',
            'user' => $this->serializeUser($user->fresh()),
        ]);
    }

    public function destroy(Request $request, int $id): JsonResponse
    {
        $actor = $this->getManager($request);
        if (!$actor) return $this->forbidden();
        if (!$request->boolean('confirmed')) {
            return response()->json([
                'status' => 'confirmation_required',
                'message' => 'Confirmation required before deleting user.',
                'requires_confirmation' => true,
            ], 409);
        }

        $user = User::find($id);
        if (!$user) {
            return response()->json(['status' => 'error', 'message' => 'User not found.'], 404);
        }
        if ((int) $user->id === (int) $actor->id) {
            return response()->json(['status' => 'error', 'message' => 'Administrators cannot delete their own account.'], 400);
        }
        if (!$actor->canManageRole((string) $user->role)) {
            return response()->json(['status' => 'error', 'message' => 'User not found.'], 404);
        }

        $ownedAccountIds = Account::query()
            ->where('owner_user_id', $user->id)
            ->orderBy('id')
            ->pluck('id')
            ->map(fn ($accountId) => (int) $accountId)
            ->values();
        if ($ownedAccountIds->isNotEmpty()) {
            return response()->json([
                'status' => 'error',
                'code' => 'ACCOUNT_OWNERSHIP_REQUIRED',
                'message' => 'Transfer or resolve owned business accounts before deleting this user.',
                'account_ids' => $ownedAccountIds,
            ], 409);
        }

        DB::transaction(function () use ($user) {
            AuthSession::where('user_id', $user->id)->delete();
            DeviceBinding::where('user_id', $user->id)->delete();
            if (DB::getSchemaBuilder()->hasTable('operator_accounts')) {
                DB::table('operator_accounts')->where('user_id', $user->id)->delete();
            }
            $user->delete();
        });

        return response()->json(['status' => 'success', 'message' => 'User deleted successfully.']);
    }

    private function manageableRoles(User $actor): array
    {
        return $actor->isSuperAdmin()
            ? [User::ROLE_ADMIN, User::ROLE_BUSINESS_USER, User::ROLE_USER]
            : [User::ROLE_BUSINESS_USER, User::ROLE_USER];
    }

    private function getManager(Request $request): ?User
    {
        $user = $request->user();
        if (!$user) {
            $token = $request->bearerToken()
                ?? $request->header('X-SAFA-ACCESS-TOKEN')
                ?? $request->input('access_token');
            if ($token) {
                $payload = AuthJWTController::verifyJwt($token);
                if ($payload && isset($payload['user_id'])) $user = User::find((int) $payload['user_id']);
            }
        }

        return $user && $user->is_activated && $user->canManageUsers() ? $user : null;
    }

    private function canonicalMobileConflict(string $mobile, ?int $ignoreUserId = null): bool
    {
        $users = User::query()->where('mobile', $mobile);
        if ($ignoreUserId !== null) $users->whereKeyNot($ignoreUserId);
        if ($users->exists()) return true;

        if (!Schema::hasTable('operator_accounts')) return false;

        return DB::table('operator_accounts')
            ->select(['user_id', 'mobile'])
            ->whereNotNull('mobile')
            ->get()
            ->contains(function ($operator) use ($mobile, $ignoreUserId): bool {
                if ($ignoreUserId !== null && (int) ($operator->user_id ?? 0) === $ignoreUserId) return false;
                return MobileNumber::normalize((string) $operator->mobile) === $mobile;
            });
    }

    private function invalidMobile(): JsonResponse
    {
        return response()->json([
            'status' => 'error',
            'message' => 'Validation failed.',
            'errors' => ['mobile' => ['The mobile number is not valid.']],
        ], 422);
    }

    private function duplicateMobile(): JsonResponse
    {
        return response()->json([
            'status' => 'error',
            'message' => 'Validation failed.',
            'errors' => ['mobile' => ['The mobile number has already been taken.']],
        ], 422);
    }

    private function syncOperatorAccount(User $user): void
    {
        if (!DB::getSchemaBuilder()->hasTable('operator_accounts')) return;

        DB::table('operator_accounts')->updateOrInsert(
            ['user_id' => $user->id],
            [
                'name' => $user->name,
                'email' => $user->email,
                'mobile' => $user->mobile,
                'role' => $user->role,
                'pin_hash' => $user->pin_hash,
                'is_activated' => $user->is_activated,
                'permissions' => json_encode($user->getFormattedPermissions()),
                'updated_at' => now(),
                'created_at' => $user->created_at ?? now(),
            ]
        );
    }

    private function serializeUser(User $user): array
    {
        return [
            'id' => $user->id,
            'name' => $user->name,
            'email' => $user->email,
            'mobile' => $user->mobile,
            'role' => User::normalizeRole((string) $user->role),
            'role_label' => User::roleLabel((string) $user->role),
            'is_activated' => (bool) $user->is_activated,
            'permissions' => $user->getFormattedPermissions(),
            'created_at' => $user->created_at?->toIso8601String(),
            'updated_at' => $user->updated_at?->toIso8601String(),
        ];
    }

    private function forbidden(): JsonResponse
    {
        return response()->json([
            'status' => 'error',
            'message' => 'Forbidden. Administrator access is required.',
        ], 403);
    }
}
