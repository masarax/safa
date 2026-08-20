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
use App\Support\BusinessPermissions;
use App\Support\MoneyDecimal;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Log;
use Illuminate\Support\Facades\Validator;

class SyncController extends Controller
{
    use AuthorizeAccountContext;

    public function __construct(private readonly SyncReconciliationService $reconciliation) {}

    private function decimal(mixed $value, int $scale, int $integerDigits = 13): string
    {
        if ($value === null || $value === '') $value = 0;
        return MoneyDecimal::unsigned($value, $scale, $integerDigits);
    }

    private function positiveDecimal(mixed $value, int $scale, int $integerDigits = 13): string
    {
        return $this->decimal($value, $scale, $integerDigits);
    }

    private function compareDecimal(string $a, string $b, int $scale): int
    {
        return MoneyDecimal::compare($a, $b, $scale, 13, false);
    }

    private function operation(array $row): string
    {
        $sync = is_array($row['_sync'] ?? null) ? $row['_sync'] : [];
        $operation = strtoupper((string) ($sync['operation'] ?? $row['operation'] ?? ''));
        if ($operation === '') {
            return (!empty($row['deleted_at']) || !empty($row['is_deleted'])) ? 'DELETE' : 'UPSERT';
        }

        return match ($operation) {
            'CREATE', 'UPDATE', 'DELETE', 'RESTORE' => $operation,
            default => $operation,
        };
    }

    private function recordExists(string $model, int $accountId, array $row): bool
    {
        $localId = (int) ($row['local_id'] ?? 0);
        return $localId > 0
            && $model::withTrashed()->where('account_id', $accountId)->where('local_id', $localId)->exists();
    }

