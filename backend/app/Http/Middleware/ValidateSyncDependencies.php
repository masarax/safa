<?php

namespace App\Http\Middleware;

use App\Http\Controllers\AuthJWTController;
use App\Models\Customer;
use App\Models\SafaApiKey;
use App\Models\Supplier;
use App\Models\SupplierDeposit;
use App\Models\WalletBatch;
use App\Models\WalletLedger;
use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

/**
 * Protects the local-first sync contract from invalid relationship IDs and
 * normalizes Android timestamps before SyncController performs stale-write
 * checks. Android local timestamps are milliseconds while the server stores
 * sync timestamps in Unix seconds; comparing the two units directly makes a
 * stale client mutation look newer than the server.
 */
class ValidateSyncDependencies
{
    public function handle(Request $request, Closure $next): Response
    {
        if (strtoupper($request->method()) !== 'POST') {
            return $next($request);
        }

        $accountId = $this->resolveAccountId($request);
        if ($accountId <= 0) {
            return response()->json([
                'status' => 'error',
                'message' => 'Unable to resolve the authenticated account context.',
            ], 401);
        }

        // Normalize the complete sync payload in-place. This keeps the public
        // API contract unchanged while making server/client conflict checks use
        // one timestamp unit.
        $payload = $request->all();
        $this->normalizeSyncTimestamps($payload);
        $request->replace($payload);

        $incoming = [
            'customers' => $this->localIds($payload['customers'] ?? null),
            'suppliers' => $this->localIds($payload['suppliers'] ?? null),
            'wallet_ledgers' => $this->localIds($payload['wallet_ledgers'] ?? null),
            'supplier_deposits' => $this->localIds($payload['supplier_deposits'] ?? null),
            'wallet_batches' => $this->localIds($payload['wallet_batches'] ?? null),
        ];

        $checks = [
            ['supplier_deposits', 'supplier_id', 'suppliers', Supplier::class],
            ['wallet_batches', 'ledger_id', 'wallet_ledgers', WalletLedger::class],
            ['wallet_batches', 'supplier_id', 'suppliers', Supplier::class],
            ['wallet_batches', 'supplier_deposit_id', 'supplier_deposits', SupplierDeposit::class],
            ['transactions', 'customer_id', 'customers', Customer::class],
            ['transactions', 'supplier_id', 'suppliers', Supplier::class],
            ['transactions', 'wallet_batch_id', 'wallet_batches', WalletBatch::class],
        ];

        foreach ($checks as [$entity, $field, $parentEntity, $model]) {
            foreach ($this->rows($payload[$entity] ?? null) as $row) {
                $localId = (int) ($row[$field] ?? 0);
                if ($localId <= 0) {
                    continue;
                }

                if (isset($incoming[$parentEntity][$localId])) {
                    continue;
                }

                $exists = $model::withTrashed()
                    ->where('account_id', $accountId)
                    ->where('local_id', $localId)
                    ->exists();

                if (!$exists) {
                    return response()->json([
                        'status' => 'error',
                        'code' => 'SYNC_DEPENDENCY_PENDING',
                        'message' => "Sync dependency is not available yet: {$parentEntity} local_id={$localId}.",
                        'entity' => $entity,
                        'local_id' => (int) ($row['local_id'] ?? 0),
                        'dependency' => $parentEntity,
                        'dependency_local_id' => $localId,
                        'retry_after_seconds' => 2,
                    ], 429)->header('Retry-After', '2');
                }
            }
        }

        return $next($request);
    }

    /**
     * Convert millisecond Unix timestamps to seconds. Existing second-based
     * timestamps are left untouched. Invalid/future values are handled later
     * by SyncController's own validation/sanitization.
     */
    private function normalizeSyncTimestamps(array &$payload): void
    {
        foreach ([
            'customers',
            'suppliers',
            'wallet_ledgers',
            'supplier_deposits',
            'wallet_batches',
            'transactions',
            'expenses_incomes',
        ] as $entity) {
            foreach ($this->rows($payload[$entity] ?? null) as $index => $row) {
                if (isset($row['timestamp']) && is_numeric($row['timestamp'])) {
                    $timestamp = (int) $row['timestamp'];
                    if ($timestamp > 2000000000) {
                        $payload[$entity][$index]['timestamp'] = intdiv($timestamp, 1000);
                    }
                }
            }
        }
    }

    private function rows($value): array
    {
        return is_array($value) ? array_values(array_filter($value, 'is_array')) : [];
    }

    private function localIds($value): array
    {
        $ids = [];
        foreach ($this->rows($value) as $row) {
            $id = (int) ($row['local_id'] ?? 0);
            if ($id > 0) {
                $ids[$id] = true;
            }
        }
        return $ids;
    }

    private function resolveAccountId(Request $request): int
    {
        $token = $request->bearerToken() ?? $request->header('X-SAFA-ACCESS-TOKEN');
        if ($token) {
            $payload = AuthJWTController::verifyJwt($token);
            if ($payload && isset($payload['account_id'])) {
                return (int) $payload['account_id'];
            }
        }

        $headerAccountId = $request->header('X-SAFA-ACCOUNT-ID');
        if ($headerAccountId !== null && is_numeric($headerAccountId)) {
            return (int) $headerAccountId;
        }

        $apiKey = $request->header('X-SAFA-API-KEY');
        if ($apiKey) {
            return (int) (SafaApiKey::query()
                ->where('api_key', $apiKey)
                ->where('is_active', true)
                ->value('account_id') ?? 0);
        }

        $user = $request->user() ?? $request->attributes->get('user');
        return $user ? (int) ($user->account_id ?? 0) : 0;
    }
}
