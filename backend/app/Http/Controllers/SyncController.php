<?php

namespace App\Http\Controllers;

use App\Models\Customer;
use App\Models\ExpenseIncome;
use App\Models\Supplier;
use App\Models\SupplierDeposit;
use App\Models\Transaction;
use App\Models\WalletBatch;
use App\Models\WalletLedger;
use App\Services\SyncReconciliationService;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Validator;

class SyncController extends Controller
{
    use AuthorizeAccountContext;

    public function __construct(private readonly SyncReconciliationService $reconciliation)
    {
    }

    public function syncUp(Request $request)
    {
        try {
            $context = $this->resolveAuthorizedAccountContext($request);
            if (isset($context['error'])) return $context['error'];
            $accountId = (int) $context['account_id'];

            $validator = Validator::make($request->all(), [
                'transactions'      => 'nullable|array|max:500',
                'customers'         => 'nullable|array|max:500',
                'suppliers'         => 'nullable|array|max:500',
                'wallet_batches'    => 'nullable|array|max:500',
                'supplier_deposits' => 'nullable|array|max:500',
                'expenses_incomes'  => 'nullable|array|max:500',
                'wallet_ledgers'    => 'nullable|array|max:500',
            ]);
            if ($validator->fails()) {
                return response()->json(['message' => 'Invalid data format.', 'errors' => $validator->errors()], 422);
            }

            $accepted = [
                'customers' => [], 'suppliers' => [], 'wallet_ledgers' => [],
                'supplier_deposits' => [], 'wallet_batches' => [],
                'transactions' => [], 'expenses_incomes' => [],
            ];
            $rejected = [];
            $conflicts = [];

            $entities = [
                'customers' => [
                    'model' => Customer::class,
                    'rows' => $request->input('customers', []),
                    'validate' => fn (array $r) => empty($r['name']) ? 'Missing required field: name' : null,
                    'attributes' => fn (array $r) => [
                        'name' => substr((string) $r['name'], 0, 255),
                        'phone' => substr((string) ($r['phone'] ?? ''), 0, 50),
                    ],
                ],
                'suppliers' => [
                    'model' => Supplier::class,
                    'rows' => $request->input('suppliers', []),
                    'validate' => fn (array $r) => empty($r['name']) ? 'Missing required field: name' : null,
                    'attributes' => fn (array $r) => [
                        'name' => substr((string) $r['name'], 0, 255),
                        'phone' => substr((string) ($r['phone'] ?? ''), 0, 50),
                    ],
                ],
                'wallet_ledgers' => [
                    'model' => WalletLedger::class,
                    'rows' => $request->input('wallet_ledgers', []),
                    'attributes' => fn (array $r) => [
                        'name' => substr((string) ($r['name'] ?? ''), 0, 255),
                    ],
                ],
                'supplier_deposits' => [
                    'model' => SupplierDeposit::class,
                    'rows' => $request->input('supplier_deposits', []),
                    'attributes' => function (array $r, int $accountId) {
                        $supplierLocalId = (int) ($r['supplier_id'] ?? 0);
                        $supplierId = $supplierLocalId > 0
                            ? Supplier::where('account_id', $accountId)->where('local_id', $supplierLocalId)->value('id')
                            : 0;
                        if ($supplierLocalId > 0 && !$supplierId) return new \RuntimeException('Supplier dependency has not been reconciled yet');
                        return [
                            'supplier_id' => (int) ($supplierId ?? 0),
                            'amount_sar' => (float) ($r['amount_sar'] ?? 0),
                            'rate' => (float) ($r['rate'] ?? 0),
                            'amount_bdt' => (float) ($r['amount_bdt'] ?? 0),
                            'paid_bdt' => (float) ($r['paid_bdt'] ?? 0),
                            'transaction_type' => substr((string) ($r['transaction_type'] ?? 'SAR_GIVEN'), 0, 50),
                            'notes' => $r['notes'] ?? null,
                        ];
                    },
                ],
                'wallet_batches' => [
                    'model' => WalletBatch::class,
                    'rows' => $request->input('wallet_batches', []),
                    'attributes' => function (array $r, int $accountId) {
                        $ledgerLocal = (int) ($r['ledger_id'] ?? 0);
                        $supplierLocal = (int) ($r['supplier_id'] ?? 0);
                        $depositLocal = (int) ($r['supplier_deposit_id'] ?? 0);
                        $ledgerId = $ledgerLocal > 0 ? WalletLedger::where('account_id', $accountId)->where('local_id', $ledgerLocal)->value('id') : 0;
                        $supplierId = $supplierLocal > 0 ? Supplier::where('account_id', $accountId)->where('local_id', $supplierLocal)->value('id') : 0;
                        $depositId = $depositLocal > 0 ? SupplierDeposit::where('account_id', $accountId)->where('local_id', $depositLocal)->value('id') : 0;
                        if (($ledgerLocal > 0 && !$ledgerId) || ($supplierLocal > 0 && !$supplierId) || ($depositLocal > 0 && !$depositId)) return new \RuntimeException('Wallet batch dependency has not been reconciled yet');
                        return [
                            'ledger_id' => (int) ($ledgerId ?? 0),
                            'rate' => (float) ($r['rate'] ?? 0),
                            'initial_bdt' => (float) ($r['initial_bdt'] ?? 0),
                            'remaining_bdt' => (float) ($r['remaining_bdt'] ?? 0),
                            'supplier_id' => (int) ($supplierId ?? 0),
                            'supplier_deposit_id' => (int) ($depositId ?? 0),
                            'notes' => $r['notes'] ?? null,
                        ];
                    },
                ],
                'transactions' => [
                    'model' => Transaction::class,
                    'rows' => $request->input('transactions', []),
                    'attributes' => function (array $r, int $accountId) {
                        $customerLocal = (int) ($r['customer_id'] ?? 0);
                        $supplierLocal = (int) ($r['supplier_id'] ?? 0);
                        $batchLocal = (int) ($r['wallet_batch_id'] ?? 0);
                        $customerId = $customerLocal > 0 ? Customer::where('account_id', $accountId)->where('local_id', $customerLocal)->value('id') : 0;
                        $supplierId = $supplierLocal > 0 ? Supplier::where('account_id', $accountId)->where('local_id', $supplierLocal)->value('id') : 0;
                        $batchId = $batchLocal > 0 ? WalletBatch::where('account_id', $accountId)->where('local_id', $batchLocal)->value('id') : 0;
                        if (($customerLocal > 0 && !$customerId) || ($supplierLocal > 0 && !$supplierId) || ($batchLocal > 0 && !$batchId)) return new \RuntimeException('Transaction dependency has not been reconciled yet');
                        return [
                            'type' => substr((string) ($r['type'] ?? $r['status'] ?? 'Pending'), 0, 20),
                            'amount' => (float) ($r['amount'] ?? $r['amount_sar'] ?? 0),
                            'customer_id' => (int) ($customerId ?? 0),
                            'supplier_id' => (int) ($supplierId ?? 0),
                            'amount_sar' => (float) ($r['amount_sar'] ?? $r['amount'] ?? 0),
                            'customer_rate' => (float) ($r['customer_rate'] ?? 0),
                            'supplier_rate' => (float) ($r['supplier_rate'] ?? 0),
                            'amount_bdt' => (float) ($r['amount_bdt'] ?? 0),
                            'receiver_name' => substr((string) ($r['receiver_name'] ?? ''), 0, 255),
                            'receiver_phone' => substr((string) ($r['receiver_phone'] ?? ''), 0, 50),
                            'receiver_account_type' => substr((string) ($r['receiver_account_type'] ?? ''), 0, 50),
                            'receiver_account_no' => substr((string) ($r['receiver_account_no'] ?? ''), 0, 100),
                            'wallet_batch_id' => (int) ($batchId ?? 0),
                            'notes' => $r['notes'] ?? null,
                            'hash' => $r['hash'] ?? null,
                        ];
                    },
                ],
                'expenses_incomes' => [
                    'model' => ExpenseIncome::class,
                    'rows' => $request->input('expenses_incomes', []),
                    'attributes' => fn (array $r) => [
                        'title' => substr((string) ($r['title'] ?? 'General'), 0, 255),
                        'amount' => (float) ($r['amount'] ?? 0),
                        'currency' => substr((string) ($r['currency'] ?? 'BDT'), 0, 10),
                        'is_expense' => (bool) ($r['is_expense'] ?? true),
                        'category' => substr((string) ($r['category'] ?? 'General'), 0, 50),
                    ],
                ],
            ];

            foreach ($entities as $entity => $config) {
                foreach ($config['rows'] as $row) {
                    if (!is_array($row)) {
                        $rejected[] = ['entity' => $entity, 'local_id' => 0, 'reason' => 'Invalid record', 'code' => 'VALIDATION'];
                        continue;
                    }
                    $result = $this->reconciliation->apply($accountId, $entity, $config['model'], $row, $config['attributes'], $config['validate'] ?? null);
                    if ($result['status'] === 'accepted') $accepted[$entity][] = $result['accepted'];
                    elseif ($result['status'] === 'conflict') $conflicts[] = $result['conflict'];
                    else $rejected[] = $result['rejected'];
                }
            }

            $user = $context['user'] ?? $request->user();
            $permissions = $user ? $user->getFormattedPermissions() : \App\Models\User::defaultPermissions(true);

            return response()->json([
                'status' => empty($conflicts) ? 'success' : 'conflict',
                'server_time' => time(),
                'accepted' => $accepted,
                'rejected' => $rejected,
                'conflicts' => $conflicts,
                'permissions' => $permissions,
            ], 200);
        } catch (\Throwable $e) {
            Log::error('SyncUp failed.', ['message' => $e->getMessage(), 'exception' => get_class($e)]);
            return response()->json(['message' => 'Sync failed.'], 500);
        }
    }

