<?php

namespace App\Http\Controllers;

use App\Models\AuthSession;
use App\Models\DeviceBinding;
use App\Models\User;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
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

        $role = User::normalizeRole((string) $request->input('role'));
        if (!$actor->canManageRole($role)) return $this->forbidden();

        $hash = Hash::make((string) $request->input('pin'));
        $user = DB::transaction(function () use ($request, $hash, $role) {
            $user = User::create([
                'name' => trim($request->string('name')->toString()),
                'email' => $request->filled('email') ? strtolower(trim((string) $request->input('email'))) : null,
                'mobile' => $request->string('mobile')->toString(),
                'role' => $role,
                'pin_hash' => $hash,
                'password' => $hash,
                'is_activated' => $request->has('is_activated') ? (bool) $request->boolean('is_activated') : true,
                'permissions' => User::permissionsForRole($role),
            ]);
            $this->syncOperatorAccount($user);
            return $user;
        });

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
        if (!$user || !$actor->canManageRole((string) $user->role)) {
            return response()->json(['status' => 'error', 'message' => 'User not found.'], 404);
        }

        $roles = $this->manageableRoles($actor);
        $validator = Validator::make($request->all(), [
            'name' => ['sometimes', 'string', 'max:255'],
            'mobile' => ['sometimes', 'string', 'max:30', 'unique:users,mobile,' . $user->id],
            'email' => ['sometimes', 'nullable', 'email', 'max:255', 'unique:users,email,' . $user->id],
            'role' => ['sometimes', 'in:' . implode(',', $roles)],
            'pin' => ['sometimes', 'digits:6'],
            'password' => ['sometimes', 'string', 'min:6', 'max:255'],
            'is_activated' => ['sometimes', 'boolean'],
        ]);
        if ($validator->fails()) {
            return response()->json(['status' => 'error', 'message' => 'Validation failed.', 'errors' => $validator->errors()], 422);
        }

        $targetRole = $request->has('role')
            ? User::normalizeRole((string) $request->input('role'))
            : User::normalizeRole((string) $user->role);
        if (!$actor->canManageRole($targetRole)) return $this->forbidden();

        DB::transaction(function () use ($request, $user, $targetRole) {
            if ($request->has('name')) $user->name = trim((string) $request->input('name'));
            if ($request->has('email')) $user->email = $request->filled('email') ? strtolower(trim((string) $request->input('email'))) : null;
            if ($request->has('mobile')) $user->mobile = (string) $request->input('mobile');
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
        if (!$user || !$actor->canManageRole((string) $user->role)) {
            return response()->json(['status' => 'error', 'message' => 'User not found.'], 404);
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
