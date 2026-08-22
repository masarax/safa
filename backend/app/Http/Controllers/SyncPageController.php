<?php

namespace App\Http\Controllers;

use App\Models\Customer;
use App\Models\ExpenseIncome;
use App\Models\Supplier;
use App\Models\SupplierDeposit;
use App\Models\Transaction;
use App\Models\WalletBatch;
use App\Models\WalletLedger;
use App\Support\BusinessPermissions;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

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
        $requestedSnapshotCursor = max(0, (int) $request->query('snapshot_cursor', 0));
        $snapshotCursor = $requestedSnapshotCursor > 0
            ? $requestedSnapshotCursor
            : (int) (DB::table('sync_changes')->max('id') ?? 0);
        $user = $context['user'] ?? $request->user();
        $permissions = BusinessPermissions::effective($user, $accountId);

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
            $requiredPermission = BusinessPermissions::readPermissionForEntity($key);
            if ($requiredPermission !== null && empty($permissions[$requiredPermission])) {
                $data[$key] = [];
                $meta[$key] = [
                    'current_page' => $page,
                    'per_page' => $perPage,
                    'last_page' => 1,
                    'has_more' => false,
                    'total' => 0,
                ];
                continue;
            }

            // Rows are ordered by immutable primary key and soft deletes remain in
            // the result. Concurrent updates therefore cannot reorder a bootstrap.
            // Writes after snapshot_cursor may appear in this baseline, but will be
            // repeated by the delta journal and are reconciled by sync_version.
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

        return response()->json(array_merge([
            'status' => 'success',
            'account_id' => $accountId,
            'server_time' => time(),
            'page' => $page,
            'per_page' => $perPage,
            'has_more' => $hasMore,
            'snapshot_cursor' => $snapshotCursor,
            'meta' => $meta,
            'permissions' => $permissions,
            'user_permissions' => $permissions,
        ], $data));
    }
}
