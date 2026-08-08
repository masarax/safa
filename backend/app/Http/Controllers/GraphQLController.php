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
use App\Models\Account;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Log;

class GraphQLController extends Controller
{
    /**
     * Handle POST /api/graphql requests.
     */
    public function handle(Request $request)
    {
        try {
            $rawQuery = $request->input('query');
            $variables = $request->input('variables') ?? [];

            if (is_string($variables)) {
                $decodedVars = json_decode($variables, true);
                $variables = is_array($decodedVars) ? $decodedVars : [];
            }

            if (empty($rawQuery)) {
                return response()->json([
                    'errors' => [
                        ['message' => 'Must provide query string.']
                    ]
                ], 400);
            }

            $user = $request->user();
            $accountId = $this->getAccountId($user);

            $result = $this->executeQuery($rawQuery, $variables, $accountId, $user);

            return response()->json($result);
        } catch (\Throwable $e) {
            Log::error("GraphQL Execution Error: " . $e->getMessage());
            return response()->json([
                'errors' => [
                    [
                        'message' => $e->getMessage(),
                        'trace' => config('app.debug') ? $e->getTraceAsString() : null
                    ]
                ]
            ], 500);
        }
    }

    /**
     * Resolve account ID for current context.
     */
    protected function getAccountId($user = null): int
    {
        if ($user && isset($user->account_id) && $user->account_id) {
            return (int) $user->account_id;
        }

        $defaultAccount = Account::first();
        return (int) ($defaultAccount?->id ?? 1);
    }

    /**
     * Parse and execute the GraphQL query or mutation string.
     */
    protected function executeQuery(string $rawQuery, array $variables, int $accountId, $user): array
    {
        $cleanQuery = trim($rawQuery);

        // Determine operation type
        $isMutation = false;
        if (preg_match('/^\s*mutation\b/i', $cleanQuery)) {
            $isMutation = true;
        }

        // Extract root operation block inside outermost braces { ... }
        $firstBrace = strpos($cleanQuery, '{');
        $lastBrace = strrpos($cleanQuery, '}');

        if ($firstBrace === false || $lastBrace === false || $firstBrace >= $lastBrace) {
            return [
                'errors' => [
                    ['message' => 'Syntax Error: Invalid GraphQL document query structure.']
                ]
            ];
        }

        $body = substr($cleanQuery, $firstBrace + 1, $lastBrace - $firstBrace - 1);
        $rootFields = $this->parseRootFields($body);

        $data = [];
        $errors = [];

        foreach ($rootFields as $field) {
            $alias = $field['alias'];
            $fieldName = $field['name'];
            $resultKey = $alias ?: $fieldName;
            $rawArgs = $field['raw_args'];
            $subfieldsStr = $field['subfields_str'];

            $args = $this->parseArguments($rawArgs, $variables);
            $requestedFields = $this->parseSubfields($subfieldsStr);

            try {
                if ($isMutation) {
                    $resolvedValue = $this->resolveMutation($fieldName, $args, $requestedFields, $accountId, $user);
                } else {
                    $resolvedValue = $this->resolveQuery($fieldName, $args, $requestedFields, $accountId, $user);
                }
                $data[$resultKey] = $resolvedValue;
            } catch (\Throwable $ex) {
                $errors[] = [
                    'message' => "Error resolving field '{$fieldName}': " . $ex->getMessage(),
                    'path' => [$resultKey]
                ];
                $data[$resultKey] = null;
            }
        }

        $response = ['data' => $data];
        if (!empty($errors)) {
            $response['errors'] = $errors;
        }

        return $response;
    }

