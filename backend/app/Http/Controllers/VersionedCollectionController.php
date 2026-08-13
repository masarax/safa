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

class VersionedCollectionController extends Controller
{
    use AuthorizeAccountContext;

    public function __invoke(Request $request, string $resource)
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];
        $map = [
            'customers' => [Customer::class, 'customers'],
            'suppliers' => [Supplier::class, 'suppliers'],
            'transactions' => [Transaction::class, 'transactions'],
            'wallet-ledgers' => [WalletLedger::class, 'wallet_ledgers'],
            'supplier-deposits' => [SupplierDeposit::class, 'supplier_deposits'],
            'wallet-batches' => [WalletBatch::class, 'wallet_batches'],
            'expenses-incomes' => [ExpenseIncome::class, 'expenses_incomes'],
        ];
        abort_unless(isset($map[$resource]), 404);
        [$model, $key] = $map[$resource];
        $page = max(1, (int) $request->query('page', 1));
        $perPage = min(250, max(1, (int) $request->query('per_page', 100)));
        $paginator = $model::withTrashed()->where('account_id', (int) $context['account_id'])->orderBy('id')->paginate($perPage, ['*'], 'page', $page);
        return response()->json([
            'status' => 'success',
            $key => $paginator->items(),
            'pagination' => [
                'current_page' => $paginator->currentPage(),
                'per_page' => $paginator->perPage(),
                'last_page' => $paginator->lastPage(),
                'total' => $paginator->total(),
                'has_more' => $paginator->hasMorePages(),
            ],
        ]);
    }
}
