<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use App\Models\Transaction;
use App\Models\Customer;
use App\Models\Supplier;
use App\Models\WalletBatch;
use App\Models\WalletLedger;
use App\Models\SupplierDeposit;
use App\Models\ExpenseIncome;
use App\Models\SafaApiKey;
use App\Models\Account;
use Illuminate\Support\Facades\Validator;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Log;

class SyncController extends Controller
{
    public function syncUp(Request $request)
    {
        try {
            $apiKey  = $request->header('X-SAFA-API-KEY');
            $keyRecord = SafaApiKey::where('api_key', $apiKey)
                ->where('is_active', true)
                ->first();

            $accountId = $keyRecord?->account_id;
            if (!$accountId) {
                $envApiKey = env('SAFA_API_KEY');
                if ($envApiKey && hash_equals($envApiKey, (string) $apiKey)) {
                    $defaultAccount = Account::first();
                    $accountId = $defaultAccount?->id ?? 1;
                }
            }

            if (!$accountId) {
                return response()->json(['message' => 'Unauthorized. Account not found.'], 401);
            }

            $validator = Validator::make($request->all(), [
                'transactions'      => 'nullable|array',
                'customers'         => 'nullable|array',
                'suppliers'         => 'nullable|array',
                'wallet_batches'    => 'nullable|array',
                'supplier_deposits' => 'nullable|array',
                'expenses_incomes'  => 'nullable|array',
                'wallet_ledgers'    => 'nullable|array',
            ]);

            if ($validator->fails()) {
                return response()->json(['message' => 'Invalid data format.', 'errors' => $validator->errors()], 422);
            }

            $data = $request->only([
                'transactions',
                'customers',
                'suppliers',
                'wallet_batches',
                'supplier_deposits',
                'expenses_incomes',
                'wallet_ledgers'
            ]);

            $parseDeletedAt = function ($raw) {
                if (empty($raw)) return null;
                if (is_numeric($raw)) {
                    $timestamp = (int) $raw;
                    if ($timestamp > 2000000000) {
                        $timestamp = (int) ($timestamp / 1000);
                    }
                    return date('Y-m-d H:i:s', $timestamp);
                }
                return (string) $raw;
            };

            DB::transaction(function () use ($data, $accountId, $parseDeletedAt) {
                // 1. Transactions Sync
                if (isset($data['transactions']) && is_array($data['transactions'])) {
                    foreach ($data['transactions'] as $tx) {
                        if (empty($tx['local_id'])) continue;

                        $existing = Transaction::withTrashed()
                            ->where('account_id', $accountId)
                            ->where('local_id', (int) $tx['local_id'])
                            ->first();

                        if ($existing && isset($tx['timestamp']) && $existing->timestamp > (int) $tx['timestamp']) {
                            continue;
                        }

                        $isDeleted = !empty($tx['deleted_at']) || !empty($tx['is_deleted']);
                        $deletedAtValue = $isDeleted ? ($parseDeletedAt($tx['deleted_at'] ?? null) ?? now()) : null;

                        $record = Transaction::withTrashed()->updateOrCreate(
                            ['account_id' => $accountId, 'local_id' => (int) $tx['local_id']],
                            [
                                'type'                  => substr((string) ($tx['type'] ?? 'Pending'), 0, 20),
                                'amount'                => (float) ($tx['amount'] ?? 0),
                                'customer_id'           => (int) ($tx['customer_id'] ?? 0),
                                'supplier_id'           => (int) ($tx['supplier_id'] ?? 0),
                                'amount_sar'            => (float) ($tx['amount_sar'] ?? $tx['amount'] ?? 0),
                                'customer_rate'         => (float) ($tx['customer_rate'] ?? 0),
                                'supplier_rate'         => (float) ($tx['supplier_rate'] ?? 0),
                                'amount_bdt'            => (float) ($tx['amount_bdt'] ?? 0),
                                'receiver_name'         => substr((string) ($tx['receiver_name'] ?? ''), 0, 255),
                                'receiver_phone'        => substr((string) ($tx['receiver_phone'] ?? ''), 0, 50),
                                'receiver_account_type' => substr((string) ($tx['receiver_account_type'] ?? ''), 0, 50),
                                'receiver_account_no'   => substr((string) ($tx['receiver_account_no'] ?? ''), 0, 100),
                                'wallet_batch_id'       => (int) ($tx['wallet_batch_id'] ?? 0),
                                'notes'                 => $tx['notes'] ?? null,
                                'hash'                  => $tx['hash'] ?? null,
                                'timestamp'             => (int) ($tx['timestamp'] ?? time()),
                            ]
                        );

                        if ($isDeleted) {
                            $record->deleted_at = $deletedAtValue;
                            $record->save();
                        } else {
                            if ($record->trashed()) {
                                $record->restore();
                            }
                            $record->deleted_at = null;
                            $record->save();
                        }
                    }
                }

                // 2. Customers Sync
                if (isset($data['customers']) && is_array($data['customers'])) {
                    foreach ($data['customers'] as $c) {
                        if (empty($c['local_id']) || empty($c['name'])) continue;

                        $existing = Customer::withTrashed()
                            ->where('account_id', $accountId)
                            ->where('local_id', (int) $c['local_id'])
                            ->first();

                        if ($existing && isset($c['timestamp']) && $existing->timestamp > (int) $c['timestamp']) {
                            continue;
                        }

                        $isDeleted = !empty($c['deleted_at']) || !empty($c['is_deleted']);
                        $deletedAtValue = $isDeleted ? ($parseDeletedAt($c['deleted_at'] ?? null) ?? now()) : null;

                        $record = Customer::withTrashed()->updateOrCreate(
                            ['account_id' => $accountId, 'local_id' => (int) $c['local_id']],
                            [
                                'name'      => substr((string) $c['name'], 0, 255),
                                'phone'     => substr((string) ($c['phone'] ?? ''), 0, 50),
                                'timestamp' => (int) ($c['timestamp'] ?? time()),
                            ]
                        );

                        if ($isDeleted) {
                            $record->deleted_at = $deletedAtValue;
                            $record->save();
                        } else {
                            if ($record->trashed()) {
                                $record->restore();
                            }
                            $record->deleted_at = null;
                            $record->save();
                        }
                    }
                }

                // 3. Suppliers Sync
                if (isset($data['suppliers']) && is_array($data['suppliers'])) {
                    foreach ($data['suppliers'] as $s) {
                        if (empty($s['local_id']) || empty($s['name'])) continue;

                        $existing = Supplier::withTrashed()
                            ->where('account_id', $accountId)
                            ->where('local_id', (int) $s['local_id'])
                            ->first();

                        if ($existing && isset($s['timestamp']) && $existing->timestamp > (int) $s['timestamp']) {
                            continue;
                        }

                        $isDeleted = !empty($s['deleted_at']) || !empty($s['is_deleted']);
                        $deletedAtValue = $isDeleted ? ($parseDeletedAt($s['deleted_at'] ?? null) ?? now()) : null;

                        $record = Supplier::withTrashed()->updateOrCreate(
                            ['account_id' => $accountId, 'local_id' => (int) $s['local_id']],
                            [
                                'name'      => substr((string) $s['name'], 0, 255),
                                'phone'     => substr((string) ($s['phone'] ?? ''), 0, 50),
                                'timestamp' => (int) ($s['timestamp'] ?? time()),
                            ]
                        );

                        if ($isDeleted) {
                            $record->deleted_at = $deletedAtValue;
                            $record->save();
                        } else {
                            if ($record->trashed()) {
                                $record->restore();
                            }
                            $record->deleted_at = null;
                            $record->save();
                        }
                    }
                }

                // 4. Wallet Batches Sync
                if (isset($data['wallet_batches']) && is_array($data['wallet_batches'])) {
                    foreach ($data['wallet_batches'] as $b) {
                        if (empty($b['local_id'])) continue;

                        $existing = WalletBatch::withTrashed()
                            ->where('account_id', $accountId)
                            ->where('local_id', (int) $b['local_id'])
                            ->first();

                        if ($existing && isset($b['timestamp']) && $existing->timestamp > (int) $b['timestamp']) {
                            continue;
                        }

                        $isDeleted = !empty($b['deleted_at']) || !empty($b['is_deleted']);
                        $deletedAtValue = $isDeleted ? ($parseDeletedAt($b['deleted_at'] ?? null) ?? now()) : null;

                        $record = WalletBatch::withTrashed()->updateOrCreate(
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

                        if ($isDeleted) {
                            $record->deleted_at = $deletedAtValue;
                            $record->save();
                        } else {
                            if ($record->trashed()) {
                                $record->restore();
                            }
                            $record->deleted_at = null;
                            $record->save();
                        }
                    }
                }

                // 5. Wallet Ledgers Sync
                if (isset($data['wallet_ledgers']) && is_array($data['wallet_ledgers'])) {
                    foreach ($data['wallet_ledgers'] as $wl) {
                        if (empty($wl['local_id'])) continue;

                        $existing = WalletLedger::withTrashed()
                            ->where('account_id', $accountId)
                            ->where('local_id', (int) $wl['local_id'])
                            ->first();

                        if ($existing && isset($wl['timestamp']) && $existing->timestamp > (int) $wl['timestamp']) {
                            continue;
                        }

                        $isDeleted = !empty($wl['deleted_at']) || !empty($wl['is_deleted']);
                        $deletedAtValue = $isDeleted ? ($parseDeletedAt($wl['deleted_at'] ?? null) ?? now()) : null;

                        $record = WalletLedger::withTrashed()->updateOrCreate(
                            ['account_id' => $accountId, 'local_id' => (int) $wl['local_id']],
                            [
                                'name'      => substr((string) ($wl['name'] ?? ''), 0, 255),
                                'timestamp' => (int) ($wl['timestamp'] ?? time()),
                            ]
                        );

                        if ($isDeleted) {
                            $record->deleted_at = $deletedAtValue;
                            $record->save();
                        } else {
                            if ($record->trashed()) {
                                $record->restore();
                            }
                            $record->deleted_at = null;
                            $record->save();
                        }
                    }
                }

                // 6. Supplier Deposits Sync
                if (isset($data['supplier_deposits']) && is_array($data['supplier_deposits'])) {
                    foreach ($data['supplier_deposits'] as $sd) {
                        if (empty($sd['local_id'])) continue;

                        $existing = SupplierDeposit::withTrashed()
                            ->where('account_id', $accountId)
                            ->where('local_id', (int) $sd['local_id'])
                            ->first();

                        if ($existing && isset($sd['timestamp']) && $existing->timestamp > (int) $sd['timestamp']) {
                            continue;
                        }

                        $isDeleted = !empty($sd['deleted_at']) || !empty($sd['is_deleted']);
                        $deletedAtValue = $isDeleted ? ($parseDeletedAt($sd['deleted_at'] ?? null) ?? now()) : null;

                        $record = SupplierDeposit::withTrashed()->updateOrCreate(
                            ['account_id' => $accountId, 'local_id' => (int) $sd['local_id']],
                            [
                                'supplier_id'      => (int) ($sd['supplier_id'] ?? 0),
                                'amount_sar'       => (float) ($sd['amount_sar'] ?? 0),
                                'rate'             => (float) ($sd['rate'] ?? 0),
                                'amount_bdt'       => (float) ($sd['amount_bdt'] ?? 0),
                                'paid_bdt'         => (float) ($sd['paid_bdt'] ?? 0),
                                'transaction_type' => substr((string) ($sd['transaction_type'] ?? 'SAR_GIVEN'), 0, 50),
                                'notes'            => $sd['notes'] ?? null,
                                'timestamp'        => (int) ($sd['timestamp'] ?? time()),
                            ]
                        );

                        if ($isDeleted) {
                            $record->deleted_at = $deletedAtValue;
                            $record->save();
                        } else {
                            if ($record->trashed()) {
                                $record->restore();
                            }
                            $record->deleted_at = null;
                            $record->save();
                        }
                    }
                }

                // 7. Expenses & Incomes Sync
                if (isset($data['expenses_incomes']) && is_array($data['expenses_incomes'])) {
                    foreach ($data['expenses_incomes'] as $e) {
                        if (empty($e['local_id'])) continue;

                        $existing = ExpenseIncome::withTrashed()
                            ->where('account_id', $accountId)
                            ->where('local_id', (int) $e['local_id'])
                            ->first();

                        if ($existing && isset($e['timestamp']) && $existing->timestamp > (int) $e['timestamp']) {
                            continue;
                        }

                        $isDeleted = !empty($e['deleted_at']) || !empty($e['is_deleted']);
                        $deletedAtValue = $isDeleted ? ($parseDeletedAt($e['deleted_at'] ?? null) ?? now()) : null;

                        $record = ExpenseIncome::withTrashed()->updateOrCreate(
                            ['account_id' => $accountId, 'local_id' => (int) $e['local_id']],
                            [
                                'title'      => substr((string) ($e['title'] ?? 'General'), 0, 255),
                                'amount'     => (float) ($e['amount'] ?? 0),
                                'currency'   => substr((string) ($e['currency'] ?? 'BDT'), 0, 10),
                                'is_expense' => (bool) ($e['is_expense'] ?? true),
                                'category'   => substr((string) ($e['category'] ?? 'General'), 0, 50),
                                'timestamp'  => (int) ($e['timestamp'] ?? time()),
                            ]
                        );

                        if ($isDeleted) {
                            $record->deleted_at = $deletedAtValue;
                            $record->save();
                        } else {
                            if ($record->trashed()) {
                                $record->restore();
                            }
                            $record->deleted_at = null;
                            $record->save();
                        }
                    }
                }
            });

            return response()->json(['status' => 'success']);
        } catch (\Throwable $e) {
            Log::error("SyncUp failed: " . $e->getMessage());
            return response()->json([
                'message' => 'Sync failed.',
                'error'   => config('app.debug') ? $e->getMessage() : 'Server Error'
            ], 500);
        }
    }

    public function syncDown(Request $request)
    {
        try {
            $apiKey    = $request->header('X-SAFA-API-KEY');
            $keyRecord = SafaApiKey::where('api_key', $apiKey)
                ->where('is_active', true)
                ->first();

            $accountId = $keyRecord?->account_id;
            if (!$accountId) {
                $envApiKey = env('SAFA_API_KEY');
                if ($envApiKey && hash_equals($envApiKey, (string) $apiKey)) {
                    $defaultAccount = Account::first();
                    $accountId = $defaultAccount?->id ?? 1;
                }
            }

            if (!$accountId) {
                return response()->json(['message' => 'Unauthorized. Account not found.'], 401);
            }

            return response()->json([
                'transactions'      => Transaction::withTrashed()->where('account_id', $accountId)->get(),
                'customers'         => Customer::withTrashed()->where('account_id', $accountId)->get(),
                'suppliers'         => Supplier::withTrashed()->where('account_id', $accountId)->get(),
                'wallet_batches'    => WalletBatch::withTrashed()->where('account_id', $accountId)->get(),
                'wallet_ledgers'    => WalletLedger::withTrashed()->where('account_id', $accountId)->get(),
                'supplier_deposits' => SupplierDeposit::withTrashed()->where('account_id', $accountId)->get(),
                'expenses_incomes'  => ExpenseIncome::withTrashed()->where('account_id', $accountId)->get(),
            ]);
        } catch (\Throwable $e) {
            Log::error("SyncDown failed: " . $e->getMessage());
            return response()->json([
                'message' => 'Failed to fetch sync data.',
                'error'   => config('app.debug') ? $e->getMessage() : 'Server Error'
            ], 500);
        }
    }
}

