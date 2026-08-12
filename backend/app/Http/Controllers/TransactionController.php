<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Models\Transaction;
use App\Models\Customer;
use App\Models\Supplier;
use App\Models\WalletBatch;
use Illuminate\Support\Facades\Validator;

class TransactionController extends Controller
{
    use AuthorizeAccountContext;

    private function timestamp($value): int { $ts = (int) ($value ?? time()); if ($ts > 2000000000) $ts = (int) floor($ts / 1000); return ($ts > 0 && $ts <= time() + 86400) ? $ts : time(); }

    private function decimal(mixed $value, int $scale, int $integerDigits): string
    {
        if ($value === null || $value === '') return '0.' . str_repeat('0', $scale);
        $raw = trim((string) $value);
        if (!preg_match('/^(?:0|[0-9]+)(?:\.[0-9]+)?$/', $raw)) throw new \InvalidArgumentException('Invalid decimal value.');
        [$whole, $fraction] = array_pad(explode('.', $raw, 2), 2, ''); $whole = ltrim($whole, '0') ?: '0';
        if (strlen($whole) > $integerDigits || strlen($fraction) > $scale) throw new \InvalidArgumentException('Decimal value is out of range or has excessive precision.');
        return $whole . '.' . str_pad($fraction, $scale, '0');
    }

    private function validateAccountRelationships(array $input, int $accountId)
    {
        foreach (['customer_id' => Customer::class, 'supplier_id' => Supplier::class, 'wallet_batch_id' => WalletBatch::class] as $field => $model) {
            if (!array_key_exists($field, $input) || (int) $input[$field] === 0) continue;
            if (!$model::query()->where('id', (int) $input[$field])->where('account_id', $accountId)->exists()) return response()->json(['status' => 'error', 'message' => "Invalid {$field}: record does not belong to the active account."], 422);
        }
        return null;
    }

    public function index(Request $request)
    {
        $context = $this->resolveAuthorizedAccountContext($request); if (isset($context['error'])) return $context['error'];
        return response()->json(['status' => 'success', 'transactions' => Transaction::where('account_id', $context['account_id'])->whereNull('deleted_at')->orderByDesc('timestamp')->orderByDesc('id')->get()]);
    }

    public function store(Request $request)
    {
        $context = $this->resolveAuthorizedAccountContext($request); if (isset($context['error'])) return $context['error'];
        $validator = Validator::make($request->all(), ['amount_sar' => 'nullable|string', 'amount' => 'nullable|string', 'customer_id' => 'nullable|integer|min:0', 'supplier_id' => 'nullable|integer|min:0', 'customer_rate' => 'nullable|string', 'supplier_rate' => 'nullable|string', 'amount_bdt' => 'nullable|string', 'receiver_name' => 'nullable|string|max:255', 'receiver_phone' => 'nullable|string|max:50', 'receiver_account_type' => 'nullable|string|max:50', 'receiver_account_no' => 'nullable|string|max:100', 'wallet_batch_id' => 'nullable|integer|min:0', 'notes' => 'nullable|string', 'local_id' => 'nullable|integer|min:1', 'timestamp' => 'nullable|integer|min:1', 'type' => 'nullable|string|max:20', 'hash' => 'nullable|string|max:255']);
        if ($validator->fails()) return response()->json(['status' => 'error', 'errors' => $validator->errors()], 422);
        if ($relationshipError = $this->validateAccountRelationships($request->all(), (int) $context['account_id'])) return $relationshipError;
        try {
            $amountSar = $this->decimal($request->input('amount_sar') ?? $request->input('amount') ?? 0, 2, 13); $localId = (int) ($request->input('local_id') ?: floor(microtime(true) * 1000));
            $transaction = Transaction::withTrashed()->updateOrCreate(['account_id' => $context['account_id'], 'local_id' => $localId], ['type' => substr((string) $request->input('type', 'Pending'), 0, 20), 'amount' => $amountSar, 'amount_sar' => $amountSar, 'customer_id' => (int) ($request->input('customer_id') ?: 0), 'supplier_id' => (int) ($request->input('supplier_id') ?: 0), 'customer_rate' => $this->decimal($request->input('customer_rate') ?: 0, 4, 6), 'supplier_rate' => $this->decimal($request->input('supplier_rate') ?: 0, 4, 6), 'amount_bdt' => $this->decimal($request->input('amount_bdt') ?: 0, 2, 13), 'receiver_name' => substr((string) $request->input('receiver_name', ''), 0, 255), 'receiver_phone' => substr((string) $request->input('receiver_phone', ''), 0, 50), 'receiver_account_type' => substr((string) $request->input('receiver_account_type', ''), 0, 50), 'receiver_account_no' => substr((string) $request->input('receiver_account_no', ''), 0, 100), 'wallet_batch_id' => (int) ($request->input('wallet_batch_id') ?: 0), 'notes' => $request->input('notes'), 'timestamp' => $this->timestamp($request->input('timestamp')), 'hash' => $request->input('hash'), 'deleted_at' => null]);
            if ($transaction->trashed()) $transaction->restore();
            return response()->json(['status' => 'success', 'message' => 'Transaction saved successfully.', 'transaction' => $transaction, 'id' => (int) $transaction->id], 201);
        } catch (\Throwable $e) { return response()->json(['status' => 'error', 'message' => $e->getMessage()], 422); }
    }

