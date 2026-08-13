<?php

namespace App\Http\Controllers;

use App\Models\Customer;
use App\Models\ExpenseIncome;
use App\Models\Supplier;
use App\Models\SupplierDeposit;
use App\Models\Transaction;
use App\Models\WalletBatch;
use App\Models\WalletLedger;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Log;

/**
 * Read-only GraphQL compatibility surface.
 *
 * SAFA's canonical business mutation contract is the versioned REST API. Keeping
 * financial writes in two independent controllers caused account, validation and
 * calculation drift, so GraphQL mutations are intentionally deprecated. Reads
 * remain for compatibility and enforce the same active-record/account boundary.
 */
class GraphQLController extends Controller
{
    private const DEFAULT_LIMIT = 100;
    private const MAX_LIMIT = 250;
    private const MAX_OFFSET = 1000000;
    private const MAX_QUERY_BYTES = 32768;

    public function handle(Request $request)
    {
        $rawQuery = trim((string) $request->input('query', ''));
        $variables = $request->input('variables') ?? [];
        if (is_string($variables)) {
            $decoded = json_decode($variables, true);
            $variables = is_array($decoded) ? $decoded : [];
        }
        if (!is_array($variables)) $variables = [];

        if ($rawQuery === '') {
            return response()->json(['errors' => [['message' => 'Must provide query string.']]], 400);
        }
        if (strlen($rawQuery) > self::MAX_QUERY_BYTES) {
            return response()->json(['errors' => [['message' => 'GraphQL query is too large.']]], 413);
        }

        $accountId = (int) $request->attributes->get('active_account_id', 0);
        if ($accountId <= 0) {
            return response()->json(['errors' => [['message' => 'Authorized account context is required.']]], 403);
        }

        if (preg_match('/^\s*mutation\b/i', $rawQuery)) {
            return response()->json([
                'errors' => [[
                    'message' => 'GraphQL mutations are deprecated. Use the versioned REST API.',
                    'extensions' => ['code' => 'GRAPHQL_MUTATIONS_DEPRECATED', 'rest_base' => '/api/v1'],
                ]],
            ], 410);
        }

        try {
            return response()->json($this->executeReadQuery($rawQuery, $variables, $accountId));
        } catch (\InvalidArgumentException $e) {
            return response()->json(['errors' => [['message' => $e->getMessage()]]], 422);
        } catch (\Throwable $e) {
            Log::error('GraphQL read failure', [
                'exception' => $e::class,
                'account_id' => $accountId,
            ]);
            return response()->json(['errors' => [['message' => 'Internal server error']]], 500);
        }
    }

    protected function executeReadQuery(string $rawQuery, array $variables, int $accountId): array
    {
        $firstBrace = strpos($rawQuery, '{');
        $lastBrace = strrpos($rawQuery, '}');
        if ($firstBrace === false || $lastBrace === false || $firstBrace >= $lastBrace) {
            throw new \InvalidArgumentException('Syntax Error: Invalid GraphQL document query structure.');
        }

        $body = substr($rawQuery, $firstBrace + 1, $lastBrace - $firstBrace - 1);
        $fields = $this->parseRootFields($body);
        if (count($fields) > 20) {
            throw new \InvalidArgumentException('Too many root fields in one GraphQL request.');
        }

        $data = [];
        $errors = [];
        foreach ($fields as $field) {
            $key = $field['alias'] ?: $field['name'];
            try {
                $args = $this->parseArguments($field['raw_args'], $variables);
                $requestedFields = $this->parseSubfields($field['subfields_str']);
                $data[$key] = $this->resolveQuery($field['name'], $args, $requestedFields, $accountId);
            } catch (\InvalidArgumentException $e) {
                $data[$key] = null;
                $errors[] = ['message' => $e->getMessage(), 'path' => [$key]];
            }
        }

        $response = ['data' => $data];
        if ($errors !== []) $response['errors'] = $errors;
        return $response;
    }

