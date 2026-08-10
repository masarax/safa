<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Models\Transaction;
use App\Models\SafaApiKey;
use App\Models\Account;
use Illuminate\Support\Facades\Validator;

class TransactionController extends Controller
{
    use AuthorizeAccountContext;

    public function index(Request $request)
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];
        $accountId = $context['account_id'];

        $transactions = Transaction::where('account_id', $accountId)
            ->whereNull('deleted_at')
            ->orderBy('id', 'desc')
            ->get();

        return response()->json([
            'status' => 'success',
            'transactions' => $transactions
        ]);
    }

    public function store(Request $request)
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];
        $accountId = $context['account_id'];

        $validator = Validator::make($request->all(), [
            'amount_sar' => 'nullable|numeric',
            'amount' => 'nullable|numeric',
            'customer_id' => 'nullable|integer',
            'supplier_id' => 'nullable|integer',
            'local_id' => 'nullable|integer',
        ]);

        if ($validator->fails()) {
            return response()->json(['status' => 'error', 'errors' => $validator->errors()], 422);
        }

        $localId = (int) ($request->input('local_id') ?? 0);
        $amountSar = (float) ($request->input('amount_sar') ?? $request->input('amount') ?? 0.0);
        $transaction = Transaction::updateOrCreate(
            ['account_id' => $accountId, 'local_id' => $localId > 0 ? $localId : time()],
            [
                'type' => substr((string) ($request->input('type') ?? 'Pending'), 0, 20),
                'amount' => $amountSar,
                'amount_sar' => $amountSar,
                'customer_id' => (int) ($request->input('customer_id') ?? 0),
                'supplier_id' => (int) ($request->input('supplier_id') ?? 0),
                'customer_rate' => (float) ($request->input('customer_rate') ?? 0.0),
                'supplier_rate' => (float) ($request->input('supplier_rate') ?? 0.0),
                'amount_bdt' => (float) ($request->input('amount_bdt') ?? 0.0),
                'receiver_name' => substr((string) ($request->input('receiver_name') ?? ''), 0, 255),
                'receiver_phone' => substr((string) ($request->input('receiver_phone') ?? ''), 0, 50),
                'receiver_account_type' => substr((string) ($request->input('receiver_account_type') ?? ''), 0, 50),
                'receiver_account_no' => substr((string) ($request->input('receiver_account_no') ?? ''), 0, 100),
                'wallet_batch_id' => (int) ($request->input('wallet_batch_id') ?? 0),
                'notes' => $request->input('notes'),
                'timestamp' => time(),
            ]
        );

        return response()->json([
            'status' => 'success',
            'message' => 'Transaction recorded successfully.',
            'transaction' => $transaction
        ], 201);
    }

    public function update(Request $request, $id)
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];
        $accountId = $context['account_id'];

        $transaction = Transaction::where('account_id', $accountId)
            ->where(function ($q) use ($id) {
                $q->where('id', (int) $id)->orWhere('local_id', (int) $id);
            })
            ->first();

        if (!$transaction) {
            return response()->json(['status' => 'error', 'message' => 'Transaction not found.'], 404);
        }

        if ($request->has('type')) $transaction->type = substr((string) $request->input('type'), 0, 20);
        if ($request->has('amount_sar')) $transaction->amount_sar = (float) $request->input('amount_sar');
        if ($request->has('customer_rate')) $transaction->customer_rate = (float) $request->input('customer_rate');
        if ($request->has('supplier_rate')) $transaction->supplier_rate = (float) $request->input('supplier_rate');
        if ($request->has('amount_bdt')) $transaction->amount_bdt = (float) $request->input('amount_bdt');
        if ($request->has('notes')) $transaction->notes = $request->input('notes');
        $transaction->save();

        return response()->json([
            'status' => 'success',
            'message' => 'Transaction updated successfully.',
            'transaction' => $transaction
        ]);
    }

    public function destroy(Request $request, $id)
    {
        $context = $this->resolveAuthorizedAccountContext($request);
        if (isset($context['error'])) return $context['error'];
        $accountId = $context['account_id'];

        $transaction = Transaction::where('account_id', $accountId)
            ->where(function ($q) use ($id) {
                $q->where('id', (int) $id)->orWhere('local_id', (int) $id);
            })
            ->first();

        if (!$transaction) {
            return response()->json(['status' => 'error', 'message' => 'Transaction not found.'], 404);
        }

        $transaction->delete();

        return response()->json([
            'status' => 'success',
            'message' => 'Transaction deleted successfully.'
        ]);
    }
}
