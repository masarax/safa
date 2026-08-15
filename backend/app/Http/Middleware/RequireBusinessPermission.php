<?php

namespace App\Http\Middleware;

use App\Models\UserAccountShare;
use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

class RequireBusinessPermission
{
    public function handle(Request $request, Closure $next): Response
    {
        // API-key-only requests are permitted only in the isolated test
        // environment. Production always requires an authenticated user.
        if (app()->environment('testing') && $request->header('X-SAFA-API-KEY') && !$request->bearerToken()) {
            return $next($request);
        }

        $user = $request->user() ?? $request->attributes->get('user');
        if (!$user || !$user->is_activated) {
            return response()->json(['status' => 'error', 'message' => 'Unauthorized.'], 401);
        }

        $permissions = $user->getFormattedPermissions();
        $accountId = (int) ($request->attributes->get('active_account_id')
            ?: $request->header('X-SAFA-ACCOUNT-ID')
            ?: $request->input('account_id', 0));

        if ($accountId > 0) {
            $share = UserAccountShare::query()
                ->where('shared_with_user_id', $user->id)
                ->where('account_id', $accountId)
                ->where('owner_user_id', '!=', $user->id)
                ->first();

            if ($share && is_array($share->permissions_override)) {
                // A share may narrow access inside the user's role preset, but it
                // can never elevate a Normal/Business user into a hidden module.
                foreach ($share->permissions_override as $key => $allowed) {
                    if (array_key_exists($key, $permissions)) {
                        $permissions[$key] = (bool) $permissions[$key] && (bool) $allowed;
                    }
                }
            }
        }

        $method = strtoupper($request->method());
        $path = trim($request->path(), '/');
        $permission = null;

        if (preg_match('#/customers(?:/[^/]+)?$#', $path)) {
            $permission = match ($method) {
                'GET' => 'can_view_customers',
                'POST' => 'can_add_customers',
                'PUT', 'PATCH' => 'can_edit_customers',
                'DELETE' => 'can_delete_customers',
                default => null,
            };
        } elseif (preg_match('#/suppliers(?:/[^/]+)?$#', $path)) {
            $permission = match ($method) {
                'GET' => 'can_view_suppliers',
                'POST' => 'can_add_suppliers',
                'PUT', 'PATCH' => 'can_edit_suppliers',
                'DELETE' => 'can_delete_suppliers',
                default => null,
            };
        } elseif (preg_match('#/transactions(?:/[^/]+)?$#', $path)) {
            $permission = match ($method) {
                'GET' => 'can_view_transactions',
                'POST' => 'can_add_transactions',
                'PUT', 'PATCH' => 'can_edit_transactions',
                'DELETE' => 'can_delete_transactions',
                default => null,
            };
        } elseif (preg_match('#/(wallet-ledgers|wallet-batches|supplier-deposits)(?:/[^/]+)?$#', $path)) {
            $permission = 'can_manage_wallet';
        } elseif (preg_match('#/expenses-incomes(?:/[^/]+)?$#', $path)) {
            $permission = 'can_manage_expenses';
        }

        if ($permission !== null && empty($permissions[$permission])) {
            return response()->json([
                'status' => 'error',
                'message' => 'Forbidden: you do not have permission to perform this action.',
                'permission' => $permission,
            ], 403);
        }

        return $next($request);
    }
}
