<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

class RequireGraphQLPermission
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

        $query = (string) $request->input('query', '');
        $permissions = $user->getFormattedPermissions();
        $isMutation = (bool) preg_match('/^\s*mutation\b/i', $query);

        $required = [];
        $required[] = [
            'patterns' => ['transactions', 'transaction', 'syncState'],
            'permission' => $isMutation ? 'can_add_transactions' : 'can_view_transactions',
        ];
        $required[] = [
            'patterns' => ['customers', 'customer'],
            'permission' => $isMutation ? 'can_add_customers' : 'can_view_customers',
        ];
        $required[] = [
            'patterns' => ['suppliers', 'supplier'],
            'permission' => $isMutation ? 'can_add_suppliers' : 'can_view_suppliers',
        ];
        $required[] = [
            'patterns' => ['walletBatches', 'wallet_batches', 'walletLedgers', 'wallet_ledgers', 'supplierDeposits', 'supplier_deposits'],
            'permission' => 'can_manage_wallet',
        ];
        $required[] = [
            'patterns' => ['expensesIncomes', 'expenses_incomes'],
            'permission' => 'can_manage_expenses',
        ];

        foreach ($required as $rule) {
            foreach ($rule['patterns'] as $pattern) {
                if (preg_match('/\b' . preg_quote($pattern, '/') . '\b/i', $query)) {
                    if (empty($permissions[$rule['permission']])) {
                        return response()->json([
                            'status' => 'error',
                            'message' => 'Forbidden: you do not have permission for this GraphQL operation.',
                            'permission' => $rule['permission'],
                        ], 403);
                    }
                    break;
                }
            }
        }

        return $next($request);
    }
}
