<?php

namespace App\Http\Controllers;

use App\Models\Customer;
use App\Models\ExpenseIncome;
use App\Models\Supplier;
use App\Models\SupplierDeposit;
use App\Models\Transaction;
use App\Models\WalletBatch;
use App\Models\WalletLedger;
use Illuminate\Http\Request;

class SyncPageController extends Controller
{
    use AuthorizeAccountContext;

    public function __invoke(Request $request)
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];
        $accountId = (int) $context['account_id'];
        $page = max(1, (int) $request->query('page', 1));
        $perPage = min(250, max(1, (int) $request->query('per_page', 100)));

        $collections = [
            'transactions' => Transaction::class,
            'customers' => Customer::class,
            'suppliers' => Supplier::class,
            'wallet_batches' => WalletBatch::class,
            'wallet_ledgers' => WalletLedger::class,
            'supplier_deposits' => SupplierDeposit::class,
            'expenses_incomes' => ExpenseIncome::class,
        ];
        $data = [];
        $meta = [];
        $hasMore = false;

        foreach ($collections as $key => $model) {
            $paginator = $model::withTrashed()
                ->where('account_id', $accountId)
                ->orderBy('id')
                ->paginate($perPage, ['*'], 'page', $page);
            $data[$key] = $paginator->items();
            $meta[$key] = [
                'current_page' => $paginator->currentPage(),
                'per_page' => $paginator->perPage(),
                'last_page' => $paginator->lastPage(),
                'has_more' => $paginator->hasMorePages(),
                'total' => $paginator->total(),
            ];
            $hasMore = $hasMore || $paginator->hasMorePages();
        }

        $user = $context['user'] ?? $request->user();
        $permissions = $user ? $user->getFormattedPermissions() : \App\Models\User::defaultPermissions(true);
        return response()->json(array_merge([
            'status' => 'success',
            'account_id' => $accountId,
            'server_time' => time(),
            'page' => $page,
            'per_page' => $perPage,
            'has_more' => $hasMore,
            'meta' => $meta,
            'permissions' => $permissions,
            'user_permissions' => $permissions,
        ], $data));
    }
}
