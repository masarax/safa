<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

class RequireBusinessPermission
{
    public function handle(Request $request, Closure $next): Response
    {
        $user = $request->user() ?? $request->attributes->get('user');
        if (!$user || !$user->is_activated) {
            return response()->json(['status' => 'error', 'message' => 'Unauthorized.'], 401);
        }

        if ($user->role === 'superadmin') {
            return $next($request);
        }

        $permissions = $user->getFormattedPermissions();
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