    /**
     * Resolve Query fields.
     */
    protected function resolveQuery(string $fieldName, array $args, array $requestedFields, int $accountId, $user)
    {
        switch ($fieldName) {
            case 'transactions':
                $query = Transaction::withTrashed()->where('account_id', $accountId);
                if (!empty($args['customer_id'])) {
                    $query->where('customer_id', (int) $args['customer_id']);
                }
                if (!empty($args['supplier_id'])) {
                    $query->where('supplier_id', (int) $args['supplier_id']);
                }
                if (!empty($args['type'])) {
                    $query->where('type', $args['type']);
                }
                if (isset($args['limit'])) {
                    $query->limit((int) $args['limit']);
                }
                if (isset($args['offset'])) {
                    $query->offset((int) $args['offset']);
                }
                return $query->get()->map(fn ($item) => $this->filterFields($item->toArray(), $requestedFields))->toArray();

            case 'customers':
                $query = Customer::withTrashed()->where('account_id', $accountId);
                if (!empty($args['search'])) {
                    $query->where('name', 'like', '%' . $args['search'] . '%');
                }
                if (isset($args['limit'])) {
                    $query->limit((int) $args['limit']);
                }
                if (isset($args['offset'])) {
                    $query->offset((int) $args['offset']);
                }
                return $query->get()->map(fn ($item) => $this->filterFields($item->toArray(), $requestedFields))->toArray();

            case 'suppliers':
                $query = Supplier::withTrashed()->where('account_id', $accountId);
                if (!empty($args['search'])) {
                    $query->where('name', 'like', '%' . $args['search'] . '%');
                }
                if (isset($args['limit'])) {
                    $query->limit((int) $args['limit']);
                }
                if (isset($args['offset'])) {
                    $query->offset((int) $args['offset']);
                }
                return $query->get()->map(fn ($item) => $this->filterFields($item->toArray(), $requestedFields))->toArray();

            case 'walletBatches':
            case 'wallet_batches':
                $query = WalletBatch::withTrashed()->where('account_id', $accountId);
                if (!empty($args['supplier_id'])) {
                    $query->where('supplier_id', (int) $args['supplier_id']);
                }
                if (!empty($args['ledger_id'])) {
                    $query->where('ledger_id', (int) $args['ledger_id']);
                }
                if (isset($args['limit'])) {
                    $query->limit((int) $args['limit']);
                }
                if (isset($args['offset'])) {
                    $query->offset((int) $args['offset']);
                }
                return $query->get()->map(fn ($item) => $this->filterFields($item->toArray(), $requestedFields))->toArray();

            case 'expensesIncomes':
            case 'expenses_incomes':
                $query = ExpenseIncome::withTrashed()->where('account_id', $accountId);
                if (isset($args['is_expense'])) {
                    $query->where('is_expense', (bool) $args['is_expense']);
                }
                if (!empty($args['category'])) {
                    $query->where('category', $args['category']);
                }
                if (isset($args['limit'])) {
                    $query->limit((int) $args['limit']);
                }
                if (isset($args['offset'])) {
                    $query->offset((int) $args['offset']);
                }
                return $query->get()->map(fn ($item) => $this->filterFields($item->toArray(), $requestedFields))->toArray();

            case 'syncState':
            case 'sync_state':
                $latestTx = Transaction::where('account_id', $accountId)->latest('updated_at')->first();
                $state = [
                    'lastSyncedAt' => $latestTx?->updated_at?->toIso8601String() ?? now()->toIso8601String(),
                    'transactionCount' => Transaction::where('account_id', $accountId)->count(),
                    'customerCount' => Customer::where('account_id', $accountId)->count(),
                    'supplierCount' => Supplier::where('account_id', $accountId)->count(),
                    'walletBatchCount' => WalletBatch::where('account_id', $accountId)->count(),
                    'expenseIncomeCount' => ExpenseIncome::where('account_id', $accountId)->count(),
                ];
                return $this->filterFields($state, $requestedFields);

            default:
                throw new \Exception("Unknown query field '{$fieldName}'.");
        }
    }

