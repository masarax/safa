<?php

namespace App\Http\Controllers;

use App\Models\Customer;
use App\Models\ExpenseIncome;
use App\Models\Supplier;
use App\Models\SupplierDeposit;
use App\Models\SystemSetting;
use App\Models\Transaction;
use App\Models\UserAccountShare;
use App\Models\WalletBatch;
use App\Models\WalletLedger;
use App\Support\DecimalMath;
use App\Support\MoneyDecimal;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Validator;
use Illuminate\Validation\Rule;

/**
 * Browser operations that intentionally mirror SafaViewModel's Android domain
 * workflow. The web UI calls these atomic actions instead of trying to emulate
 * coupled ledger mutations with a sequence of generic CRUD requests.
 */
class WebMobileFlowController extends Controller
{
    use AuthorizeAccountContext;

    private const PAYMENT_METHODS = ['Cash', 'Bkash', 'Nagad', 'Rocket', 'Bank Transfer'];
    private const TRANSACTION_STATUSES = ['Pending', 'Delivered', 'Cancelled'];
    private const SUPPLIER_PURCHASE_TYPES = ['SAR_GIVEN', 'SAR_DEPOSIT'];
    private const SUPPLIER_SETTLEMENT_TYPES = ['SAR_RECEIVED', 'SAR_SETTLEMENT'];