    public function update(Request $request, $id)
    {
        $context = $this->resolveAuthorizedAccountContext($request); if (isset($context['error'])) return $context['error'];
        $transaction = Transaction::withTrashed()->where('account_id', $context['account_id'])->where(fn ($q) => $q->where('id', (int) $id)->orWhere('local_id', (int) $id))->first(); if (!$transaction) return response()->json(['status' => 'error', 'message' => 'Transaction not found.'], 404);
        $validator = Validator::make($request->all(), ['type' => 'nullable|string|max:20', 'customer_id' => 'nullable|integer|min:0', 'supplier_id' => 'nullable|integer|min:0', 'amount_sar' => 'nullable|string', 'amount' => 'nullable|string', 'customer_rate' => 'nullable|string', 'supplier_rate' => 'nullable|string', 'amount_bdt' => 'nullable|string', 'receiver_name' => 'nullable|string|max:255', 'receiver_phone' => 'nullable|string|max:50', 'receiver_account_type' => 'nullable|string|max:50', 'receiver_account_no' => 'nullable|string|max:100', 'wallet_batch_id' => 'nullable|integer|min:0', 'notes' => 'nullable|string', 'timestamp' => 'nullable|integer|min:1', 'hash' => 'nullable|string|max:255']);
        if ($validator->fails()) return response()->json(['status' => 'error', 'errors' => $validator->errors()], 422); if ($relationshipError = $this->validateAccountRelationships($request->all(), (int) $context['account_id'])) return $relationshipError;
        try {
            foreach (['type','receiver_name','receiver_phone','receiver_account_type','receiver_account_no','notes','hash'] as $field) if ($request->has($field)) $transaction->{$field} = $request->input($field);
            foreach (['customer_id','supplier_id','wallet_batch_id'] as $field) if ($request->has($field)) $transaction->{$field} = (int) ($request->input($field) ?: 0);
            foreach (['amount_sar','customer_rate','supplier_rate','amount_bdt'] as $field) if ($request->has($field)) $transaction->{$field} = $this->decimal($request->input($field) ?: 0, in_array($field, ['amount_sar','amount_bdt'], true) ? 2 : 4, in_array($field, ['amount_sar','amount_bdt'], true) ? 13 : 6);
            if ($request->has('amount') && !$request->has('amount_sar')) $transaction->amount_sar = $this->decimal($request->input('amount') ?: 0, 2, 13); $transaction->amount = $transaction->amount_sar;
            if ($request->has('timestamp')) $transaction->timestamp = $this->timestamp($request->input('timestamp')); $transaction->deleted_at = null; $transaction->save();
            return response()->json(['status' => 'success', 'message' => 'Transaction updated successfully.', 'transaction' => $transaction]);
        } catch (\Throwable $e) { return response()->json(['status' => 'error', 'message' => $e->getMessage()], 422); }
    }

    public function destroy(Request $request, $id)
    {
        $context = $this->resolveAuthorizedAccountContext($request); if (isset($context['error'])) return $context['error']; if (!$request->boolean('confirmed')) return response()->json(['status' => 'confirmation_required', 'message' => 'Confirmation required before deleting transaction.', 'requires_confirmation' => true], 409);
        $transaction = Transaction::where('account_id', $context['account_id'])->where(fn ($q) => $q->where('id', (int) $id)->orWhere('local_id', (int) $id))->first(); if (!$transaction) return response()->json(['status' => 'error', 'message' => 'Transaction not found.'], 404); $transaction->delete();
        return response()->json(['status' => 'success', 'message' => 'Transaction deleted successfully.', 'id' => (int) $transaction->id]);
    }
}
