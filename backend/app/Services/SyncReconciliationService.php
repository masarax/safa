<?php

namespace App\Services;

use App\Models\SyncMutation;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\SoftDeletes;
use Illuminate\Support\Facades\DB;

class SyncReconciliationService
{
    public function apply(int $accountId, string $entity, string $modelClass, array $payload, callable $attributes, ?callable $validate = null, ?string $defaultOperation = null): array
    {
        $localId = (int) ($payload['local_id'] ?? 0);
        $sync = is_array($payload['_sync'] ?? null) ? $payload['_sync'] : [];
        $operation = strtoupper((string) ($sync['operation'] ?? $payload['operation'] ?? $defaultOperation ?? ($this->isDeleted($payload) ? 'DELETE' : 'UPSERT')));
        $mutationId = trim((string) ($sync['mutation_id'] ?? $payload['mutation_id'] ?? ''));
        $mutationId = $mutationId !== '' ? substr($mutationId, 0, 128) : $this->fallbackMutationId($accountId, $entity, $payload, $operation);
        $baseVersion = array_key_exists('base_version', $sync) ? $this->positiveIntOrNull($sync['base_version']) : $this->positiveIntOrNull($payload['base_version'] ?? null);

        if ($localId <= 0) return $this->rejected($entity, $localId, 'Missing local_id', 'VALIDATION');
        if (!in_array($operation, ['UPSERT', 'DELETE', 'RESTORE'], true)) return $this->rejected($entity, $localId, 'Unsupported sync operation', 'VALIDATION', $mutationId);
        if ($domainError = $this->validateDomainPayload($entity, $payload)) return $this->rejected($entity, $localId, $domainError, 'VALIDATION', $mutationId);
        if ($validate) {
            $validationError = $validate($payload);
            if ($validationError) return $this->rejected($entity, $localId, $validationError, 'VALIDATION', $mutationId);
        }

        return DB::transaction(function () use ($accountId, $entity, $modelClass, $payload, $attributes, $operation, $mutationId, $baseVersion, $localId) {
            $previousMutation = SyncMutation::where('account_id', $accountId)->where('mutation_id', $mutationId)->lockForUpdate()->first();
            if ($previousMutation) {
                $response = $previousMutation->response ?: ['local_id' => $localId, 'server_id' => $previousMutation->server_id, 'sync_version' => $previousMutation->sync_version, 'mutation_id' => $mutationId];
                $response['idempotent'] = true;
                return ['status' => 'accepted', 'accepted' => $response];
            }

            $queryModel = new $modelClass();
            $usesSoftDeletes = in_array(SoftDeletes::class, class_uses_recursive($queryModel), true);
            $query = $usesSoftDeletes ? $modelClass::withTrashed() : $modelClass::query();
            $record = $query->where('account_id', $accountId)->where('local_id', $localId)->lockForUpdate()->first();
            $incomingTimestamp = $this->normalizeTimestamp($payload['timestamp'] ?? null);

            if ($record) {
                $currentVersion = (int) ($record->sync_version ?? 0);
                if ($baseVersion !== null && $baseVersion !== $currentVersion) return $this->conflict($entity, $localId, $record, $mutationId, $currentVersion, $operation);
                if ($baseVersion === null && isset($payload['timestamp']) && (int) ($record->timestamp ?? 0) > (int) $incomingTimestamp) return $this->legacyStaleAck($entity, $localId, $record, $mutationId, $operation);
            } elseif ($baseVersion !== null && $baseVersion > 0) {
                return $this->rejected($entity, $localId, 'Referenced server version does not exist', 'STALE_BASE_VERSION', $mutationId);
            }

            $record ??= new $modelClass();
            $record->account_id = $accountId;
            $record->local_id = $localId;
            $data = $attributes($payload, $accountId, $record);
            if ($data instanceof \Throwable) return $this->rejected($entity, $localId, $data->getMessage(), 'DEPENDENCY', $mutationId);
            foreach ($data as $key => $value) $record->{$key} = $value;
            $record->timestamp = $incomingTimestamp;
            $nextVersion = (int) ($record->sync_version ?? 0) + 1;
            $record->sync_version = $nextVersion;
            $record->last_mutation_id = $mutationId;

            if ($operation === 'DELETE' || $this->isDeleted($payload)) $record->deleted_at = $this->parseDeletedAt($payload['deleted_at'] ?? null) ?? now();
            else $record->deleted_at = null;
            $record->save();

            $accepted = ['local_id' => $localId, 'server_id' => (int) $record->id, 'sync_version' => $nextVersion, 'mutation_id' => $mutationId, 'operation' => $operation, 'server_deleted' => $record->deleted_at !== null];
            SyncMutation::create(['account_id' => $accountId, 'mutation_id' => $mutationId, 'entity' => $entity, 'local_id' => $localId, 'server_id' => (int) $record->id, 'operation' => $operation, 'sync_version' => $nextVersion, 'response' => $accepted]);
            return ['status' => 'accepted', 'accepted' => $accepted];
        });
    }