    /**
     * Resolve Mutation fields.
     */
    protected function resolveMutation(string $fieldName, array $args, array $requestedFields, int $accountId, $user)
    {
        switch ($fieldName) {
            case 'syncUpData':
            case 'sync_up_data':
                return $this->performSyncUp($args, $accountId);

            case 'registerCustomer':
            case 'register_customer':
                $name = $args['name'] ?? null;
                if (empty($name)) {
                    throw new \Exception("Customer 'name' is required.");
                }
                $localId = isset($args['local_id']) ? (int) $args['local_id'] : ((Customer::where('account_id', $accountId)->max('local_id') ?? 0) + 1);
                $customer = Customer::updateOrCreate(
                    ['account_id' => $accountId, 'local_id' => $localId],
                    [
                        'name' => substr((string) $name, 0, 255),
                        'phone' => isset($args['phone']) ? substr((string) $args['phone'], 0, 50) : null,
                        'timestamp' => (int) ($args['timestamp'] ?? time()),
                    ]
                );
                return $this->filterFields($customer->toArray(), $requestedFields);

            case 'registerSupplier':
            case 'register_supplier':
                $name = $args['name'] ?? null;
                if (empty($name)) {
                    throw new \Exception("Supplier 'name' is required.");
                }
                $localId = isset($args['local_id']) ? (int) $args['local_id'] : ((Supplier::where('account_id', $accountId)->max('local_id') ?? 0) + 1);
                $supplier = Supplier::updateOrCreate(
                    ['account_id' => $accountId, 'local_id' => $localId],
                    [
                        'name' => substr((string) $name, 0, 255),
                        'phone' => isset($args['phone']) ? substr((string) $args['phone'], 0, 50) : null,
                        'timestamp' => (int) ($args['timestamp'] ?? time()),
                    ]
                );
                return $this->filterFields($supplier->toArray(), $requestedFields);

            case 'recordTransaction':
            case 'record_transaction':
                $localId = isset($args['local_id']) ? (int) $args['local_id'] : ((Transaction::where('account_id', $accountId)->max('local_id') ?? 0) + 1);
                $tx = Transaction::updateOrCreate(
                    ['account_id' => $accountId, 'local_id' => $localId],
                    [
                        'type' => substr((string) ($args['type'] ?? 'Pending'), 0, 20),
                        'amount' => (float) ($args['amount'] ?? 0),
                        'customer_id' => (int) ($args['customer_id'] ?? 0),
                        'supplier_id' => (int) ($args['supplier_id'] ?? 0),
                        'amount_sar' => (float) ($args['amount_sar'] ?? $args['amount'] ?? 0),
                        'customer_rate' => (float) ($args['customer_rate'] ?? 0),
                        'supplier_rate' => (float) ($args['supplier_rate'] ?? 0),
                        'amount_bdt' => (float) ($args['amount_bdt'] ?? 0),
                        'receiver_name' => isset($args['receiver_name']) ? substr((string) $args['receiver_name'], 0, 255) : null,
                        'receiver_phone' => isset($args['receiver_phone']) ? substr((string) $args['receiver_phone'], 0, 50) : null,
                        'receiver_account_type' => isset($args['receiver_account_type']) ? substr((string) $args['receiver_account_type'], 0, 50) : null,
                        'receiver_account_no' => isset($args['receiver_account_no']) ? substr((string) $args['receiver_account_no'], 0, 100) : null,
                        'wallet_batch_id' => (int) ($args['wallet_batch_id'] ?? 0),
                        'notes' => $args['notes'] ?? null,
                        'timestamp' => (int) ($args['timestamp'] ?? time()),
                    ]
                );
                return $this->filterFields($tx->toArray(), $requestedFields);

            default:
                throw new \Exception("Unknown mutation field '{$fieldName}'.");
        }
    }