    public function syncDown(Request $request)
    {
        try {
            $context = $this->resolveAuthorizedAccountContext($request);
            if (isset($context['error'])) return $context['error'];
            $accountId = (int) $context['account_id'];
            $user = $context['user'] ?? $request->user();
            $permissions = $user ? $user->getFormattedPermissions() : \App\Models\User::defaultPermissions(true);

            return response()->json([
                'status' => 'success',
                'account_id' => $accountId,
                'server_time' => time(),
                'transactions' => Transaction::withTrashed()->where('account_id', $accountId)->get(),
                'customers' => Customer::withTrashed()->where('account_id', $accountId)->get(),
                'suppliers' => Supplier::withTrashed()->where('account_id', $accountId)->get(),
                'wallet_batches' => WalletBatch::withTrashed()->where('account_id', $accountId)->get(),
                'wallet_ledgers' => WalletLedger::withTrashed()->where('account_id', $accountId)->get(),
                'supplier_deposits' => SupplierDeposit::withTrashed()->where('account_id', $accountId)->get(),
                'expenses_incomes' => ExpenseIncome::withTrashed()->where('account_id', $accountId)->get(),
                'permissions' => $permissions,
                'user_permissions' => $permissions,
            ]);
        } catch (\Throwable $e) {
            Log::error('SyncDown failed.', ['message' => $e->getMessage(), 'exception' => get_class($e)]);
            return response()->json(['message' => 'Failed to fetch sync data.'], 500);
        }
    }
}
