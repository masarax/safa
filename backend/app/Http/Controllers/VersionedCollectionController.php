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
use Illuminate\Support\Facades\Validator;

class VersionedCollectionController extends Controller
{
    use AuthorizeAccountContext;

    private const DEFAULT_PAGE_SIZE = 100;
    private const MAX_PAGE_SIZE = 250;

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

        $validator = Validator::make($request->query(), [
            'page' => 'sometimes|integer|min:1|max:1000000',
            'per_page' => 'sometimes|integer|min:1|max:' . self::MAX_PAGE_SIZE,
        ]);
        if ($validator->fails()) {
            return response()->json([
                'status' => 'error',
                'message' => 'Invalid pagination parameters.',
                'errors' => $validator->errors(),
            ], 422);
        }

        [$model, $key] = $map[$resource];
        $page = (int) $request->query('page', 1);
        $perPage = (int) $request->query('per_page', self::DEFAULT_PAGE_SIZE);

        // Normal REST collection reads expose active records only. Tombstones are
        // reserved for sync/reconciliation endpoints and never leak into UI lists.
        $paginator = $model::query()
            ->where('account_id', (int) $context['account_id'])
            ->whereNull('deleted_at')
            ->orderBy('id', 'asc')
            ->paginate($perPage, ['*'], 'page', $page);

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