    /**
     * Perform batch sync up inside DB transaction.
     */
    protected function performSyncUp(array $data, int $accountId): array
    {
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

        $syncedCounts = [
            'transactions' => 0,
            'customers' => 0,
            'suppliers' => 0,
            'wallet_batches' => 0,
            'supplier_deposits' => 0,
            'expenses_incomes' => 0,
            'wallet_ledgers' => 0,
        ];

        DB::transaction(function () use ($data, $accountId, $parseDeletedAt, &$syncedCounts) {
            // 1. Transactions
            if (isset($data['transactions']) && is_array($data['transactions'])) {
                foreach ($data['transactions'] as $tx) {
                    if (empty($tx['local_id'])) continue;
                    $existing = Transaction::withTrashed()->where('account_id', $accountId)->where('local_id', (int) $tx['local_id'])->first();
                    if ($existing && isset($tx['timestamp']) && $existing->timestamp > (int) $tx['timestamp']) continue;

                    $isDeleted = !empty($tx['deleted_at']) || !empty($tx['is_deleted']);
                    $record = Transaction::withTrashed()->updateOrCreate(
                        ['account_id' => $accountId, 'local_id' => (int) $tx['local_id']],
                        [
                            'type' => substr((string) ($tx['type'] ?? 'Pending'), 0, 20),
                            'amount' => (float) ($tx['amount'] ?? 0),
                            'customer_id' => (int) ($tx['customer_id'] ?? 0),
                            'supplier_id' => (int) ($tx['supplier_id'] ?? 0),
                            'amount_sar' => (float) ($tx['amount_sar'] ?? $tx['amount'] ?? 0),
                            'customer_rate' => (float) ($tx['customer_rate'] ?? 0),
                            'supplier_rate' => (float) ($tx['supplier_rate'] ?? 0),
                            'amount_bdt' => (float) ($tx['amount_bdt'] ?? 0),
                            'receiver_name' => substr((string) ($tx['receiver_name'] ?? ''), 0, 255),
                            'receiver_phone' => substr((string) ($tx['receiver_phone'] ?? ''), 0, 50),
                            'receiver_account_type' => substr((string) ($tx['receiver_account_type'] ?? ''), 0, 50),
                            'receiver_account_no' => substr((string) ($tx['receiver_account_no'] ?? ''), 0, 100),
                            'wallet_batch_id' => (int) ($tx['wallet_batch_id'] ?? 0),
                            'notes' => $tx['notes'] ?? null,
                            'timestamp' => (int) ($tx['timestamp'] ?? time()),
                        ]
                    );

                    if ($isDeleted) {
                        $record->deleted_at = $parseDeletedAt($tx['deleted_at'] ?? null) ?? now();
                        $record->save();
                    } else {
                        if ($record->trashed()) $record->restore();
                        $record->deleted_at = null;
                        $record->save();
                    }
                    $syncedCounts['transactions']++;
                }
            }

            // 2. Customers
            if (isset($data['customers']) && is_array($data['customers'])) {
                foreach ($data['customers'] as $c) {
                    if (empty($c['local_id']) || empty($c['name'])) continue;
                    $existing = Customer::withTrashed()->where('account_id', $accountId)->where('local_id', (int) $c['local_id'])->first();
                    if ($existing && isset($c['timestamp']) && $existing->timestamp > (int) $c['timestamp']) continue;

                    $isDeleted = !empty($c['deleted_at']) || !empty($c['is_deleted']);
                    $record = Customer::withTrashed()->updateOrCreate(
                        ['account_id' => $accountId, 'local_id' => (int) $c['local_id']],
                        [
                            'name' => substr((string) $c['name'], 0, 255),
                            'phone' => substr((string) ($c['phone'] ?? ''), 0, 50),
                            'timestamp' => (int) ($c['timestamp'] ?? time()),
                        ]
                    );

                    if ($isDeleted) {
                        $record->deleted_at = $parseDeletedAt($c['deleted_at'] ?? null) ?? now();
                        $record->save();
                    } else {
                        if ($record->trashed()) $record->restore();
                        $record->deleted_at = null;
                        $record->save();
                    }
                    $syncedCounts['customers']++;
                }
            }

            // 3. Suppliers
            if (isset($data['suppliers']) && is_array($data['suppliers'])) {
                foreach ($data['suppliers'] as $s) {
                    if (empty($s['local_id']) || empty($s['name'])) continue;
                    $existing = Supplier::withTrashed()->where('account_id', $accountId)->where('local_id', (int) $s['local_id'])->first();
                    if ($existing && isset($s['timestamp']) && $existing->timestamp > (int) $s['timestamp']) continue;

                    $isDeleted = !empty($s['deleted_at']) || !empty($s['is_deleted']);
                    $record = Supplier::withTrashed()->updateOrCreate(
                        ['account_id' => $accountId, 'local_id' => (int) $s['local_id']],
                        [
                            'name' => substr((string) $s['name'], 0, 255),
                            'phone' => substr((string) ($s['phone'] ?? ''), 0, 50),
                            'timestamp' => (int) ($s['timestamp'] ?? time()),
                        ]
                    );

                    if ($isDeleted) {
                        $record->deleted_at = $parseDeletedAt($s['deleted_at'] ?? null) ?? now();
                        $record->save();
                    } else {
                        if ($record->trashed()) $record->restore();
                        $record->deleted_at = null;
                        $record->save();
                    }
                    $syncedCounts['suppliers']++;
                }
            }

            // 4. Wallet Batches
            $walletBatches = $data['wallet_batches'] ?? $data['walletBatches'] ?? null;
            if (isset($walletBatches) && is_array($walletBatches)) {
                foreach ($walletBatches as $b) {
                    if (empty($b['local_id'])) continue;
                    $existing = WalletBatch::withTrashed()->where('account_id', $accountId)->where('local_id', (int) $b['local_id'])->first();
                    if ($existing && isset($b['timestamp']) && $existing->timestamp > (int) $b['timestamp']) continue;

                    $isDeleted = !empty($b['deleted_at']) || !empty($b['is_deleted']);
                    $record = WalletBatch::withTrashed()->updateOrCreate(
                        ['account_id' => $accountId, 'local_id' => (int) $b['local_id']],
                        [
                            'ledger_id' => (int) ($b['ledger_id'] ?? 0),
                            'rate' => (float) ($b['rate'] ?? 0),
                            'initial_bdt' => (float) ($b['initial_bdt'] ?? 0),
                            'remaining_bdt' => (float) ($b['remaining_bdt'] ?? 0),
                            'supplier_id' => (int) ($b['supplier_id'] ?? 0),
                            'supplier_deposit_id' => (int) ($b['supplier_deposit_id'] ?? 0),
                            'notes' => $b['notes'] ?? null,
                            'timestamp' => (int) ($b['timestamp'] ?? time()),
                        ]
                    );

                    if ($isDeleted) {
                        $record->deleted_at = $parseDeletedAt($b['deleted_at'] ?? null) ?? now();
                        $record->save();
                    } else {
                        if ($record->trashed()) $record->restore();
                        $record->deleted_at = null;
                        $record->save();
                    }
                    $syncedCounts['wallet_batches']++;
                }
            }

            // 5. Expenses Incomes
            $expensesIncomes = $data['expenses_incomes'] ?? $data['expensesIncomes'] ?? null;
            if (isset($expensesIncomes) && is_array($expensesIncomes)) {
                foreach ($expensesIncomes as $e) {
                    if (empty($e['local_id'])) continue;
                    $existing = ExpenseIncome::withTrashed()->where('account_id', $accountId)->where('local_id', (int) $e['local_id'])->first();
                    if ($existing && isset($e['timestamp']) && $existing->timestamp > (int) $e['timestamp']) continue;

                    $isDeleted = !empty($e['deleted_at']) || !empty($e['is_deleted']);
                    $record = ExpenseIncome::withTrashed()->updateOrCreate(
                        ['account_id' => $accountId, 'local_id' => (int) $e['local_id']],
                        [
                            'title' => substr((string) ($e['title'] ?? 'General'), 0, 255),
                            'amount' => (float) ($e['amount'] ?? 0),
                            'currency' => substr((string) ($e['currency'] ?? 'BDT'), 0, 10),
                            'is_expense' => (bool) ($e['is_expense'] ?? true),
                            'category' => substr((string) ($e['category'] ?? 'General'), 0, 50),
                            'timestamp' => (int) ($e['timestamp'] ?? time()),
                        ]
                    );

                    if ($isDeleted) {
                        $record->deleted_at = $parseDeletedAt($e['deleted_at'] ?? null) ?? now();
                        $record->save();
                    } else {
                        if ($record->trashed()) $record->restore();
                        $record->deleted_at = null;
                        $record->save();
                    }
                    $syncedCounts['expenses_incomes']++;
                }
            }
        });

        return [
            'status' => 'success',
            'message' => 'Data synchronized successfully.',
            'synced_counts' => $syncedCounts,
        ];
    }

