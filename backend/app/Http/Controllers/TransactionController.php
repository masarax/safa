<?php

namespace App\Http\Controllers;

use App\Models\Customer;
use App\Models\Supplier;
use App\Models\Transaction;
use App\Models\WalletBatch;
use App\Services\TransactionWalletAccounting;
use App\Support\MoneyDecimal;
use DomainException;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Validator;

class TransactionController extends Controller
{
    use AuthorizeAccountContext;

    public function __construct(private readonly TransactionWalletAccounting $walletAccounting) {}

    private function timestamp($value): int
    {
        $ts = (int) ($value ?? time());
        if ($ts > 2000000000) $ts = (int) floor($ts / 1000);
        return ($ts > 0 && $ts <= time() + 86400) ? $ts : time();
    }

    private function decimal(mixed $value, int $scale, int $integerDigits): string
    {
        if ($value === null || $value === '') $value = 0;
        return MoneyDecimal::unsigned($value, $scale, $integerDigits);
    }

    private function exactDecimalRule(int $scale, int $integerDigits, bool $signed = false): \Closure
    {
        return static function (string $attribute, mixed $value, \Closure $fail) use ($scale, $integerDigits, $signed): void {
            if ($value === null || $value === '') return;
            try {
                $signed
                    ? MoneyDecimal::signed($value, $scale, $integerDigits)
                    : MoneyDecimal::unsigned($value, $scale, $integerDigits);
            } catch (\InvalidArgumentException) {
                $fail("The {$attribute} field must be a supported fixed-point decimal.");
            }
        };
    }

    private function validateAccountRelationships(array $input, int $accountId)
    {
        foreach (['customer_id' => Customer::class, 'supplier_id' => Supplier::class, 'wallet_batch_id' => WalletBatch::class] as $field => $model) {
            if (!array_key_exists($field, $input) || (int) $input[$field] === 0) continue;
            if (!$model::query()->whereKey((int) $input[$field])->where('account_id', $accountId)->exists()) {
                return response()->json(['status' => 'error', 'message' => "Invalid {$field}: record does not belong to the active account."], 422);
            }
        }
        return null;
    }

    private function nullableForeignKey(Request $request, string $field): ?int
    {
        if (!$request->has($field)) return null;
        $value = (int) $request->input($field);
        return $value > 0 ? $value : null;
    }

    private function valuesForStore(Request $request, string $amountSar, string $amountBdt, ?int $batchId, string $status): array
    {
        return [
            'type' => $status,
            'amount' => $amountSar,
            'amount_sar' => $amountSar,
            'customer_id' => $this->nullableForeignKey($request, 'customer_id'),
            'supplier_id' => $this->nullableForeignKey($request, 'supplier_id'),
            'customer_rate' => $this->decimal($request->input('customer_rate') ?: 0, 4, 6),
            'supplier_rate' => $this->decimal($request->input('supplier_rate') ?: 0, 4, 6),
            'amount_bdt' => $amountBdt,
            'sar_collected' => MoneyDecimal::signed($request->input('sar_collected', $amountSar), 2, 13),
            'bdt_disbursed' => MoneyDecimal::unsigned($request->input('bdt_disbursed', $amountBdt), 2, 13),
            'receiver_name' => substr((string) $request->input('receiver_name', ''), 0, 255),
            'receiver_phone' => substr((string) $request->input('receiver_phone', ''), 0, 50),
            'receiver_account_type' => substr((string) $request->input('receiver_account_type', ''), 0, 50),
            'receiver_account_no' => substr((string) $request->input('receiver_account_no', ''), 0, 100),
            'wallet_batch_id' => $batchId,
            'notes' => $request->input('notes'),
            'timestamp' => $this->timestamp($request->input('timestamp')),
            'hash' => $request->input('hash'),
            'deleted_at' => null,
        ];
    }