    protected function resolveQuery(string $fieldName, array $args, array $requestedFields, int $accountId)
    {
        [$limit, $offset] = $this->pagination($args);

        switch ($fieldName) {
            case 'transactions':
                $query = Transaction::query()->where('account_id', $accountId)->whereNull('deleted_at');
                $this->optionalPositiveId($args, 'customer_id')?->let(fn () => null);
                if (isset($args['customer_id'])) $query->where('customer_id', $this->positiveId($args['customer_id'], 'customer_id'));
                if (isset($args['supplier_id'])) $query->where('supplier_id', $this->positiveId($args['supplier_id'], 'supplier_id'));
                if (isset($args['type'])) $query->where('type', substr((string) $args['type'], 0, 20));
                return $this->boundedRows($query, $limit, $offset, $requestedFields);

            case 'customers':
                $query = Customer::query()->where('account_id', $accountId)->whereNull('deleted_at');
                if (isset($args['search']) && trim((string) $args['search']) !== '') {
                    $search = substr(trim((string) $args['search']), 0, 100);
                    $query->where('name', 'like', '%' . $search . '%');
                }
                return $this->boundedRows($query, $limit, $offset, $requestedFields);

            case 'suppliers':
                $query = Supplier::query()->where('account_id', $accountId)->whereNull('deleted_at');
                if (isset($args['search']) && trim((string) $args['search']) !== '') {
                    $search = substr(trim((string) $args['search']), 0, 100);
                    $query->where('name', 'like', '%' . $search . '%');
                }
                return $this->boundedRows($query, $limit, $offset, $requestedFields);

            case 'walletBatches':
            case 'wallet_batches':
                $query = WalletBatch::query()->where('account_id', $accountId)->whereNull('deleted_at');
                if (isset($args['supplier_id'])) $query->where('supplier_id', $this->positiveId($args['supplier_id'], 'supplier_id'));
                if (isset($args['ledger_id'])) $query->where('ledger_id', $this->positiveId($args['ledger_id'], 'ledger_id'));
                return $this->boundedRows($query, $limit, $offset, $requestedFields);

            case 'walletLedgers':
            case 'wallet_ledgers':
                $query = WalletLedger::query()->where('account_id', $accountId)->whereNull('deleted_at');
                return $this->boundedRows($query, $limit, $offset, $requestedFields);

            case 'supplierDeposits':
            case 'supplier_deposits':
                $query = SupplierDeposit::query()->where('account_id', $accountId)->whereNull('deleted_at');
                if (isset($args['supplier_id'])) $query->where('supplier_id', $this->positiveId($args['supplier_id'], 'supplier_id'));
                return $this->boundedRows($query, $limit, $offset, $requestedFields);

            case 'expensesIncomes':
            case 'expenses_incomes':
                $query = ExpenseIncome::query()->where('account_id', $accountId)->whereNull('deleted_at');
                if (isset($args['is_expense'])) $query->where('is_expense', filter_var($args['is_expense'], FILTER_VALIDATE_BOOLEAN));
                if (isset($args['category']) && trim((string) $args['category']) !== '') {
                    $query->where('category', substr(trim((string) $args['category']), 0, 50));
                }
                return $this->boundedRows($query, $limit, $offset, $requestedFields);

            case 'syncState':
            case 'sync_state':
                $state = [
                    'lastSyncedAt' => Transaction::query()->where('account_id', $accountId)->whereNull('deleted_at')->latest('updated_at')->value('updated_at')?->toIso8601String() ?? now()->toIso8601String(),
                    'transactionCount' => Transaction::query()->where('account_id', $accountId)->whereNull('deleted_at')->count(),
                    'customerCount' => Customer::query()->where('account_id', $accountId)->whereNull('deleted_at')->count(),
                    'supplierCount' => Supplier::query()->where('account_id', $accountId)->whereNull('deleted_at')->count(),
                    'walletBatchCount' => WalletBatch::query()->where('account_id', $accountId)->whereNull('deleted_at')->count(),
                    'expenseIncomeCount' => ExpenseIncome::query()->where('account_id', $accountId)->whereNull('deleted_at')->count(),
                ];
                return $this->filterFields($state, $requestedFields);

            default:
                throw new \InvalidArgumentException("Unknown query field '{$fieldName}'.");
        }
    }

    private function boundedRows($query, int $limit, int $offset, array $requestedFields): array
    {
        return $query->orderBy('id', 'asc')
            ->offset($offset)
            ->limit($limit)
            ->get()
            ->map(fn ($item) => $this->filterFields($item->toArray(), $requestedFields))
            ->values()
            ->toArray();
    }

    private function pagination(array $args): array
    {
        $limit = $args['limit'] ?? self::DEFAULT_LIMIT;
        $offset = $args['offset'] ?? 0;
        if (!is_numeric($limit) || (int) $limit < 1) throw new \InvalidArgumentException('limit must be a positive integer.');
        if (!is_numeric($offset) || (int) $offset < 0) throw new \InvalidArgumentException('offset must be a non-negative integer.');
        return [min((int) $limit, self::MAX_LIMIT), min((int) $offset, self::MAX_OFFSET)];
    }