    /**
     * Parse root operation fields from the query body.
     */
    protected function parseRootFields(string $body): array
    {
        $fields = [];
        $len = strlen($body);
        $i = 0;

        while ($i < $len) {
            while ($i < $len && (ctype_space($body[$i]) || $body[$i] === ',')) {
                $i++;
            }
            if ($i >= $len) break;

            $nameStart = $i;
            while ($i < $len && (ctype_alnum($body[$i]) || $body[$i] === '_' || $body[$i] === ':')) {
                $i++;
            }
            $rawName = trim(substr($body, $nameStart, $i - $nameStart));
            if (empty($rawName)) {
                $i++;
                continue;
            }

            $alias = null;
            $fieldName = $rawName;
            if (str_contains($rawName, ':')) {
                $parts = explode(':', $rawName, 2);
                $alias = trim($parts[0]);
                $fieldName = trim($parts[1]);
            }

            while ($i < $len && ctype_space($body[$i])) {
                $i++;
            }

            // Extract raw arguments
            $rawArgs = '';
            if ($i < $len && $body[$i] === '(') {
                $i++;
                $parenDepth = 1;
                $argStart = $i;
                $inString = false;
                $quoteChar = '';
                while ($i < $len && $parenDepth > 0) {
                    $char = $body[$i];
                    if ($inString) {
                        if ($char === $quoteChar && $body[$i - 1] !== '\\') {
                            $inString = false;
                        }
                    } else {
                        if ($char === '"' || $char === "'") {
                            $inString = true;
                            $quoteChar = $char;
                        } elseif ($char === '(') {
                            $parenDepth++;
                        } elseif ($char === ')') {
                            $parenDepth--;
                        }
                    }
                    $i++;
                }
                $rawArgs = substr($body, $argStart, $i - $argStart - 1);
            }

            while ($i < $len && ctype_space($body[$i])) {
                $i++;
            }

            // Extract subfields block
            $subfieldsStr = '';
            if ($i < $len && $body[$i] === '{') {
                $i++;
                $braceDepth = 1;
                $subStart = $i;
                $inString = false;
                $quoteChar = '';
                while ($i < $len && $braceDepth > 0) {
                    $char = $body[$i];
                    if ($inString) {
                        if ($char === $quoteChar && $body[$i - 1] !== '\\') {
                            $inString = false;
                        }
                    } else {
                        if ($char === '"' || $char === "'") {
                            $inString = true;
                            $quoteChar = $char;
                        } elseif ($char === '{') {
                            $braceDepth++;
                        } elseif ($char === '}') {
                            $braceDepth--;
                        }
                    }
                    $i++;
                }
                $subfieldsStr = substr($body, $subStart, $i - $subStart - 1);
            }

            $fields[] = [
                'alias' => $alias,
                'name' => $fieldName,
                'raw_args' => $rawArgs,
                'subfields_str' => $subfieldsStr,
            ];
        }

        return $fields;
    }