    public function workspace(Request $request): JsonResponse
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];
        $accountId = (int) $context['account_id'];
        $permissions = $this->effectivePermissions($request, $accountId);
        $setting = SystemSetting::first();

        return response()->json([
            'status' => 'success',
            'account_id' => $accountId,
            'user' => [
                'id' => (int) $request->user()->id,
                'name' => (string) $request->user()->name,
                'mobile' => (string) ($request->user()->mobile ?? ''),
                'email' => (string) ($request->user()->email ?? ''),
                'role' => (string) $request->user()->role,
            ],
            'permissions' => $permissions,
            'settings' => [
                'app_name' => $setting?->app_name ?: 'SAFA',
                'app_logo_url' => $setting?->webLogoSource() ?: '/safa-logo.png',
                'app_version' => $setting?->app_version ?: '1.0.0',
                'local_currency' => $setting?->local_currency ?: 'BDT',
                'foreign_currency' => $setting?->foreign_currency ?: 'SAR',
                'rate_based_mode' => (bool) ($setting?->rate_based_mode ?? true),
                'supplier_rate_enabled' => (bool) ($setting?->supplier_rate_enabled ?? true),
                'wallet_rate_enabled' => (bool) ($setting?->wallet_rate_enabled ?? true),
            ],
            'customers' => !empty($permissions['can_view_customers'])
                ? Customer::query()->where('account_id', $accountId)->whereNull('deleted_at')->orderByDesc('timestamp')->orderByDesc('id')->get()
                : [],
            'suppliers' => !empty($permissions['can_view_suppliers'])
                ? Supplier::query()->where('account_id', $accountId)->whereNull('deleted_at')->orderByDesc('timestamp')->orderByDesc('id')->get()
                : [],
            'transactions' => !empty($permissions['can_view_transactions'])
                ? Transaction::query()->where('account_id', $accountId)->whereNull('deleted_at')->orderByDesc('timestamp')->orderByDesc('id')->get()
                : [],
            'supplier_deposits' => !empty($permissions['can_manage_wallet'])
                ? SupplierDeposit::query()->where('account_id', $accountId)->whereNull('deleted_at')->orderByDesc('timestamp')->orderByDesc('id')->get()
                : [],
            'wallet_ledgers' => !empty($permissions['can_manage_wallet'])
                ? WalletLedger::query()->where('account_id', $accountId)->whereNull('deleted_at')->orderByDesc('timestamp')->orderByDesc('id')->get()
                : [],
            'wallet_batches' => !empty($permissions['can_manage_wallet'])
                ? WalletBatch::query()->where('account_id', $accountId)->whereNull('deleted_at')->orderBy('timestamp')->orderBy('id')->get()
                : [],
            'expenses' => !empty($permissions['can_manage_expenses'])
                ? ExpenseIncome::query()->where('account_id', $accountId)->whereNull('deleted_at')->orderByDesc('timestamp')->orderByDesc('id')->get()
                : [],
        ]);
    }

    /** New Sale, optionally with the Android Step-2 old-due/advance adjustment. */
    public function customerSale(Request $request): JsonResponse
    {
        $context = $this->authorized($request, 'can_add_transactions');
        if (isset($context['error'])) return $context['error'];
        $accountId = (int) $context['account_id'];

        $validator = Validator::make($request->all(), [
            'customer_id' => ['required', 'integer', 'min:1'],
            'wallet_batch_id' => ['required', 'integer', 'min:1'],
            'amount_sar' => ['required', $this->decimalRule(2, 13)],
            'customer_rate' => ['required', $this->decimalRule(4, 6)],
            'sar_collected' => ['nullable', $this->decimalRule(2, 13)],
            'bdt_disbursed' => ['nullable', $this->decimalRule(2, 13)],
            'receiver_name' => ['nullable', 'string', 'max:255'],
            'receiver_phone' => ['nullable', 'string', 'max:50'],
            'receiver_account_type' => ['required', Rule::in(self::PAYMENT_METHODS)],
            'receiver_account_no' => ['nullable', 'string', 'max:100'],
            'notes' => ['nullable', 'string', 'max:5000'],
            'timestamp' => ['nullable', 'integer', 'min:1'],
            'due_adjustment_type' => ['nullable', Rule::in(['due', 'advance'])],
            'due_adjustment_amount' => ['nullable', $this->decimalRule(2, 13)],
        ]);
        if ($validator->fails()) return response()->json(['status' => 'error', 'errors' => $validator->errors()], 422);

        $amountSar = MoneyDecimal::unsigned($request->input('amount_sar'), 2, 13);
        $customerRate = MoneyDecimal::unsigned($request->input('customer_rate'), 4, 6);
        if (DecimalMath::compareAmount($amountSar, '0') <= 0 || MoneyDecimal::compare($customerRate, '0', 4, 6, false) <= 0) {
            return response()->json(['status' => 'error', 'message' => 'A positive amount and customer rate are required.'], 422);
        }
        if ($request->input('receiver_account_type') !== 'Cash' && trim((string) $request->input('receiver_account_no')) === '') {
            return response()->json(['status' => 'error', 'message' => 'A payout account/mobile number is required for this payout method.'], 422);
        }

        $customer = Customer::query()->where('account_id', $accountId)->whereKey((int) $request->input('customer_id'))->first();
        if (!$customer) return response()->json(['status' => 'error', 'message' => 'Customer does not belong to the active account.'], 422);

        try {
            $result = DB::transaction(function () use ($request, $accountId, $amountSar, $customerRate) {
                $batch = WalletBatch::query()
                    ->where('account_id', $accountId)
                    ->whereNull('deleted_at')
                    ->whereKey((int) $request->input('wallet_batch_id'))
                    ->lockForUpdate()
                    ->first();
                if (!$batch) throw new \DomainException('Selected wallet stock is no longer available.');

                $amountBdt = DecimalMath::multiplyAmountRate($amountSar, $customerRate);
                if (DecimalMath::compareAmount($batch->remaining_bdt, $amountBdt) < 0) {
                    throw new \DomainException('Selected wallet stock does not have enough remaining BDT.');
                }

                $sarCollected = MoneyDecimal::unsigned($request->input('sar_collected', $amountSar), 2, 13);
                $bdtDisbursed = MoneyDecimal::unsigned($request->input('bdt_disbursed', $amountBdt), 2, 13);
                $timestamp = $this->timestamp($request->input('timestamp'));
                $transaction = $this->createTransaction($accountId, [
                    'type' => 'Pending',
                    'customer_id' => (int) $request->input('customer_id'),
                    'supplier_id' => $batch->supplier_id ?: null,
                    'amount_sar' => $amountSar,
                    'customer_rate' => $customerRate,
                    'supplier_rate' => MoneyDecimal::unsigned($batch->rate, 4, 6),
                    'amount_bdt' => $amountBdt,
                    'sar_collected' => $sarCollected,
                    'bdt_disbursed' => $bdtDisbursed,
                    'receiver_name' => trim((string) $request->input('receiver_name', 'Recipient')) ?: 'Recipient',
                    'receiver_phone' => (string) ($request->input('receiver_phone') ?: $request->input('receiver_account_no', '')),
                    'receiver_account_type' => (string) $request->input('receiver_account_type'),
                    'receiver_account_no' => (string) ($request->input('receiver_account_no') ?: 'Cash'),
                    'wallet_batch_id' => (int) $batch->id,
                    'notes' => $request->input('notes'),
                    'timestamp' => $timestamp,
                ]);

                $batch->remaining_bdt = DecimalMath::subtractAmount($batch->remaining_bdt, $amountBdt);
                $batch->save();

                $adjustment = null;
                $adjustmentType = $request->input('due_adjustment_type');
                $adjustmentAmount = MoneyDecimal::unsigned($request->input('due_adjustment_amount', 0), 2, 13);
                if ($adjustmentType && DecimalMath::compareAmount($adjustmentAmount, '0') > 0) {
                    $signedCollected = $adjustmentType === 'advance' ? '-' . $adjustmentAmount : $adjustmentAmount;
                    $adjustment = $this->createTransaction($accountId, [
                        'type' => 'Delivered',
                        'customer_id' => (int) $request->input('customer_id'),
                        'supplier_id' => null,
                        'amount_sar' => '0.00',
                        'customer_rate' => '0.0000',
                        'supplier_rate' => '0.0000',
                        'amount_bdt' => '0.00',
                        'sar_collected' => $signedCollected,
                        'bdt_disbursed' => '0.00',
                        'receiver_name' => $adjustmentType === 'advance' ? 'Advance Return' : 'Due Payment',
                        'receiver_phone' => 'N/A',
                        'receiver_account_type' => 'N/A',
                        'receiver_account_no' => 'N/A',
                        'wallet_batch_id' => null,
                        'notes' => $adjustmentType === 'advance' ? 'Advance Returned' : 'Previous Due Payment / Recovery',
                        'timestamp' => $timestamp,
                    ]);
                }

                return ['transaction' => $transaction, 'adjustment' => $adjustment, 'wallet_batch' => $batch->fresh()];
            });
        } catch (\DomainException $e) {
            return response()->json(['status' => 'error', 'message' => $e->getMessage()], 422);
        }

        return response()->json(['status' => 'success', 'message' => 'Customer transaction created.', ...$result], 201);
    }

    /** Android Due Collection / Advance Return zero-principal ledger entry. */
    public function customerAdjustment(Request $request): JsonResponse
    {
        $context = $this->authorized($request, 'can_add_transactions');
        if (isset($context['error'])) return $context['error'];
        $accountId = (int) $context['account_id'];
        $validator = Validator::make($request->all(), [
            'customer_id' => ['required', 'integer', 'min:1'],
            'kind' => ['required', Rule::in(['due', 'advance'])],
            'amount_sar' => ['required', $this->decimalRule(2, 13)],
            'timestamp' => ['nullable', 'integer', 'min:1'],
        ]);
        if ($validator->fails()) return response()->json(['status' => 'error', 'errors' => $validator->errors()], 422);
        if (!Customer::query()->where('account_id', $accountId)->whereKey((int) $request->input('customer_id'))->exists()) {
            return response()->json(['status' => 'error', 'message' => 'Customer does not belong to the active account.'], 422);
        }
        $amount = MoneyDecimal::unsigned($request->input('amount_sar'), 2, 13);
        if (DecimalMath::compareAmount($amount, '0') <= 0) return response()->json(['status' => 'error', 'message' => 'Adjustment amount must be positive.'], 422);
        $kind = (string) $request->input('kind');
        $transaction = DB::transaction(fn () => $this->createTransaction($accountId, [
            'type' => 'Delivered', 'customer_id' => (int) $request->input('customer_id'), 'supplier_id' => null,
            'amount_sar' => '0.00', 'customer_rate' => '0.0000', 'supplier_rate' => '0.0000', 'amount_bdt' => '0.00',
            'sar_collected' => $kind === 'advance' ? '-' . $amount : $amount, 'bdt_disbursed' => '0.00',
            'receiver_name' => $kind === 'advance' ? 'Advance Return' : 'Due Payment', 'receiver_phone' => 'N/A',
            'receiver_account_type' => 'N/A', 'receiver_account_no' => 'N/A', 'wallet_batch_id' => null,
            'notes' => $kind === 'advance' ? 'Advance Returned' : 'Previous Due Payment / Recovery',
            'timestamp' => $this->timestamp($request->input('timestamp')),
        ]));
        return response()->json(['status' => 'success', 'transaction' => $transaction], 201);
    }

    public function transactionStatus(Request $request, int $id): JsonResponse
    {
        $context = $this->authorized($request, 'can_edit_transactions');
        if (isset($context['error'])) return $context['error'];
        $request->validate(['status' => ['required', Rule::in(self::TRANSACTION_STATUSES)]]);
        $accountId = (int) $context['account_id'];
        try {
            $transaction = DB::transaction(function () use ($request, $accountId, $id) {
                $tx = Transaction::query()->where('account_id', $accountId)->whereNull('deleted_at')->whereKey($id)->lockForUpdate()->first();
                if (!$tx) throw new \DomainException('Transaction not found.');
                $oldStatus = (string) $tx->type;
                $newStatus = (string) $request->input('status');
                if ($oldStatus === $newStatus) return $tx;
                if ($tx->wallet_batch_id) {
                    $batch = WalletBatch::query()->where('account_id', $accountId)->whereKey((int) $tx->wallet_batch_id)->lockForUpdate()->first();
                    if ($batch) {
                        if ($newStatus === 'Cancelled' && $oldStatus !== 'Cancelled') {
                            $batch->remaining_bdt = DecimalMath::addAmount($batch->remaining_bdt, $tx->amount_bdt);
                            $batch->save();
                        } elseif ($oldStatus === 'Cancelled' && $newStatus !== 'Cancelled') {
                            if (DecimalMath::compareAmount($batch->remaining_bdt, $tx->amount_bdt) < 0) throw new \DomainException('Wallet stock is insufficient to reactivate this transaction.');
                            $batch->remaining_bdt = DecimalMath::subtractAmount($batch->remaining_bdt, $tx->amount_bdt);
                            $batch->save();
                        }
                    }
                }
                $tx->type = $newStatus;
                $tx->save();
                return $tx->fresh();
            });
        } catch (\DomainException $e) {
            return response()->json(['status' => 'error', 'message' => $e->getMessage()], str_contains($e->getMessage(), 'not found') ? 404 : 422);
        }
        return response()->json(['status' => 'success', 'transaction' => $transaction]);
    }

    public function updateTransaction(Request $request, int $id): JsonResponse
    {
        $context = $this->authorized($request, 'can_edit_transactions');
        if (isset($context['error'])) return $context['error'];
        $accountId = (int) $context['account_id'];
        $validator = Validator::make($request->all(), [
            'amount_sar' => ['required', $this->decimalRule(2, 13)], 'customer_rate' => ['required', $this->decimalRule(4, 6)],
            'supplier_rate' => ['nullable', $this->decimalRule(4, 6)], 'sar_collected' => ['required', $this->decimalRule(2, 13, true)],
            'bdt_disbursed' => ['nullable', $this->decimalRule(2, 13)], 'supplier_id' => ['nullable', 'integer', 'min:1'],
            'wallet_batch_id' => ['nullable', 'integer', 'min:1'], 'receiver_name' => ['nullable', 'string', 'max:255'],
            'receiver_phone' => ['nullable', 'string', 'max:50'], 'receiver_account_type' => ['required', 'string', 'max:50'],
            'receiver_account_no' => ['nullable', 'string', 'max:100'], 'notes' => ['nullable', 'string', 'max:5000'],
            'status' => ['required', Rule::in(self::TRANSACTION_STATUSES)],
        ]);
        if ($validator->fails()) return response()->json(['status' => 'error', 'errors' => $validator->errors()], 422);

        try {
            $transaction = DB::transaction(function () use ($request, $accountId, $id) {
                $tx = Transaction::query()->where('account_id', $accountId)->whereNull('deleted_at')->whereKey($id)->lockForUpdate()->first();
                if (!$tx) throw new \DomainException('Transaction not found.');
                $oldBatch = $tx->wallet_batch_id ? WalletBatch::query()->where('account_id', $accountId)->whereKey((int) $tx->wallet_batch_id)->lockForUpdate()->first() : null;
                if ((string) $tx->type !== 'Cancelled' && $oldBatch) {
                    $oldBatch->remaining_bdt = DecimalMath::addAmount($oldBatch->remaining_bdt, $tx->amount_bdt);
                    $oldBatch->save();
                }

                $amountSar = MoneyDecimal::unsigned($request->input('amount_sar'), 2, 13);
                $customerRate = MoneyDecimal::unsigned($request->input('customer_rate'), 4, 6);
                $amountBdt = DecimalMath::multiplyAmountRate($amountSar, $customerRate);
                $newBatchId = $request->filled('wallet_batch_id') ? (int) $request->input('wallet_batch_id') : null;
                $newBatch = $newBatchId ? WalletBatch::query()->where('account_id', $accountId)->whereNull('deleted_at')->whereKey($newBatchId)->lockForUpdate()->first() : null;
                if ($newBatchId && !$newBatch) throw new \DomainException('Selected wallet stock is not available.');
                if ((string) $request->input('status') !== 'Cancelled' && $newBatch) {
                    if (DecimalMath::compareAmount($newBatch->remaining_bdt, $amountBdt) < 0) throw new \DomainException('Selected wallet stock does not have enough remaining BDT.');
                    $newBatch->remaining_bdt = DecimalMath::subtractAmount($newBatch->remaining_bdt, $amountBdt);
                    $newBatch->save();
                }
                $supplierId = $request->filled('supplier_id') ? (int) $request->input('supplier_id') : ($newBatch?->supplier_id ?: null);
                if ($supplierId && !Supplier::query()->where('account_id', $accountId)->whereKey($supplierId)->exists()) throw new \DomainException('Supplier does not belong to the active account.');

                $tx->fill([
                    'type' => (string) $request->input('status'), 'amount' => $amountSar, 'amount_sar' => $amountSar,
                    'supplier_id' => $supplierId, 'customer_rate' => $customerRate,
                    'supplier_rate' => MoneyDecimal::unsigned($request->input('supplier_rate', $newBatch?->rate ?: $customerRate), 4, 6),
                    'amount_bdt' => $amountBdt, 'sar_collected' => MoneyDecimal::signed($request->input('sar_collected'), 2, 13),
                    'bdt_disbursed' => MoneyDecimal::unsigned($request->input('bdt_disbursed', $amountBdt), 2, 13),
                    'receiver_name' => $request->input('receiver_name'), 'receiver_phone' => $request->input('receiver_phone'),
                    'receiver_account_type' => $request->input('receiver_account_type'), 'receiver_account_no' => $request->input('receiver_account_no'),
                    'wallet_batch_id' => $newBatchId, 'notes' => $request->input('notes'),
                ]);
                $tx->save();
                return $tx->fresh();
            });
        } catch (\DomainException $e) {
            return response()->json(['status' => 'error', 'message' => $e->getMessage()], str_contains($e->getMessage(), 'not found') ? 404 : 422);
        }
        return response()->json(['status' => 'success', 'transaction' => $transaction]);
    }

    public function deleteTransaction(Request $request, int $id): JsonResponse
    {
        $context = $this->authorized($request, 'can_delete_transactions');
        if (isset($context['error'])) return $context['error'];
        if (!$request->boolean('confirmed')) return response()->json(['status' => 'confirmation_required', 'message' => 'Confirmation required.'], 409);
        $accountId = (int) $context['account_id'];
        try {
            DB::transaction(function () use ($accountId, $id) {
                $tx = Transaction::query()->where('account_id', $accountId)->whereNull('deleted_at')->whereKey($id)->lockForUpdate()->first();
                if (!$tx) throw new \DomainException('Transaction not found.');
                if ((string) $tx->type !== 'Cancelled' && $tx->wallet_batch_id) {
                    $batch = WalletBatch::query()->where('account_id', $accountId)->whereKey((int) $tx->wallet_batch_id)->lockForUpdate()->first();
                    if ($batch) { $batch->remaining_bdt = DecimalMath::addAmount($batch->remaining_bdt, $tx->amount_bdt); $batch->save(); }
                }
                $tx->delete();
            });
        } catch (\DomainException $e) {
            return response()->json(['status' => 'error', 'message' => $e->getMessage()], 404);
        }
        return response()->json(['status' => 'success', 'id' => $id]);
    }

    /** Android Supplier Profile -> New Transaction -> Fund Purchase/Settlement. */
    public function supplierFund(Request $request): JsonResponse
    {
        $context = $this->authorized($request, 'can_manage_wallet');
        if (isset($context['error'])) return $context['error'];
        $accountId = (int) $context['account_id'];
        $types = array_merge(self::SUPPLIER_PURCHASE_TYPES, self::SUPPLIER_SETTLEMENT_TYPES);
        $validator = Validator::make($request->all(), [
            'supplier_id' => ['required', 'integer', 'min:1'], 'transaction_type' => ['required', Rule::in($types)],
            'amount_sar' => ['required', $this->decimalRule(2, 13)], 'rate' => ['required', $this->decimalRule(4, 6)],
            'paid_bdt' => ['nullable', $this->decimalRule(2, 13)], 'ledger_id' => ['nullable', 'integer', 'min:1'],
            'notes' => ['nullable', 'string', 'max:10000'], 'timestamp' => ['nullable', 'integer', 'min:1'],
        ]);
        if ($validator->fails()) return response()->json(['status' => 'error', 'errors' => $validator->errors()], 422);
        if (!Supplier::query()->where('account_id', $accountId)->whereKey((int) $request->input('supplier_id'))->exists()) return response()->json(['status' => 'error', 'message' => 'Supplier does not belong to the active account.'], 422);
        $type = (string) $request->input('transaction_type');
        $purchase = in_array($type, self::SUPPLIER_PURCHASE_TYPES, true);
        if ($purchase && !$request->filled('ledger_id')) return response()->json(['status' => 'error', 'message' => 'A wallet ledger is required for a supplier fund purchase.'], 422);
        if ($request->filled('ledger_id') && !WalletLedger::query()->where('account_id', $accountId)->whereNull('deleted_at')->whereKey((int) $request->input('ledger_id'))->exists()) return response()->json(['status' => 'error', 'message' => 'Wallet ledger does not belong to the active account.'], 422);

        $amountSar = MoneyDecimal::unsigned($request->input('amount_sar'), 2, 13);
        $rate = MoneyDecimal::unsigned($request->input('rate'), 4, 6);
        if (DecimalMath::compareAmount($amountSar, '0') <= 0 || MoneyDecimal::compare($rate, 0, 4, 6, false) <= 0) return response()->json(['status' => 'error', 'message' => 'Positive SAR amount and rate are required.'], 422);
        $amountBdt = DecimalMath::multiplyAmountRate($amountSar, $rate);
        $paidBdt = MoneyDecimal::unsigned($request->input('paid_bdt', $amountBdt), 2, 13);

        $result = DB::transaction(function () use ($request, $accountId, $type, $purchase, $amountSar, $rate, $amountBdt, $paidBdt) {
            $deposit = SupplierDeposit::create([
                'account_id' => $accountId, 'local_id' => $this->localId(), 'supplier_id' => (int) $request->input('supplier_id'),
                'amount_sar' => $amountSar, 'rate' => $rate, 'amount_bdt' => $amountBdt, 'paid_bdt' => $paidBdt,
                'transaction_type' => $type, 'notes' => $request->input('notes'), 'timestamp' => $this->timestamp($request->input('timestamp')),
            ]);
            $batch = null;
            if ($purchase) {
                $supplier = Supplier::find((int) $request->input('supplier_id'));
                $batch = WalletBatch::create([
                    'account_id' => $accountId, 'local_id' => $this->localId(), 'ledger_id' => (int) $request->input('ledger_id'),
                    'rate' => $rate, 'initial_bdt' => $amountBdt, 'remaining_bdt' => $amountBdt,
                    'supplier_id' => (int) $request->input('supplier_id'), 'supplier_deposit_id' => (int) $deposit->id,
                    'notes' => 'Purchased BDT from ' . ($supplier?->name ?: 'Supplier'), 'timestamp' => $deposit->timestamp,
                ]);
            }
            return ['supplier_deposit' => $deposit, 'wallet_batch' => $batch];
        });
        return response()->json(['status' => 'success', ...$result], 201);
    }

    public function updateSupplierFund(Request $request, int $id): JsonResponse
    {
        $context = $this->authorized($request, 'can_manage_wallet');
        if (isset($context['error'])) return $context['error'];
        $accountId = (int) $context['account_id'];
        $types = array_merge(self::SUPPLIER_PURCHASE_TYPES, self::SUPPLIER_SETTLEMENT_TYPES);
        $validator = Validator::make($request->all(), [
            'transaction_type' => ['required', Rule::in($types)], 'amount_sar' => ['required', $this->decimalRule(2, 13)],
            'rate' => ['required', $this->decimalRule(4, 6)], 'paid_bdt' => ['nullable', $this->decimalRule(2, 13)],
            'ledger_id' => ['nullable', 'integer', 'min:1'], 'notes' => ['nullable', 'string', 'max:10000'],
        ]);
        if ($validator->fails()) return response()->json(['status' => 'error', 'errors' => $validator->errors()], 422);
        $type = (string) $request->input('transaction_type'); $purchase = in_array($type, self::SUPPLIER_PURCHASE_TYPES, true);
        if ($purchase && !$request->filled('ledger_id')) return response()->json(['status' => 'error', 'message' => 'A wallet ledger is required for a supplier fund purchase.'], 422);
        if ($request->filled('ledger_id') && !WalletLedger::query()->where('account_id', $accountId)->whereNull('deleted_at')->whereKey((int) $request->input('ledger_id'))->exists()) return response()->json(['status' => 'error', 'message' => 'Wallet ledger does not belong to the active account.'], 422);
        $amountSar = MoneyDecimal::unsigned($request->input('amount_sar'), 2, 13); $rate = MoneyDecimal::unsigned($request->input('rate'), 4, 6);
        $amountBdt = DecimalMath::multiplyAmountRate($amountSar, $rate); $paidBdt = MoneyDecimal::unsigned($request->input('paid_bdt', $amountBdt), 2, 13);
        try {
            $result = DB::transaction(function () use ($request, $accountId, $id, $type, $purchase, $amountSar, $rate, $amountBdt, $paidBdt) {
                $deposit = SupplierDeposit::query()->where('account_id', $accountId)->whereNull('deleted_at')->whereKey($id)->lockForUpdate()->first();
                if (!$deposit) throw new \DomainException('Supplier fund record not found.');
                $batch = WalletBatch::query()->where('account_id', $accountId)->whereNull('deleted_at')->where('supplier_deposit_id', $deposit->id)->lockForUpdate()->first();
                $deposit->fill(['amount_sar' => $amountSar, 'rate' => $rate, 'amount_bdt' => $amountBdt, 'paid_bdt' => $paidBdt, 'transaction_type' => $type, 'notes' => $request->input('notes')]);
                $deposit->save();
                if ($purchase) {
                    if ($batch) {
                        $difference = DecimalMath::subtractAmount($amountBdt, $batch->initial_bdt);
                        $remaining = DecimalMath::addAmount($batch->remaining_bdt, $difference);
                        if (DecimalMath::compareAmount($remaining, '0') < 0) $remaining = '0.00';
                        $batch->fill(['ledger_id' => (int) $request->input('ledger_id'), 'rate' => $rate, 'initial_bdt' => $amountBdt, 'remaining_bdt' => $remaining]);
                        $batch->save();
                    } else {
                        $batch = WalletBatch::create(['account_id' => $accountId, 'local_id' => $this->localId(), 'ledger_id' => (int) $request->input('ledger_id'), 'rate' => $rate, 'initial_bdt' => $amountBdt, 'remaining_bdt' => $amountBdt, 'supplier_id' => $deposit->supplier_id, 'supplier_deposit_id' => $deposit->id, 'notes' => 'Supplier fund purchase', 'timestamp' => $deposit->timestamp]);
                    }
                } elseif ($batch) {
                    $batch->delete(); $batch = null;
                }
                return ['supplier_deposit' => $deposit->fresh(), 'wallet_batch' => $batch?->fresh()];
            });
        } catch (\DomainException $e) {
            return response()->json(['status' => 'error', 'message' => $e->getMessage()], 404);
        }
        return response()->json(['status' => 'success', ...$result]);
    }

    public function deleteSupplierFund(Request $request, int $id): JsonResponse
    {
        $context = $this->authorized($request, 'can_manage_wallet'); if (isset($context['error'])) return $context['error'];
        if (!$request->boolean('confirmed')) return response()->json(['status' => 'confirmation_required', 'message' => 'Confirmation required.'], 409);
        $accountId = (int) $context['account_id'];
        try {
            DB::transaction(function () use ($accountId, $id) {
                $deposit = SupplierDeposit::query()->where('account_id', $accountId)->whereNull('deleted_at')->whereKey($id)->lockForUpdate()->first();
                if (!$deposit) throw new \DomainException('Supplier fund record not found.');
                WalletBatch::query()->where('account_id', $accountId)->whereNull('deleted_at')->where('supplier_deposit_id', $deposit->id)->get()->each->delete();
                $deposit->delete();
            });
        } catch (\DomainException $e) { return response()->json(['status' => 'error', 'message' => $e->getMessage()], 404); }
        return response()->json(['status' => 'success', 'id' => $id]);
    }

    public function createWalletLedger(Request $request): JsonResponse
    {
        $context = $this->authorized($request, 'can_manage_wallet'); if (isset($context['error'])) return $context['error'];
        $validated = $request->validate(['name' => ['required', 'string', 'max:255']]);
        $ledger = WalletLedger::create(['account_id' => $context['account_id'], 'local_id' => $this->localId(), 'name' => trim($validated['name']), 'timestamp' => time()]);
        return response()->json(['status' => 'success', 'wallet_ledger' => $ledger], 201);
    }

    public function renameWalletLedger(Request $request, int $id): JsonResponse
    {
        $context = $this->authorized($request, 'can_manage_wallet'); if (isset($context['error'])) return $context['error'];
        $validated = $request->validate(['name' => ['required', 'string', 'max:255']]);
        $ledger = WalletLedger::query()->where('account_id', $context['account_id'])->whereNull('deleted_at')->whereKey($id)->first();
        if (!$ledger) return response()->json(['status' => 'error', 'message' => 'Wallet ledger not found.'], 404);
        $ledger->name = trim($validated['name']); $ledger->save();
        return response()->json(['status' => 'success', 'wallet_ledger' => $ledger]);
    }

    public function deleteWalletLedger(Request $request, int $id): JsonResponse
    {
        $context = $this->authorized($request, 'can_manage_wallet'); if (isset($context['error'])) return $context['error'];
        if (!$request->boolean('confirmed')) return response()->json(['status' => 'confirmation_required', 'message' => 'Confirmation required.'], 409);
        $accountId = (int) $context['account_id'];
        try {
            DB::transaction(function () use ($accountId, $id) {
                $ledger = WalletLedger::query()->where('account_id', $accountId)->whereNull('deleted_at')->whereKey($id)->lockForUpdate()->first();
                if (!$ledger) throw new \DomainException('Wallet ledger not found.');
                $batches = WalletBatch::query()->where('account_id', $accountId)->whereNull('deleted_at')->where('ledger_id', $id)->lockForUpdate()->get();
                $balance = '0.00'; foreach ($batches as $batch) $balance = DecimalMath::addAmount($balance, $batch->remaining_bdt);
                if (DecimalMath::compareAmount($balance, '0') > 0) throw new \DomainException('Wallet ledger cannot be deleted while it still has a balance.');
                foreach ($batches as $batch) $batch->delete();
                $ledger->delete();
            });
        } catch (\DomainException $e) { return response()->json(['status' => 'error', 'message' => $e->getMessage()], str_contains($e->getMessage(), 'not found') ? 404 : 422); }
        return response()->json(['status' => 'success', 'id' => $id]);
    }

    public function walletDeposit(Request $request): JsonResponse
    {
        $context = $this->authorized($request, 'can_manage_wallet'); if (isset($context['error'])) return $context['error'];
        $accountId = (int) $context['account_id'];
        $validator = Validator::make($request->all(), ['ledger_id' => ['required', 'integer', 'min:1'], 'amount_bdt' => ['required', $this->decimalRule(2, 13)], 'rate' => ['required', $this->decimalRule(4, 6)], 'notes' => ['nullable', 'string', 'max:10000'], 'timestamp' => ['nullable', 'integer', 'min:1']]);
        if ($validator->fails()) return response()->json(['status' => 'error', 'errors' => $validator->errors()], 422);
        if (!WalletLedger::query()->where('account_id', $accountId)->whereNull('deleted_at')->whereKey((int) $request->input('ledger_id'))->exists()) return response()->json(['status' => 'error', 'message' => 'Wallet ledger not found.'], 422);
        $amount = MoneyDecimal::unsigned($request->input('amount_bdt'), 2, 13); $rate = MoneyDecimal::unsigned($request->input('rate'), 4, 6);
        if (DecimalMath::compareAmount($amount, 0) <= 0 || MoneyDecimal::compare($rate, 0, 4, 6, false) <= 0) return response()->json(['status' => 'error', 'message' => 'Positive BDT amount and rate are required.'], 422);
        $batch = WalletBatch::create(['account_id' => $accountId, 'local_id' => $this->localId(), 'ledger_id' => (int) $request->input('ledger_id'), 'rate' => $rate, 'initial_bdt' => $amount, 'remaining_bdt' => $amount, 'supplier_id' => null, 'supplier_deposit_id' => null, 'notes' => trim((string) $request->input('notes')) ?: 'Manual Capital Deposit', 'timestamp' => $this->timestamp($request->input('timestamp'))]);
        return response()->json(['status' => 'success', 'wallet_batch' => $batch], 201);
    }

    /** Android deductMoneyFromWalletLedger: oldest active stock is consumed first. */
    public function walletWithdraw(Request $request): JsonResponse
    {
        $context = $this->authorized($request, 'can_manage_wallet'); if (isset($context['error'])) return $context['error'];
        $accountId = (int) $context['account_id'];
        $validator = Validator::make($request->all(), ['ledger_id' => ['required', 'integer', 'min:1'], 'amount_bdt' => ['required', $this->decimalRule(2, 13)]]);
        if ($validator->fails()) return response()->json(['status' => 'error', 'errors' => $validator->errors()], 422);
        $amount = MoneyDecimal::unsigned($request->input('amount_bdt'), 2, 13); if (DecimalMath::compareAmount($amount, 0) <= 0) return response()->json(['status' => 'error', 'message' => 'Withdrawal amount must be positive.'], 422);
        try {
            $updated = DB::transaction(function () use ($accountId, $request, $amount) {
                $ledgerId = (int) $request->input('ledger_id');
                if (!WalletLedger::query()->where('account_id', $accountId)->whereNull('deleted_at')->whereKey($ledgerId)->exists()) throw new \DomainException('Wallet ledger not found.');
                $batches = WalletBatch::query()->where('account_id', $accountId)->whereNull('deleted_at')->where('ledger_id', $ledgerId)->orderBy('timestamp')->orderBy('id')->lockForUpdate()->get();
                $total = '0.00'; foreach ($batches as $batch) $total = DecimalMath::addAmount($total, $batch->remaining_bdt);
                if (DecimalMath::compareAmount($total, $amount) < 0) throw new \DomainException('Wallet ledger does not have enough BDT for this withdrawal.');
                $remaining = $amount; $changed = [];
                foreach ($batches as $batch) {
                    if (DecimalMath::compareAmount($remaining, 0) <= 0) break;
                    if (DecimalMath::compareAmount($batch->remaining_bdt, 0) <= 0) continue;
                    $deduct = DecimalMath::minAmount($batch->remaining_bdt, $remaining);
                    $batch->remaining_bdt = DecimalMath::subtractAmount($batch->remaining_bdt, $deduct); $batch->save();
                    $remaining = DecimalMath::subtractAmount($remaining, $deduct); $changed[] = $batch->fresh();
                }
                return $changed;
            });
        } catch (\DomainException $e) { return response()->json(['status' => 'error', 'message' => $e->getMessage()], str_contains($e->getMessage(), 'not found') ? 404 : 422); }
        return response()->json(['status' => 'success', 'wallet_batches' => $updated]);
    }

    private function authorized(Request $request, string $permission): array
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context;
        $permissions = $this->effectivePermissions($request, (int) $context['account_id']);
        if (empty($permissions[$permission])) {
            return ['error' => response()->json(['status' => 'error', 'message' => 'Forbidden: permission required.', 'permission' => $permission], 403)];
        }
        return $context;
    }

    private function effectivePermissions(Request $request, int $accountId): array
    {
        $user = $request->user();
        $permissions = $user?->getFormattedPermissions() ?? [];
        if (!$user) return $permissions;
        $share = UserAccountShare::query()->where('shared_with_user_id', $user->id)->where('account_id', $accountId)->where('owner_user_id', '!=', $user->id)->first();
        if ($share && is_array($share->permissions_override)) {
            foreach ($share->permissions_override as $key => $allowed) {
                if (array_key_exists($key, $permissions)) $permissions[$key] = (bool) $permissions[$key] && (bool) $allowed;
            }
        }
        return $permissions;
    }

    private function createTransaction(int $accountId, array $values): Transaction
    {
        $values['account_id'] = $accountId; $values['local_id'] = $this->localId(); $values['amount'] = $values['amount_sar'];
        return Transaction::create($values);
    }

    private function localId(): int
    {
        static $last = 0;
        $candidate = (int) floor(microtime(true) * 1_000_000);
        if ($candidate <= $last) $candidate = $last + 1;
        $last = $candidate;
        return $candidate;
    }

    private function timestamp(mixed $value): int
    {
        $ts = (int) ($value ?: time()); if ($ts > 2_000_000_000) $ts = (int) floor($ts / 1000);
        return ($ts > 0 && $ts <= time() + 86400) ? $ts : time();
    }

    private function decimalRule(int $scale, int $integerDigits, bool $signed = false): \Closure
    {
        return static function (string $attribute, mixed $value, \Closure $fail) use ($scale, $integerDigits, $signed): void {
            if ($value === null || $value === '') return;
            try { $signed ? MoneyDecimal::signed($value, $scale, $integerDigits) : MoneyDecimal::unsigned($value, $scale, $integerDigits); }
            catch (\InvalidArgumentException) { $fail("The {$attribute} field must be a supported fixed-point decimal."); }
        };
    }
}