    public function syncUp(Request $request)
    {
        try {
            $context = $this->resolveAuthorizedAccountContext($request);
            if (isset($context['error'])) return $context['error'];
            $accountId = (int) $context['account_id'];
            $user = $context['user'] ?? $request->user();
            $permissions = BusinessPermissions::effective($user, $accountId);

            $validator = Validator::make($request->all(), [
                'transactions' => 'nullable|array|max:500',
                'customers' => 'nullable|array|max:500',
                'suppliers' => 'nullable|array|max:500',
                'wallet_batches' => 'nullable|array|max:500',
                'supplier_deposits' => 'nullable|array|max:500',
                'expenses_incomes' => 'nullable|array|max:500',
                'wallet_ledgers' => 'nullable|array|max:500',
            ]);
            if ($validator->fails()) {
                return response()->json(['message' => 'Invalid data format.', 'errors' => $validator->errors()], 422);
            }

            $accepted = [
                'customers' => [],
                'suppliers' => [],
                'wallet_ledgers' => [],
                'supplier_deposits' => [],
                'wallet_batches' => [],
                'transactions' => [],
                'expenses_incomes' => [],
            ];
            $rejected = [];
            $conflicts = [];

            $entities = [
                'customers' => [
                    'model' => Customer::class,
                    'rows' => $request->input('customers', []),
                    'validate' => fn (array $r) => empty(trim((string) ($r['name'] ?? ''))) ? 'Missing required field: name' : null,
                    'attributes' => fn (array $r) => [
                        'name' => substr(trim((string) $r['name']), 0, 255),
                        'phone' => substr(trim((string) ($r['phone'] ?? '')), 0, 50),
                        'avatar_color' => substr((string) ($r['avatar_color'] ?? ''), 0, 20) ?: null,
                        'avatar_emoji' => substr((string) ($r['avatar_emoji'] ?? ''), 0, 16) ?: null,
                    ],
                ],
                'suppliers' => [
                    'model' => Supplier::class,
                    'rows' => $request->input('suppliers', []),
                    'validate' => fn (array $r) => empty(trim((string) ($r['name'] ?? ''))) ? 'Missing required field: name' : null,
                    'attributes' => fn (array $r) => [
                        'name' => substr(trim((string) $r['name']), 0, 255),
                        'phone' => substr(trim((string) ($r['phone'] ?? '')), 0, 50),
                        'avatar_color' => substr((string) ($r['avatar_color'] ?? ''), 0, 20) ?: null,
                        'avatar_emoji' => substr((string) ($r['avatar_emoji'] ?? ''), 0, 16) ?: null,
                    ],
                ],
                'wallet_ledgers' => [
                    'model' => WalletLedger::class,
                    'rows' => $request->input('wallet_ledgers', []),
                    'validate' => fn (array $r) => empty(trim((string) ($r['name'] ?? ''))) ? 'Missing required field: name' : null,
                    'attributes' => fn (array $r) => ['name' => substr(trim((string) ($r['name'] ?? '')), 0, 255)],
                ],
                'supplier_deposits' => [
                    'model' => SupplierDeposit::class,
                    'rows' => $request->input('supplier_deposits', []),
                    'attributes' => function (array $r, int $accountId) {
                        $supplierLocal = (int) ($r['supplier_id'] ?? 0);
                        $supplierId = $supplierLocal > 0 ? Supplier::where('account_id', $accountId)->where('local_id', $supplierLocal)->value('id') : null;
                        if ($supplierLocal > 0 && !$supplierId) throw new \RuntimeException('Supplier dependency has not been reconciled yet.');
                        return [
                            'supplier_id' => $supplierId,
                            'amount_sar' => $this->positiveDecimal($r['amount_sar'] ?? 0, 2),
                            'rate' => $this->positiveDecimal($r['rate'] ?? 0, 4, 6),
                            'amount_bdt' => $this->positiveDecimal($r['amount_bdt'] ?? 0, 2),
                            'paid_bdt' => $this->positiveDecimal($r['paid_bdt'] ?? 0, 2),
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
                        $ledgerId = $ledgerLocal > 0 ? WalletLedger::where('account_id', $accountId)->where('local_id', $ledgerLocal)->value('id') : null;
                        $supplierId = $supplierLocal > 0 ? Supplier::where('account_id', $accountId)->where('local_id', $supplierLocal)->value('id') : null;
                        $depositId = $depositLocal > 0 ? SupplierDeposit::where('account_id', $accountId)->where('local_id', $depositLocal)->value('id') : null;
                        if (($ledgerLocal > 0 && !$ledgerId) || ($supplierLocal > 0 && !$supplierId) || ($depositLocal > 0 && !$depositId)) {
                            throw new \RuntimeException('Wallet batch dependency has not been reconciled yet.');
                        }
                        $initial = $this->positiveDecimal($r['initial_bdt'] ?? 0, 2);
                        $remaining = $this->positiveDecimal($r['remaining_bdt'] ?? 0, 2);
                        if ($this->compareDecimal($remaining, $initial, 2) > 0) throw new \InvalidArgumentException('Remaining wallet balance cannot exceed initial balance.');
                        return [
                            'ledger_id' => $ledgerId,
                            'rate' => $this->positiveDecimal($r['rate'] ?? 0, 4, 6),
                            'initial_bdt' => $initial,
                            'remaining_bdt' => $remaining,
                            'supplier_id' => $supplierId,
                            'supplier_deposit_id' => $depositId,
                            'notes' => $r['notes'] ?? null,
                        ];
                    },
                ],
                'transactions' => [
                    'model' => Transaction::class,
                    'rows' => $request->input('transactions', []),
                    'validate' => fn (array $r) => array_key_exists('type', $r) && trim((string) $r['type']) === '' ? 'Missing required field: type' : null,
                    'attributes' => function (array $r, int $accountId) {
                        $customerLocal = (int) ($r['customer_id'] ?? 0);
                        $supplierLocal = (int) ($r['supplier_id'] ?? 0);
                        $batchLocal = (int) ($r['wallet_batch_id'] ?? 0);
                        $customerId = $customerLocal > 0 ? Customer::where('account_id', $accountId)->where('local_id', $customerLocal)->value('id') : null;
                        $supplierId = $supplierLocal > 0 ? Supplier::where('account_id', $accountId)->where('local_id', $supplierLocal)->value('id') : null;
                        $batchId = $batchLocal > 0 ? WalletBatch::where('account_id', $accountId)->where('local_id', $batchLocal)->value('id') : null;
                        if (($customerLocal > 0 && !$customerId) || ($supplierLocal > 0 && !$supplierId) || ($batchLocal > 0 && !$batchId)) {
                            throw new \RuntimeException('Transaction dependency has not been reconciled yet.');
                        }
                        $amountSar = $this->positiveDecimal($r['amount_sar'] ?? $r['amount'] ?? 0, 2);
                        $amountBdt = $this->positiveDecimal($r['amount_bdt'] ?? 0, 2);
                        return [
                            'type' => substr((string) ($r['type'] ?? $r['status'] ?? 'Pending'), 0, 20),
                            'amount' => $this->positiveDecimal($r['amount'] ?? $amountSar, 2),
                            'customer_id' => $customerId,
                            'supplier_id' => $supplierId,
                            'amount_sar' => $amountSar,
                            'customer_rate' => $this->positiveDecimal($r['customer_rate'] ?? 0, 4, 6),
                            'supplier_rate' => $this->positiveDecimal($r['supplier_rate'] ?? 0, 4, 6),
                            'amount_bdt' => $amountBdt,
                            'sar_collected' => MoneyDecimal::signed($r['sar_collected'] ?? $amountSar, 2, 13),
                            'bdt_disbursed' => MoneyDecimal::unsigned($r['bdt_disbursed'] ?? $amountBdt, 2, 13),
                            'receiver_name' => substr((string) ($r['receiver_name'] ?? ''), 0, 255),
                            'receiver_phone' => substr((string) ($r['receiver_phone'] ?? ''), 0, 50),
                            'receiver_account_type' => substr((string) ($r['receiver_account_type'] ?? ''), 0, 50),
                            'receiver_account_no' => substr((string) ($r['receiver_account_no'] ?? ''), 0, 100),
                            'wallet_batch_id' => $batchId,
                            'notes' => $r['notes'] ?? null,
                            'hash' => $r['hash'] ?? null,
                        ];
                    },
                ],
                'expenses_incomes' => [
                    'model' => ExpenseIncome::class,
                    'rows' => $request->input('expenses_incomes', []),
                    'validate' => fn (array $r) => empty(trim((string) ($r['title'] ?? ''))) ? 'Missing required field: title' : null,
                    'attributes' => fn (array $r) => [
                        'title' => substr(trim((string) ($r['title'] ?? 'General')), 0, 255),
                        'amount' => $this->positiveDecimal($r['amount'] ?? 0, 2),
                        'currency' => substr(strtoupper(trim((string) ($r['currency'] ?? 'BDT'))), 0, 10),
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

                    $operation = $this->operation($row);
                    $permission = BusinessPermissions::mutationPermissionForEntity(
                        $entity,
                        $operation,
                        $this->recordExists($config['model'], $accountId, $row),
                    );
                    if ($permission !== null && empty($permissions[$permission])) {
                        $rejected[] = [
                            'entity' => $entity,
                            'local_id' => (int) ($row['local_id'] ?? 0),
                            'reason' => 'The authenticated user is not permitted to synchronize this entity operation.',
                            'code' => 'FORBIDDEN',
                            'permission' => $permission,
                        ];
                        continue;
                    }

                    try {
                        $result = $this->reconciliation->apply(
                            $accountId,
                            $entity,
                            $config['model'],
                            $row,
                            $config['attributes'],
                            $config['validate'] ?? null,
                        );
                    } catch (\Throwable $e) {
                        Log::warning('Sync record rejected.', [
                            'entity' => $entity,
                            'local_id' => (int) ($row['local_id'] ?? 0),
                            'exception' => get_class($e),
                        ]);
                        $dependency = $e instanceof \RuntimeException;
                        $result = [
                            'status' => 'rejected',
                            'rejected' => [
                                'entity' => $entity,
                                'local_id' => (int) ($row['local_id'] ?? 0),
                                'reason' => $dependency ? 'A referenced record has not been synchronized yet.' : 'The record contains invalid or unsupported values.',
                                'code' => $dependency ? 'DEPENDENCY' : 'VALIDATION',
                            ],
                        ];
                    }

                    if ($result['status'] === 'accepted') {
                        $accepted[$entity][] = $result['accepted'];
                    } elseif ($result['status'] === 'conflict') {
                        $conflicts[] = $result['conflict'];
                    } else {
                        $rejected[] = $result['rejected'];
                    }
                }
            }

            return response()->json([
                'status' => empty($conflicts) ? 'success' : 'conflict',
                'server_time' => time(),
                'accepted' => $accepted,
                'rejected' => $rejected,
                'conflicts' => $conflicts,
                'permissions' => $permissions,
            ]);
        } catch (\Throwable $e) {
            Log::error('SyncUp failed.', ['exception' => get_class($e)]);
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
                $data[$key] = $permission !== null && empty($permissions[$permission])
                    ? []
                    : $model::withTrashed()->where('account_id', $accountId)->get();
            }

            return response()->json(array_merge([
                'status' => 'success',
                'account_id' => $accountId,
                'server_time' => time(),
                'permissions' => $permissions,
                'user_permissions' => $permissions,
            ], $data));
        } catch (\Throwable $e) {
            Log::error('SyncDown failed.', ['exception' => get_class($e)]);
            return response()->json(['message' => 'Failed to fetch sync data.'], 500);
        }
    }
}