    /**
     * Parse raw arguments string and substitute GraphQL variables.
     */
    protected function parseArguments(string $rawArgs, array $variables): array
    {
        $args = [];
        $rawArgsTrim = trim($rawArgs);

        if (!empty($rawArgsTrim)) {
            preg_match_all('/([a-zA-Z0-9_]+)\s*:\s*(\$[a-zA-Z0-9_]+|"[^"]*"|\'[^\']*\'|true|false|null|\[[^\]]*\]|\{[^\}]*\}|[-+]?[0-9]*\.?[0-9]+)/i', $rawArgsTrim, $matches, PREG_SET_ORDER);

            foreach ($matches as $match) {
                $key = $match[1];
                $valStr = trim($match[2]);

                if (str_starts_with($valStr, '$')) {
                    $varName = substr($valStr, 1);
                    $val = $variables[$varName] ?? null;
                } elseif ((str_starts_with($valStr, '"') && str_ends_with($valStr, '"')) || (str_starts_with($valStr, "'") && str_ends_with($valStr, "'"))) {
                    $val = substr($valStr, 1, -1);
                } elseif ($valStr === 'true') {
                    $val = true;
                } elseif ($valStr === 'false') {
                    $val = false;
                } elseif ($valStr === 'null') {
                    $val = null;
                } elseif (is_numeric($valStr)) {
                    $val = str_contains($valStr, '.') ? (float) $valStr : (int) $valStr;
                } else {
                    $jsonVal = json_decode($valStr, true);
                    $val = ($jsonVal !== null) ? $jsonVal : $valStr;
                }

                $args[$key] = $val;
            }
        }

        foreach ($variables as $vKey => $vVal) {
            if (!array_key_exists($vKey, $args)) {
                $args[$vKey] = $vVal;
            }
        }

        return $args;
    }

    /**
     * Parse requested child fields.
     */
    protected function parseSubfields(string $subfieldsStr): array
    {
        $clean = trim($subfieldsStr);
        if (empty($clean)) {
            return [];
        }

        // Remove nested blocks if present
        $clean = preg_replace('/\{[^\}]*\}/', '', $clean);
        $tokens = preg_split('/[\s,]+/', $clean);

        $fields = [];
        foreach ($tokens as $token) {
            $t = trim($token);
            if (!empty($t) && !str_starts_with($t, '__')) {
                $fields[] = $t;
            }
        }

        return array_unique($fields);
    }

    /**
     * Filter array items according to requested subfields.
     */
    protected function filterFields(array $item, array $requestedFields): array
    {
        if (empty($requestedFields)) {
            return $item;
        }

        $filtered = [];
        foreach ($requestedFields as $field) {
            if (array_key_exists($field, $item)) {
                $filtered[$field] = $item[$field];
            }
        }

        return $filtered;
    }
}
