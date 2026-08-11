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
        if (!$this->isSuperAdmin($request)) {
            return $this->forbidden();
        }

        $users = User::query()
            ->whereIn('role', [User::ROLE_ADMIN, User::ROLE_USER])
            ->orderBy('id')
            ->get()
            ->map(fn (User $user) => $this->serializeUser($user));

        return response()->json(['status' => 'success', 'users' => $users]);
    }

    public function store(Request $request): JsonResponse
    {
        if (!$this->isSuperAdmin($request)) {
            return $this->forbidden();
        }

        $validator = Validator::make($request->all(), [
            'name' => ['required', 'string', 'max:255'],
            'mobile' => ['required', 'string', 'max:30', 'unique:users,mobile'],
            'email' => ['nullable', 'email', 'max:255'],
            'role' => ['required', 'in:' . User::ROLE_ADMIN . ',' . User::ROLE_USER],
            'pin' => ['required', 'digits:6'],
            'permissions' => ['nullable', 'array'],
            'is_activated' => ['nullable', 'boolean'],
        ]);

        if ($validator->fails()) {
            return response()->json([
                'status' => 'error',
                'message' => 'Validation failed.',
                'errors' => $validator->errors(),
            ], 422);
        }

        $pinHash = Hash::make((string) $request->input('pin'));
        $role = (string) $request->input('role');
        $permissions = $this->sanitizePermissions($request->input('permissions', []));

        $user = DB::transaction(function () use ($request, $pinHash, $role, $permissions) {
            $user = User::create([
                'name' => $request->string('name')->toString(),
                'email' => $request->input('email') ?: ($request->input('mobile') . '@safa.local'),
                'mobile' => $request->string('mobile')->toString(),
                'role' => $role,
                'pin_hash' => $pinHash,
                'password' => $pinHash,
                'is_activated' => $request->has('is_activated') ? (bool) $request->boolean('is_activated') : true,
                'permissions' => $permissions,
            ]);

            $this->syncOperatorAccount($user);
            return $user;
        });

        return response()->json([
            'status' => 'success',
            'message' => 'User account created successfully.',
            'user' => $this->serializeUser($user),
        ], 201);
    }

    public function update(Request $request, int $id): JsonResponse
    {
        $superAdmin = $this->getSuperAdmin($request);
        if (!$superAdmin) {
            return $this->forbidden();
        }

        $user = User::find($id);
        if (!$user || $user->isSuperAdmin()) {
            return response()->json(['status' => 'error', 'message' => 'User not found.'], 404);
        }

        $validator = Validator::make($request->all(), [
            'name' => ['sometimes', 'string', 'max:255'],
            'mobile' => ['sometimes', 'string', 'max:30', 'unique:users,mobile,' . $user->id],
            'email' => ['sometimes', 'nullable', 'email', 'max:255'],
            'role' => ['sometimes', 'in:' . User::ROLE_ADMIN . ',' . User::ROLE_USER],
            'pin' => ['sometimes', 'digits:6'],
            'password' => ['sometimes', 'string', 'min:6', 'max:255'],
            'permissions' => ['sometimes', 'array'],
            'is_activated' => ['sometimes', 'boolean'],
        ]);

        if ($validator->fails()) {
            return response()->json([
                'status' => 'error',
                'message' => 'Validation failed.',
                'errors' => $validator->errors(),
            ], 422);
        }

        DB::transaction(function () use ($request, $user) {
            foreach (['name', 'email', 'mobile', 'role'] as $field) {
                if ($request->has($field)) {
                    $user->{$field} = $request->input($field);
                }
            }

            if ($request->has('is_activated')) {
                $user->is_activated = (bool) $request->boolean('is_activated');
            }

            $newSecret = $request->input('pin') ?? $request->input('password');
            if ($newSecret !== null && $newSecret !== '') {
                $hash = Hash::make((string) $newSecret);
                $user->pin_hash = $hash;
                $user->password = $hash;

                // Changing credentials immediately invalidates all existing
                // sessions and device bindings for the target account.
                AuthSession::where('user_id', $user->id)->update(['is_revoked' => true]);
                DeviceBinding::where('user_id', $user->id)->update(['is_active' => false]);
            }

            if ($request->has('permissions')) {
                $user->permissions = $this->sanitizePermissions($request->input('permissions', []));
            }

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
        $superAdmin = $this->getSuperAdmin($request);
        if (!$superAdmin) {
            return $this->forbidden();
        }

        $user = User::find($id);
        if (!$user || $user->isSuperAdmin()) {
            return response()->json(['status' => 'error', 'message' => 'User not found.'], 404);
        }

        DB::transaction(function () use ($user) {
            AuthSession::where('user_id', $user->id)->delete();
            DeviceBinding::where('user_id', $user->id)->delete();
            DB::table('operator_accounts')->where('user_id', $user->id)->delete();
            $user->delete();
        });

        return response()->json(['status' => 'success', 'message' => 'User deleted successfully.']);
    }

    private function getSuperAdmin(Request $request): ?User
    {
        $user = $request->user();
        if (!$user) {
            $token = $request->bearerToken() ?? $request->header('X-SAFA-ACCESS-TOKEN') ?? $request->input('access_token');
            if ($token) {
                $payload = AuthJWTController::verifyJwt($token);
                if ($payload && isset($payload['user_id'])) {
                    $user = User::find($payload['user_id']);
                }
            }
        }

        return $user && $user->isSuperAdmin() ? $user : null;
    }

    private function isSuperAdmin(Request $request): bool
    {
        return $this->getSuperAdmin($request) !== null;
    }

    private function sanitizePermissions(mixed $permissions): array
    {
        if (!is_array($permissions)) {
            return User::defaultPermissions(false);
        }

        $allowed = User::defaultPermissions(false);
        foreach ($allowed as $key => $default) {
            if (array_key_exists($key, $permissions)) {
                $allowed[$key] = (bool) $permissions[$key];
            }
        }

        return $allowed;
    }

    private function syncOperatorAccount(User $user): void
    {
        if (!DB::getSchemaBuilder()->hasTable('operator_accounts')) {
            return;
        }

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
            'role' => $user->role,
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
            'message' => 'Forbidden. SuperAdmin access required.',
        ], 403);
    }
}