    private function positiveId($value, string $name): int
    {
        if (!is_numeric($value) || (int) $value <= 0) throw new \InvalidArgumentException("{$name} must be a positive integer.");
        return (int) $value;
    }

    private function optionalPositiveId(array $args, string $name): ?int
    {
        return isset($args[$name]) ? $this->positiveId($args[$name], $name) : null;
    }

    protected function parseRootFields(string $body): array
    {
        $fields = [];
        $len = strlen($body);
        $i = 0;
        while ($i < $len) {
            while ($i < $len && (ctype_space($body[$i]) || $body[$i] === ',')) $i++;
            if ($i >= $len) break;

            $start = $i;
            while ($i < $len && (ctype_alnum($body[$i]) || $body[$i] === '_' || $body[$i] === ':')) $i++;
            $rawName = trim(substr($body, $start, $i - $start));
            if ($rawName === '') { $i++; continue; }

            $alias = null;
            $name = $rawName;
            if (str_contains($rawName, ':')) {
                [$alias, $name] = array_map('trim', explode(':', $rawName, 2));
            }
            while ($i < $len && ctype_space($body[$i])) $i++;

            $rawArgs = '';
            if ($i < $len && $body[$i] === '(') {
                $argStart = ++$i;
                $depth = 1;
                $quote = null;
                while ($i < $len && $depth > 0) {
                    $char = $body[$i];
                    if ($quote !== null) {
                        if ($char === $quote && ($i === 0 || $body[$i - 1] !== '\\')) $quote = null;
                    } else {
                        if ($char === '"' || $char === "'") $quote = $char;
                        elseif ($char === '(') $depth++;
                        elseif ($char === ')') $depth--;
                    }
                    $i++;
                }
                $rawArgs = substr($body, $argStart, max(0, $i - $argStart - 1));
            }
            while ($i < $len && ctype_space($body[$i])) $i++;

            $subfields = '';
            if ($i < $len && $body[$i] === '{') {
                $subStart = ++$i;
                $depth = 1;
                $quote = null;
                while ($i < $len && $depth > 0) {
                    $char = $body[$i];
                    if ($quote !== null) {
                        if ($char === $quote && $body[$i - 1] !== '\\') $quote = null;
                    } else {
                        if ($char === '"' || $char === "'") $quote = $char;
                        elseif ($char === '{') $depth++;
                        elseif ($char === '}') $depth--;
                    }
                    $i++;
                }
                $subfields = substr($body, $subStart, max(0, $i - $subStart - 1));
            }

            $fields[] = ['alias' => $alias, 'name' => $name, 'raw_args' => $rawArgs, 'subfields_str' => $subfields];
        }
        return $fields;
    }

    protected function parseArguments(string $rawArgs, array $variables): array
    {
        $args = [];
        if (trim($rawArgs) !== '') {
            preg_match_all('/([a-zA-Z0-9_]+)\s*:\s*(\$[a-zA-Z0-9_]+|"[^"]*"|\'[^\']*\'|true|false|null|[-+]?[0-9]*\.?[0-9]+)/i', $rawArgs, $matches, PREG_SET_ORDER);
            foreach ($matches as $match) {
                $key = $match[1];
                $raw = trim($match[2]);
                if (str_starts_with($raw, '$')) $value = $variables[substr($raw, 1)] ?? null;
                elseif ((str_starts_with($raw, '"') && str_ends_with($raw, '"')) || (str_starts_with($raw, "'") && str_ends_with($raw, "'"))) $value = substr($raw, 1, -1);
                elseif ($raw === 'true') $value = true;
                elseif ($raw === 'false') $value = false;
                elseif ($raw === 'null') $value = null;
                elseif (is_numeric($raw)) $value = str_contains($raw, '.') ? (float) $raw : (int) $raw;
                else $value = $raw;
                $args[$key] = $value;
            }
        }
        foreach ($variables as $key => $value) if (!array_key_exists($key, $args)) $args[$key] = $value;
        return $args;
    }

    protected function parseSubfields(string $subfields): array
    {
        $clean = preg_replace('/\{[^\}]*\}/', '', trim($subfields));
        if ($clean === '') return [];
        return array_values(array_unique(array_filter(
            preg_split('/[\s,]+/', $clean) ?: [],
            fn ($field) => $field !== '' && !str_starts_with($field, '__')
        )));
    }

    protected function filterFields(array $item, array $requestedFields): array
    {
        if ($requestedFields === []) return $item;
        return array_intersect_key($item, array_flip($requestedFields));
    }
}
