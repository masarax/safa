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

/**
 * Compatibility endpoint for pre-cursor clients. It intentionally refuses
 * large accounts instead of allocating an unbounded full snapshot in memory.
 */
class LegacySyncDownController extends Controller
{
    use AuthorizeAccountContext;

    private const MAX_ROWS_PER_ENTITY = 500;

    public function __invoke(Request $request)
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];
        $accountId = (int) $context['account_id'];
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
        foreach ($collections as $key => $model) {
            $permission = BusinessPermissions::readPermissionForEntity($key);
            if ($permission !== null && empty($permissions[$permission])) {
                $data[$key] = [];
                continue;
            }

            $rows = $model::withTrashed()
                ->where('account_id', $accountId)
                ->orderBy('id')
                ->limit(self::MAX_ROWS_PER_ENTITY + 1)
                ->get();

            if ($rows->count() > self::MAX_ROWS_PER_ENTITY) {
                return response()->json([
                    'status' => 'upgrade_required',
                    'message' => 'This account is too large for the deprecated full-snapshot sync endpoint. Update the SAFA app to use cursor sync.',
                    'max_rows_per_entity' => self::MAX_ROWS_PER_ENTITY,
                ], 426)->withHeaders($this->deprecationHeaders());
            }

            $data[$key] = $rows;
        }

        return response()->json(array_merge([
            'status' => 'success',
            'account_id' => $accountId,
            'server_time' => time(),
            'permissions' => $permissions,
            'user_permissions' => $permissions,
            'deprecated' => true,
        ], $data))->withHeaders($this->deprecationHeaders());
    }

    private function deprecationHeaders(): array
    {
        return [
            'Deprecation' => 'true',
            'Sunset' => 'Thu, 31 Dec 2026 23:59:59 GMT',
            'Link' => '</api/v1/sync/changes>; rel="successor-version"',
        ];
    }
}
