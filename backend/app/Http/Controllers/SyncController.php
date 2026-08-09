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

            $sanitizeTimestamp = function ($raw) {
                $ts = (int) ($raw ?? time());
                if ($ts <= 0) return time();
                if ($ts > 2000000000) {
                    $ts = (int) ($ts / 1000);
                }
                $maxAllowed = time() + 86400;
                if ($ts > $maxAllowed) {
                    return time();
                }
                return $ts;
            };

            $accepted = [
                'customers'         => [],
                'suppliers'         => [],
                'wallet_ledgers'    => [],
                'supplier_deposits' => [],
                'wallet_batches'    => [],
                'transactions'      => [],
                'expenses_incomes'  => [],
            ];
            $rejected = [];

            DB::transaction(function () use ($data, $accountId, $parseDeletedAt, $sanitizeTimestamp, &$accepted, &$rejected) {
                // Step 1: Customers Sync (Independent Root)
                if (isset($data['customers']) && is_array($data['customers'])) {
                    foreach ($data['customers'] as $c) {
                        if (empty($c['local_id']) || empty($c['name'])) {
                            $rejected[] = [
                                'entity' => 'customers',
                                'local_id' => (int) ($c['local_id'] ?? 0),
                                'reason' => 'Missing required fields (local_id or name)'
                            ];
                            continue;
                        }

                        $existing = Customer::withTrashed()
                            ->where('account_id', $accountId)
                            ->where('local_id', (int) $c['local_id'])
                            ->first();

                        if ($existing && isset($c['timestamp']) && (int)$existing->timestamp > (int)$c['timestamp']) {
                            $accepted['customers'][] = [
                                'local_id'  => (int) $c['local_id'],
                                'server_id' => $existing->id
                            ];
                            continue;
                        }

                        $isDeleted = !empty($c['deleted_at']) || !empty($c['is_deleted']);
                        $deletedAtValue = $isDeleted ? ($parseDeletedAt($c['deleted_at'] ?? null) ?? now()) : null;

                        $record = Customer::withTrashed()->updateOrCreate(
                            ['account_id' => $accountId, 'local_id' => (int) $c['local_id']],
                            [
                                'name'      => substr((string) $c['name'], 0, 255),
                                'phone'     => substr((string) ($c['phone'] ?? ''), 0, 50),
                                'timestamp' => $sanitizeTimestamp($c['timestamp'] ?? null),
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

                        $accepted['customers'][] = [
                            'local_id'  => (int) $c['local_id'],
                            'server_id' => $record->id
                        ];
                    }
                }

                // Step 2: Suppliers Sync (Independent Root)
                if (isset($data['suppliers']) && is_array($data['suppliers'])) {
                    foreach ($data['suppliers'] as $s) {
                        if (empty($s['local_id']) || empty($s['name'])) {
                            $rejected[] = [
                                'entity' => 'suppliers',
                                'local_id' => (int) ($s['local_id'] ?? 0),
                                'reason' => 'Missing required fields (local_id or name)'
                            ];
                            continue;
                        }

                        $existing = Supplier::withTrashed()
                            ->where('account_id', $accountId)
                            ->where('local_id', (int) $s['local_id'])
                            ->first();

                        if ($existing && isset($s['timestamp']) && (int)$existing->timestamp > (int)$s['timestamp']) {
                            $accepted['suppliers'][] = [
                                'local_id'  => (int) $s['local_id'],
                                'server_id' => $existing->id
                            ];
                            continue;
                        }

                        $isDeleted = !empty($s['deleted_at']) || !empty($s['is_deleted']);
                        $deletedAtValue = $isDeleted ? ($parseDeletedAt($s['deleted_at'] ?? null) ?? now()) : null;

                        $record = Supplier::withTrashed()->updateOrCreate(
                            ['account_id' => $accountId, 'local_id' => (int) $s['local_id']],
                            [
                                'name'      => substr((string) $s['name'], 0, 255),
                                'phone'     => substr((string) ($s['phone'] ?? ''), 0, 50),
                                'timestamp' => $sanitizeTimestamp($s['timestamp'] ?? null),
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

                        $accepted['suppliers'][] = [
                            'local_id'  => (int) $s['local_id'],
                            'server_id' => $record->id
                        ];
                    }
                }

                // Step 3: Wallet Ledgers Sync (Independent Root)
                if (isset($data['wallet_ledgers']) && is_array($data['wallet_ledgers'])) {
                    foreach ($data['wallet_ledgers'] as $wl) {
                        if (empty($wl['local_id'])) {
                            $rejected[] = [
                                'entity' => 'wallet_ledgers',
                                'local_id' => 0,
                                'reason' => 'Missing local_id'
                            ];
                            continue;
                        }

                        $existing = WalletLedger::withTrashed()
                            ->where('account_id', $accountId)
                            ->where('local_id', (int) $wl['local_id'])
                            ->first();

                        if ($existing && isset($wl['timestamp']) && (int)$existing->timestamp > (int)$wl['timestamp']) {
                            $accepted['wallet_ledgers'][] = [
                                'local_id'  => (int) $wl['local_id'],
                                'server_id' => $existing->id
                            ];
                            continue;
                        }

                        $isDeleted = !empty($wl['deleted_at']) || !empty($wl['is_deleted']);
                        $deletedAtValue = $isDeleted ? ($parseDeletedAt($wl['deleted_at'] ?? null) ?? now()) : null;

                        $record = WalletLedger::withTrashed()->updateOrCreate(
                            ['account_id' => $accountId, 'local_id' => (int) $wl['local_id']],
                            [
                                'name'      => substr((string) ($wl['name'] ?? ''), 0, 255),
                                'timestamp' => $sanitizeTimestamp($wl['timestamp'] ?? null),
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

                        $accepted['wallet_ledgers'][] = [
                            'local_id'  => (int) $wl['local_id'],
                            'server_id' => $record->id
                        ];
                    }
                }

                // Step 4: Supplier Deposits Sync (Depends on Supplier)
                if (isset($data['supplier_deposits']) && is_array($data['supplier_deposits'])) {
                    foreach ($data['supplier_deposits'] as $sd) {
                        if (empty($sd['local_id'])) {
                            $rejected[] = [
                                'entity' => 'supplier_deposits',
                                'local_id' => 0,
                                'reason' => 'Missing local_id'
                            ];
                            continue;
                        }

                        $existing = SupplierDeposit::withTrashed()
                            ->where('account_id', $accountId)
                            ->where('local_id', (int) $sd['local_id'])
                            ->first();

                        if ($existing && isset($sd['timestamp']) && (int)$existing->timestamp > (int)$sd['timestamp']) {
                            $accepted['supplier_deposits'][] = [
                                'local_id'  => (int) $sd['local_id'],
                                'server_id' => $existing->id
                            ];
                            continue;
                        }

                        // Resolve local_id to server primary key for supplier_id
                        $rawSupplierId = (int) ($sd['supplier_id'] ?? 0);
                        $serverSupplierId = 0;
                        if ($rawSupplierId > 0) {
                            $serverSupplierId = Supplier::where('account_id', $accountId)
                                ->where('local_id', $rawSupplierId)
                                ->value('id') ?? $rawSupplierId;
                        }

                        $isDeleted = !empty($sd['deleted_at']) || !empty($sd['is_deleted']);
                        $deletedAtValue = $isDeleted ? ($parseDeletedAt($sd['deleted_at'] ?? null) ?? now()) : null;

                        $record = SupplierDeposit::withTrashed()->updateOrCreate(
                            ['account_id' => $accountId, 'local_id' => (int) $sd['local_id']],
                            [
                                'supplier_id'      => $serverSupplierId,
                                'amount_sar'       => (float) ($sd['amount_sar'] ?? 0),
                                'rate'             => (float) ($sd['rate'] ?? 0),
                                'amount_bdt'       => (float) ($sd['amount_bdt'] ?? 0),
                                'paid_bdt'         => (float) ($sd['paid_bdt'] ?? 0),
                                'transaction_type' => substr((string) ($sd['transaction_type'] ?? 'SAR_GIVEN'), 0, 50),
                                'notes'            => $sd['notes'] ?? null,
                                'timestamp'        => $sanitizeTimestamp($sd['timestamp'] ?? null),
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

                        $accepted['supplier_deposits'][] = [
                            'local_id'  => (int) $sd['local_id'],
                            'server_id' => $record->id
                        ];
                    }
                }

                // Step 5: Wallet Batches Sync (Depends on WalletLedger, Supplier, SupplierDeposit)
                if (isset($data['wallet_batches']) && is_array($data['wallet_batches'])) {
                    foreach ($data['wallet_batches'] as $b) {
                        if (empty($b['local_id'])) {
                            $rejected[] = [
                                'entity' => 'wallet_batches',
                                'local_id' => 0,
                                'reason' => 'Missing local_id'
                            ];
                            continue;
                        }

                        $existing = WalletBatch::withTrashed()
                            ->where('account_id', $accountId)
                            ->where('local_id', (int) $b['local_id'])
                            ->first();

                        if ($existing && isset($b['timestamp']) && (int)$existing->timestamp > (int)$b['timestamp']) {
                            $accepted['wallet_batches'][] = [
                                'local_id'  => (int) $b['local_id'],
                                'server_id' => $existing->id
                            ];
                            continue;
                        }

                        // Resolve local IDs to server primary keys
                        $rawLedgerId = (int) ($b['ledger_id'] ?? 0);
                        $serverLedgerId = $rawLedgerId > 0 ? (WalletLedger::where('account_id', $accountId)->where('local_id', $rawLedgerId)->value('id') ?? $rawLedgerId) : 0;

                        $rawSupplierId = (int) ($b['supplier_id'] ?? 0);
                        $serverSupplierId = $rawSupplierId > 0 ? (Supplier::where('account_id', $accountId)->where('local_id', $rawSupplierId)->value('id') ?? $rawSupplierId) : 0;

                        $rawDepositId = (int) ($b['supplier_deposit_id'] ?? 0);
                        $serverDepositId = $rawDepositId > 0 ? (SupplierDeposit::where('account_id', $accountId)->where('local_id', $rawDepositId)->value('id') ?? $rawDepositId) : 0;

                        $isDeleted = !empty($b['deleted_at']) || !empty($b['is_deleted']);
                        $deletedAtValue = $isDeleted ? ($parseDeletedAt($b['deleted_at'] ?? null) ?? now()) : null;

                        $record = WalletBatch::withTrashed()->updateOrCreate(
                            ['account_id' => $accountId, 'local_id' => (int) $b['local_id']],
                            [
                                'ledger_id'           => $serverLedgerId,
                                'rate'                => (float) ($b['rate'] ?? 0),
                                'initial_bdt'         => (float) ($b['initial_bdt'] ?? 0),
                                'remaining_bdt'       => (float) ($b['remaining_bdt'] ?? 0),
                                'supplier_id'         => $serverSupplierId,
                                'supplier_deposit_id' => $serverDepositId,
                                'notes'               => $b['notes'] ?? null,
                                'timestamp'           => $sanitizeTimestamp($b['timestamp'] ?? null),
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

                        $accepted['wallet_batches'][] = [
                            'local_id'  => (int) $b['local_id'],
                            'server_id' => $record->id
                        ];
                    }
                }

                // Step 6: Transactions Sync (Depends on Customer, Supplier, WalletBatch)
                if (isset($data['transactions']) && is_array($data['transactions'])) {
                    foreach ($data['transactions'] as $tx) {
                        if (empty($tx['local_id'])) {
                            $rejected[] = [
                                'entity' => 'transactions',
                                'local_id' => 0,
                                'reason' => 'Missing local_id'
                            ];
                            continue;
                        }

                        $existing = Transaction::withTrashed()
                            ->where('account_id', $accountId)
                            ->where('local_id', (int) $tx['local_id'])
                            ->first();

                        if ($existing && isset($tx['timestamp']) && (int)$existing->timestamp > (int)$tx['timestamp']) {
                            $accepted['transactions'][] = [
                                'local_id'  => (int) $tx['local_id'],
                                'server_id' => $existing->id
                            ];
                            continue;
                        }

                        // Resolve local IDs to server primary keys
                        $rawCustomerId = (int) ($tx['customer_id'] ?? 0);
                        $serverCustomerId = $rawCustomerId > 0 ? (Customer::where('account_id', $accountId)->where('local_id', $rawCustomerId)->value('id') ?? $rawCustomerId) : 0;

                        $rawSupplierId = (int) ($tx['supplier_id'] ?? 0);
                        $serverSupplierId = $rawSupplierId > 0 ? (Supplier::where('account_id', $accountId)->where('local_id', $rawSupplierId)->value('id') ?? $rawSupplierId) : 0;

                        $rawBatchId = (int) ($tx['wallet_batch_id'] ?? 0);
                        $serverBatchId = $rawBatchId > 0 ? (WalletBatch::where('account_id', $accountId)->where('local_id', $rawBatchId)->value('id') ?? $rawBatchId) : 0;

                        $isDeleted = !empty($tx['deleted_at']) || !empty($tx['is_deleted']);
                        $deletedAtValue = $isDeleted ? ($parseDeletedAt($tx['deleted_at'] ?? null) ?? now()) : null;

                        $record = Transaction::withTrashed()->updateOrCreate(
                            ['account_id' => $accountId, 'local_id' => (int) $tx['local_id']],
                            [
                                'type'                  => substr((string) ($tx['type'] ?? 'Pending'), 0, 20),
                                'amount'                => (float) ($tx['amount'] ?? 0),
                                'customer_id'           => $serverCustomerId,
                                'supplier_id'           => $serverSupplierId,
                                'amount_sar'            => (float) ($tx['amount_sar'] ?? $tx['amount'] ?? 0),
                                'customer_rate'         => (float) ($tx['customer_rate'] ?? 0),
                                'supplier_rate'         => (float) ($tx['supplier_rate'] ?? 0),
                                'amount_bdt'            => (float) ($tx['amount_bdt'] ?? 0),
                                'receiver_name'         => substr((string) ($tx['receiver_name'] ?? ''), 0, 255),
                                'receiver_phone'        => substr((string) ($tx['receiver_phone'] ?? ''), 0, 50),
                                'receiver_account_type' => substr((string) ($tx['receiver_account_type'] ?? ''), 0, 50),
                                'receiver_account_no'   => substr((string) ($tx['receiver_account_no'] ?? ''), 0, 100),
                                'wallet_batch_id'       => $serverBatchId,
                                'notes'                 => $tx['notes'] ?? null,
                                'hash'                  => $tx['hash'] ?? null,
                                'timestamp'             => $sanitizeTimestamp($tx['timestamp'] ?? null),
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

                        $accepted['transactions'][] = [
                            'local_id'  => (int) $tx['local_id'],
                            'server_id' => $record->id
                        ];
                    }
                }

                // Step 7: Expenses & Incomes Sync (Independent Root)
                if (isset($data['expenses_incomes']) && is_array($data['expenses_incomes'])) {
                    foreach ($data['expenses_incomes'] as $e) {
                        if (empty($e['local_id'])) {
                            $rejected[] = [
                                'entity' => 'expenses_incomes',
                                'local_id' => 0,
                                'reason' => 'Missing local_id'
                            ];
                            continue;
                        }

                        $existing = ExpenseIncome::withTrashed()
                            ->where('account_id', $accountId)
                            ->where('local_id', (int) $e['local_id'])
                            ->first();

                        if ($existing && isset($e['timestamp']) && (int)$existing->timestamp > (int)$e['timestamp']) {
                            $accepted['expenses_incomes'][] = [
                                'local_id'  => (int) $e['local_id'],
                                'server_id' => $existing->id
                            ];
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
                                'timestamp'  => $sanitizeTimestamp($e['timestamp'] ?? null),
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

                        $accepted['expenses_incomes'][] = [
                            'local_id'  => (int) $e['local_id'],
                            'server_id' => $record->id
                        ];
                    }
                }
            });

            $user = $request->user();
            if (!$user) {
                $token = $request->bearerToken() ?? $request->header('X-SAFA-ACCESS-TOKEN');
                if ($token) {
                    $payload = AuthJWTController::verifyJwt($token);
                    if ($payload && isset($payload['user_id'])) {
                        $user = \App\Models\User::find($payload['user_id']);
                    }
                }
            }
            $permissions = $user ? $user->getFormattedPermissions() : \App\Models\User::defaultPermissions(true);

            return response()->json([
                'status'      => 'success',
                'server_time' => time(),
                'accepted'    => $accepted,
                'rejected'    => $rejected,
                'permissions' => $permissions,
            ]);
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

            $user = $request->user();
            if (!$user) {
                $token = $request->bearerToken() ?? $request->header('X-SAFA-ACCESS-TOKEN');
                if ($token) {
                    $payload = AuthJWTController::verifyJwt($token);
                    if ($payload && isset($payload['user_id'])) {
                        $user = \App\Models\User::find($payload['user_id']);
                    }
                }
            }
            $permissions = $user ? $user->getFormattedPermissions() : \App\Models\User::defaultPermissions(true);

            return response()->json([
                'transactions'      => Transaction::withTrashed()->where('account_id', $accountId)->get(),
                'customers'         => Customer::withTrashed()->where('account_id', $accountId)->get(),
                'suppliers'         => Supplier::withTrashed()->where('account_id', $accountId)->get(),
                'wallet_batches'    => WalletBatch::withTrashed()->where('account_id', $accountId)->get(),
                'wallet_ledgers'    => WalletLedger::withTrashed()->where('account_id', $accountId)->get(),
                'supplier_deposits' => SupplierDeposit::withTrashed()->where('account_id', $accountId)->get(),
                'expenses_incomes'  => ExpenseIncome::withTrashed()->where('account_id', $accountId)->get(),
                'permissions'       => $permissions,
                'user_permissions'  => $permissions,
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

