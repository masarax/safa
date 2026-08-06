<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Models\Transaction;
use App\Models\Customer;
use App\Models\Supplier;
use Illuminate\Support\Facades\Validator;
use Illuminate\Support\Facades\DB;

use App\Models\WalletBatch;
use App\Models\WalletLedger;
use App\Models\SupplierDeposit;
use App\Models\ExpenseIncome;

class SyncController extends Controller
{
    public function syncUp(Request $request)
    {
        $apiKey  = $request->header('X-SAFA-API-KEY');
        $keyRecord = \App\Models\SafaApiKey::where('api_key', $apiKey)
            ->where('is_active', true)
            ->first();

        if (! $keyRecord || ! isset($keyRecord->account_id)) {
            return response()->json(['message' => 'Unauthorized.'], 401);
        }

        $accountId = (int) $keyRecord->account_id;
        $validator = Validator::make($request->all(), [
            'transactions' => 'nullable|array',
            'customers' => 'nullable|array',
            'suppliers' => 'nullable|array',
            'wallet_batches' => 'nullable|array',
            'supplier_deposits' => 'nullable|array',
            'expenses_incomes' => 'nullable|array',
            'wallet_ledgers' => 'nullable|array',
        ]);

        if ($validator->fails()) {
            return response()->json(['message' => 'Invalid data format.', 'errors' => $validator->errors()], 422);
        }

        $data = $request->only(['transactions', 'customers', 'suppliers', 'wallet_batches', 'supplier_deposits', 'expenses_incomes', 'wallet_ledgers']);

        DB::transaction(function () use ($data, $accountId) {
            // 1. Transactions Sync
            if (isset($data['transactions']) && is_array($data['transactions'])) {
                foreach ($data['transactions'] as $tx) {
                    if (empty($tx['local_id'])) continue;

                    $existing = Transaction::where('account_id', $accountId)
                        ->where('local_id', (int) $tx['local_id'])
                        ->first();

                    // Timestamp-based Last-Write-Wins Conflict Resolution
                    if ($existing && isset($tx['timestamp']) && $existing->timestamp > (int) $tx['timestamp']) {
                        continue; // Local record on server is newer
                    }

                    Transaction::updateOrCreate(
                        ['account_id' => $accountId, 'local_id' => (int) $tx['local_id']],
                        [
                            'type'                   => $tx['type'] ?? 'Pending',
                            'amount'                 => (float) ($tx['amount'] ?? 0),
                            'customer_id'            => (int) ($tx['customer_id'] ?? 0),
                            'supplier_id'            => (int) ($tx['supplier_id'] ?? 0),
                            'amount_sar'             => (float) ($tx['amount_sar'] ?? $tx['amount'] ?? 0),
                            'customer_rate'          => (float) ($tx['customer_rate'] ?? 0),
                            'supplier_rate'          => (float) ($tx['supplier_rate'] ?? 0),
                            'amount_bdt'             => (float) ($tx['amount_bdt'] ?? 0),
                            'receiver_name'          => substr((string) ($tx['receiver_name'] ?? ''), 0, 255),
                            'receiver_phone'         => substr((string) ($tx['receiver_phone'] ?? ''), 0, 50),
                            'receiver_account_type'  => substr((string) ($tx['receiver_account_type'] ?? ''), 0, 50),
                            'receiver_account_no'    => substr((string) ($tx['receiver_account_no'] ?? ''), 0, 100),
                            'wallet_batch_id'        => (int) ($tx['wallet_batch_id'] ?? 0),
                            'notes'                  => $tx['notes'] ?? null,
                            'hash'                   => $tx['hash'] ?? null,
                            'timestamp'              => (int) ($tx['timestamp'] ?? time()),
                        ]
                    );
                }
            }

            // 2. Customers Sync
            if (isset($data['customers']) && is_array($data['customers'])) {
                foreach ($data['customers'] as $c) {
                    if (empty($c['name'])) continue;
                    Customer::updateOrCreate(
                        ['account_id' => $accountId, 'local_id' => (int) ($c['local_id'] ?? 0)],
                        [
                            'name'       => substr((string) $c['name'], 0, 255),
                            'phone'      => substr((string) ($c['phone'] ?? ''), 0, 50),
                        ]
                    );
                }
            }

            // 3. Suppliers Sync
            if (isset($data['suppliers']) && is_array($data['suppliers'])) {
                foreach ($data['suppliers'] as $s) {
                    if (empty($s['name'])) continue;
                    Supplier::updateOrCreate(
                        ['account_id' => $accountId, 'local_id' => (int) ($s['local_id'] ?? 0)],
                        [
                            'name'       => substr((string) $s['name'], 0, 255),
                            'phone'      => substr((string) ($s['phone'] ?? ''), 0, 50),
                        ]
                    );
                }
            }

            // 4. Wallet Batches Sync
            if (isset($data['wallet_batches']) && is_array($data['wallet_batches'])) {
                foreach ($data['wallet_batches'] as $b) {
                    if (empty($b['local_id'])) continue;
                    WalletBatch::updateOrCreate(
                        ['account_id' => $accountId, 'local_id' => (int) $b['local_id']],
                        [
                            'ledger_id'           => (int) ($b['ledger_id'] ?? 0),
                            'rate'                => (float) ($b['rate'] ?? 0),
                            'initial_bdt'         => (float) ($b['initial_bdt'] ?? 0),
                            'remaining_bdt'       => (float) ($b['remaining_bdt'] ?? 0),
                            'supplier_id'         => (int) ($b['supplier_id'] ?? 0),
                            'supplier_deposit_id' => (int) ($b['supplier_deposit_id'] ?? 0),
                            'notes'               => $b['notes'] ?? null,
                            'timestamp'           => (int) ($b['timestamp'] ?? time()),
                        ]
                    );
                }
            }

            // 4.5 Wallet Ledgers Sync
            if (isset($data['wallet_ledgers']) && is_array($data['wallet_ledgers'])) {
                foreach ($data['wallet_ledgers'] as $wl) {
                    if (empty($wl['local_id'])) continue;
                    WalletLedger::updateOrCreate(
                        ['account_id' => $accountId, 'local_id' => (int) $wl['local_id']],
                        [
                            'name'       => substr((string) ($wl['name'] ?? ''), 0, 255),
                            'timestamp'  => (int) ($wl['timestamp'] ?? time()),
                        ]
                    );
                }
            }

            // 5. Supplier Deposits Sync
            if (isset($data['supplier_deposits']) && is_array($data['supplier_deposits'])) {
                foreach ($data['supplier_deposits'] as $sd) {
                    if (empty($sd['local_id'])) continue;
                    SupplierDeposit::updateOrCreate(
                        ['account_id' => $accountId, 'local_id' => (int) $sd['local_id']],
                        [
                            'supplier_id'      => (int) ($sd['supplier_id'] ?? 0),
                            'amount_sar'       => (float) ($sd['amount_sar'] ?? 0),
                            'rate'             => (float) ($sd['rate'] ?? 0),
                            'amount_bdt'       => (float) ($sd['amount_bdt'] ?? 0),
                            'paid_bdt'         => (float) ($sd['paid_bdt'] ?? 0),
                            'transaction_type' => $sd['transaction_type'] ?? 'SAR_GIVEN',
                            'notes'            => $sd['notes'] ?? null,
                            'timestamp'        => (int) ($sd['timestamp'] ?? time()),
                        ]
                    );
                }
            }

            // 6. Expenses & Incomes Sync
            if (isset($data['expenses_incomes']) && is_array($data['expenses_incomes'])) {
                foreach ($data['expenses_incomes'] as $e) {
                    if (empty($e['local_id'])) continue;
                    ExpenseIncome::updateOrCreate(
                        ['account_id' => $accountId, 'local_id' => (int) $e['local_id']],
                        [
                            'title'      => substr((string) ($e['title'] ?? 'General'), 0, 255),
                            'amount'     => (float) ($e['amount'] ?? 0),
                            'currency'   => $e['currency'] ?? 'BDT',
                            'is_expense' => (bool) ($e['is_expense'] ?? true),
                            'category'   => $e['category'] ?? 'General',
                            'timestamp'  => (int) ($e['timestamp'] ?? time()),
                        ]
                    );
                }
            }
        });

        return response()->json(['status' => 'success']);
    }

    public function syncDown(Request $request)
    {
        $apiKey    = $request->header('X-SAFA-API-KEY');
        $keyRecord = \App\Models\SafaApiKey::where('api_key', $apiKey)
            ->where('is_active', true)
            ->first();

        if (! $keyRecord || ! isset($keyRecord->account_id)) {
            return response()->json(['message' => 'Unauthorized.'], 401);
        }

        $accountId = (int) $keyRecord->account_id;

        return response()->json([
            'transactions'      => Transaction::where('account_id', $accountId)->get(),
            'customers'         => Customer::where('account_id', $accountId)->get(),
            'suppliers'         => Supplier::where('account_id', $accountId)->get(),
            'wallet_batches'    => WalletBatch::where('account_id', $accountId)->get(),
            'wallet_ledgers'    => WalletLedger::where('account_id', $accountId)->get(),
            'supplier_deposits' => SupplierDeposit::where('account_id', $accountId)->get(),
            'expenses_incomes'  => ExpenseIncome::where('account_id', $accountId)->get(),
        ]);
    }
}