    public function index(Request $request)
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];
        return response()->json(['status' => 'success', 'transactions' => Transaction::where('account_id', $context['account_id'])->whereNull('deleted_at')->orderByDesc('timestamp')->orderByDesc('id')->paginate(min(max((int) $request->integer('per_page', 50), 1), 200))]);
    }

    public function store(Request $request)
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];

        $validator = Validator::make($request->all(), [
            'amount_sar' => ['nullable', $this->exactDecimalRule(2, 13)], 'amount' => ['nullable', $this->exactDecimalRule(2, 13)], 'customer_id' => 'nullable|integer|min:0', 'supplier_id' => 'nullable|integer|min:0',
            'customer_rate' => ['nullable', $this->exactDecimalRule(4, 6)], 'supplier_rate' => ['nullable', $this->exactDecimalRule(4, 6)], 'amount_bdt' => ['nullable', $this->exactDecimalRule(2, 13)],
            'sar_collected' => ['nullable', $this->exactDecimalRule(2, 13, true)], 'bdt_disbursed' => ['nullable', $this->exactDecimalRule(2, 13)], 'receiver_name' => 'nullable|string|max:255',
            'receiver_phone' => 'nullable|string|max:50', 'receiver_account_type' => 'nullable|string|max:50', 'receiver_account_no' => 'nullable|string|max:100',
            'wallet_batch_id' => 'nullable|integer|min:0', 'notes' => 'nullable|string|max:5000', 'local_id' => 'nullable|integer|min:1',
            'timestamp' => 'nullable|integer|min:1', 'type' => 'nullable|string|max:20', 'hash' => 'nullable|string|max:255',
        ]);
        if ($validator->fails()) return response()->json(['status' => 'error', 'errors' => $validator->errors()], 422);
        $accountId = (int) $context['account_id'];
        if ($relationshipError = $this->validateAccountRelationships($request->all(), $accountId)) return $relationshipError;

        try {
            $transaction = DB::transaction(function () use ($request, $accountId) {
                $amountSar = $this->decimal($request->input('amount_sar') ?? $request->input('amount') ?? 0, 2, 13);
                $localId = (int) ($request->input('local_id') ?: floor(microtime(true) * 1000));
                $amountBdt = $this->decimal($request->input('amount_bdt') ?: 0, 2, 13);
                $status = substr((string) $request->input('type', 'Delivered'), 0, 20);
                $batchId = $this->nullableForeignKey($request, 'wallet_batch_id');
                $existing = Transaction::withTrashed()
                    ->where('account_id', $accountId)
                    ->where('local_id', $localId)
                    ->lockForUpdate()
                    ->first();

                if ($existing) $this->walletAccounting->restoreExisting($existing, $accountId);
                $this->walletAccounting->debitNew($accountId, $batchId, $amountBdt, $status);
                $values = $this->valuesForStore($request, $amountSar, $amountBdt, $batchId, $status);

                if ($existing) {
                    $existing->fill($values);
                    $existing->save();
                    return $existing->fresh();
                }

                return Transaction::create(array_merge([
                    'account_id' => $accountId,
                    'local_id' => $localId,
                ], $values));
            });
            return response()->json(['status' => 'success', 'message' => 'Transaction saved successfully.', 'transaction' => $transaction, 'id' => (int) $transaction->id], 201);
        } catch (DomainException $e) {
            return response()->json(['status' => 'error', 'message' => $e->getMessage()], 422);
        } catch (\Throwable $e) {
            report($e);
            return response()->json(['status' => 'error', 'message' => 'Unable to save transaction.'], 422);
        }
    }

    public function update(Request $request, $id)
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];
        $accountId = (int) $context['account_id'];
        $transaction = Transaction::withTrashed()->where('account_id', $accountId)->where(fn ($q) => $q->where('id', (int) $id)->orWhere('local_id', (int) $id))->first();
        if (!$transaction) return response()->json(['status' => 'error', 'message' => 'Transaction not found.'], 404);

        $validator = Validator::make($request->all(), [
            'type' => 'nullable|string|max:20', 'customer_id' => 'nullable|integer|min:0', 'supplier_id' => 'nullable|integer|min:0',
            'amount_sar' => ['nullable', $this->exactDecimalRule(2, 13)], 'amount' => ['nullable', $this->exactDecimalRule(2, 13)], 'customer_rate' => ['nullable', $this->exactDecimalRule(4, 6)], 'supplier_rate' => ['nullable', $this->exactDecimalRule(4, 6)], 'amount_bdt' => ['nullable', $this->exactDecimalRule(2, 13)],
            'sar_collected' => ['nullable', $this->exactDecimalRule(2, 13, true)], 'bdt_disbursed' => ['nullable', $this->exactDecimalRule(2, 13)],
            'receiver_name' => 'nullable|string|max:255', 'receiver_phone' => 'nullable|string|max:50', 'receiver_account_type' => 'nullable|string|max:50',
            'receiver_account_no' => 'nullable|string|max:100', 'wallet_batch_id' => 'nullable|integer|min:0', 'notes' => 'nullable|string|max:5000',
            'timestamp' => 'nullable|integer|min:1', 'hash' => 'nullable|string|max:255',
        ]);
        if ($validator->fails()) return response()->json(['status' => 'error', 'errors' => $validator->errors()], 422);
        if ($relationshipError = $this->validateAccountRelationships($request->all(), $accountId)) return $relationshipError;

        try {
            $transaction = DB::transaction(function () use ($request, $accountId, $id) {
                $transaction = Transaction::withTrashed()
                    ->where('account_id', $accountId)
                    ->where(fn ($q) => $q->where('id', (int) $id)->orWhere('local_id', (int) $id))
                    ->lockForUpdate()
                    ->first();
                if (!$transaction) throw new DomainException('Transaction not found.');

                $this->walletAccounting->restoreExisting($transaction, $accountId);

                foreach (['type','receiver_name','receiver_phone','receiver_account_type','receiver_account_no','notes','hash'] as $field) {
                    if ($request->has($field)) $transaction->{$field} = $request->input($field);
                }
                foreach (['customer_id','supplier_id','wallet_batch_id'] as $field) {
                    if ($request->has($field)) $transaction->{$field} = $this->nullableForeignKey($request, $field);
                }
                foreach (['amount_sar','customer_rate','supplier_rate','amount_bdt'] as $field) {
                    if ($request->has($field)) $transaction->{$field} = $this->decimal($request->input($field) ?: 0, in_array($field, ['amount_sar','amount_bdt'], true) ? 2 : 4, in_array($field, ['amount_sar','amount_bdt'], true) ? 13 : 6);
                }
                if ($request->has('sar_collected')) $transaction->sar_collected = MoneyDecimal::signed($request->input('sar_collected'), 2, 13);
                if ($request->has('bdt_disbursed')) $transaction->bdt_disbursed = MoneyDecimal::unsigned($request->input('bdt_disbursed'), 2, 13);
                if ($request->has('amount') && !$request->has('amount_sar')) $transaction->amount_sar = $this->decimal($request->input('amount') ?: 0, 2, 13);
                $transaction->amount = $transaction->amount_sar;
                if ($request->has('timestamp')) $transaction->timestamp = $this->timestamp($request->input('timestamp'));
                $transaction->deleted_at = null;

                $this->walletAccounting->debitNew(
                    $accountId,
                    $transaction->wallet_batch_id ? (int) $transaction->wallet_batch_id : null,
                    $transaction->amount_bdt,
                    (string) $transaction->type,
                );
                $transaction->save();
                return $transaction->fresh();
            });
            return response()->json(['status' => 'success', 'message' => 'Transaction updated successfully.', 'transaction' => $transaction]);
        } catch (DomainException $e) {
            return response()->json(['status' => 'error', 'message' => $e->getMessage()], $e->getMessage() === 'Transaction not found.' ? 404 : 422);
        } catch (\Throwable $e) {
            report($e);
            return response()->json(['status' => 'error', 'message' => 'Unable to update transaction.'], 422);
        }
    }

    public function destroy(Request $request, $id)
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];
        if (!$request->boolean('confirmed')) return response()->json(['status' => 'confirmation_required', 'message' => 'Confirmation required before deleting transaction.', 'requires_confirmation' => true], 409);
        $accountId = (int) $context['account_id'];

        try {
            $deletedId = DB::transaction(function () use ($accountId, $id) {
                $transaction = Transaction::query()
                    ->where('account_id', $accountId)
                    ->where(fn ($q) => $q->where('id', (int) $id)->orWhere('local_id', (int) $id))
                    ->lockForUpdate()
                    ->first();
                if (!$transaction) throw new DomainException('Transaction not found.');
                $this->walletAccounting->restoreExisting($transaction, $accountId);
                $transaction->delete();
                return (int) $transaction->id;
            });
        } catch (DomainException $e) {
            return response()->json(['status' => 'error', 'message' => $e->getMessage()], 404);
        }

        return response()->json(['status' => 'success', 'message' => 'Transaction deleted successfully.', 'id' => $deletedId]);
    }
}