    private function validateDomainPayload(string $entity, array $payload): ?string
    {
        $numericFields = match ($entity) {
            'transactions' => ['amount', 'amount_sar', 'customer_rate', 'supplier_rate', 'amount_bdt'],
            'supplier_deposits' => ['amount_sar', 'rate', 'amount_bdt', 'paid_bdt'],
            'wallet_batches' => ['rate', 'initial_bdt', 'remaining_bdt'],
            'expenses_incomes' => ['amount'],
            default => [],
        };
        foreach ($numericFields as $field) {
            if (!array_key_exists($field, $payload) || $payload[$field] === null || $payload[$field] === '') continue;
            if (is_array($payload[$field]) || is_object($payload[$field]) || !is_numeric($payload[$field])) return "Invalid numeric field: {$field}";
            if ((float) $payload[$field] < 0) return "Negative value is not allowed: {$field}";
            if (abs((float) $payload[$field]) > 1000000000000) return "Numeric value is outside the supported range: {$field}";
        }
        foreach (['customer_id', 'supplier_id', 'wallet_batch_id', 'ledger_id', 'supplier_deposit_id'] as $field) {
            if (array_key_exists($field, $payload) && $payload[$field] !== null && $payload[$field] !== '' && (!is_numeric($payload[$field]) || (int) $payload[$field] < 0)) return "Invalid identifier: {$field}";
        }
        return null;
    }

    private function legacyStaleAck(string $entity, int $localId, Model $record, string $mutationId, string $operation): array
    {
        return ['status' => 'accepted', 'accepted' => ['local_id' => $localId, 'server_id' => (int) $record->id, 'sync_version' => (int) ($record->sync_version ?? 0), 'mutation_id' => $mutationId, 'operation' => $operation, 'stale' => true, 'server_authoritative' => true]];
    }

    private function conflict(string $entity, int $localId, Model $record, string $mutationId, int $version, string $operation): array
    {
        return ['status' => 'conflict', 'conflict' => ['entity' => $entity, 'local_id' => $localId, 'server_id' => (int) $record->id, 'reason' => 'STALE_BASE_VERSION', 'mutation_id' => $mutationId, 'operation' => $operation, 'server_version' => $version, 'server' => $record->toArray()]];
    }

    private function rejected(string $entity, int $localId, string $reason, string $code, ?string $mutationId = null): array
    {
        return ['status' => 'rejected', 'rejected' => ['entity' => $entity, 'local_id' => $localId, 'reason' => $reason, 'code' => $code, 'mutation_id' => $mutationId]];
    }

    private function isDeleted(array $payload): bool { return !empty($payload['deleted_at']) || !empty($payload['is_deleted']); }

    private function positiveIntOrNull(mixed $value): ?int
    {
        if ($value === null || $value === '') return null;
        $value = (int) $value;
        return $value >= 0 ? $value : null;
    }

    private function normalizeTimestamp(mixed $raw): int
    {
        $ts = (int) ($raw ?? time());
        if ($ts <= 0) return time();
        if ($ts > 2000000000) $ts = (int) ($ts / 1000);
        return min($ts, time() + 86400);
    }

    private function parseDeletedAt(mixed $raw): ?string
    {
        if (empty($raw)) return null;
        if (is_numeric($raw)) {
            $ts = (int) $raw;
            if ($ts > 2000000000) $ts = (int) ($ts / 1000);
            return date('Y-m-d H:i:s', $ts);
        }
        return (string) $raw;
    }

    private function fallbackMutationId(int $accountId, string $entity, array $payload, string $operation): string
    {
        unset($payload['_sync'], $payload['mutation_id']);
        return hash('sha256', $accountId . '|' . $entity . '|' . ($payload['local_id'] ?? 0) . '|' . $operation . '|' . json_encode($payload, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_PRESERVE_ZERO_FRACTION));
    }
}
