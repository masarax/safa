<?php

namespace App\Services;

use App\Models\SyncMutation;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\SoftDeletes;
use Illuminate\Support\Facades\DB;

class SyncReconciliationService
{
    /**
     * Apply one local-first mutation using optimistic concurrency and idempotency.
     *
     * The server owns the authoritative revision. A client may update a row only
     * when its base_version still matches the current server revision. Re-delivery
     * of the same mutation_id is always idempotent.
     */
    public function apply(
        int $accountId,
        string $entity,
        string $modelClass,
        array $payload,
        callable $attributes,
        ?callable $validate = null,
        ?string $defaultOperation = null,
    ): array {
        $localId = (int) ($payload['local_id'] ?? 0);
        $sync = is_array($payload['_sync'] ?? null) ? $payload['_sync'] : [];
        $operation = strtoupper((string) ($sync['operation'] ?? $payload['operation'] ?? $defaultOperation ?? ($this->isDeleted($payload) ? 'DELETE' : 'UPSERT')));
        $mutationId = trim((string) ($sync['mutation_id'] ?? $payload['mutation_id'] ?? ''));
        $mutationId = $mutationId !== '' ? substr($mutationId, 0, 128) : $this->fallbackMutationId($accountId, $entity, $payload, $operation);
        $baseVersion = array_key_exists('base_version', $sync)
            ? $this->positiveIntOrNull($sync['base_version'])
            : $this->positiveIntOrNull($payload['base_version'] ?? null);

        if ($localId <= 0) {
            return $this->rejected($entity, $localId, 'Missing local_id', 'VALIDATION');
        }

        if ($validate) {
            $validationError = $validate($payload);
            if ($validationError) {
                return $this->rejected($entity, $localId, $validationError, 'VALIDATION', $mutationId);
            }
        }

        return DB::transaction(function () use (
            $accountId,
            $entity,
            $modelClass,
            $payload,
            $attributes,
            $operation,
            $mutationId,
            $baseVersion,
            $localId,
        ) {
            $previousMutation = SyncMutation::where('account_id', $accountId)
                ->where('mutation_id', $mutationId)
                ->lockForUpdate()
                ->first();

            if ($previousMutation) {
                $response = $previousMutation->response ?: [
                    'local_id' => $localId,
                    'server_id' => $previousMutation->server_id,
                    'sync_version' => $previousMutation->sync_version,
                    'mutation_id' => $mutationId,
                ];
                $response['idempotent'] = true;
                return [
                    'status' => 'accepted',
                    'accepted' => $response,
                ];
            }

            /** @var Model $queryModel */
            $queryModel = new $modelClass();
            $usesSoftDeletes = in_array(SoftDeletes::class, class_uses_recursive($queryModel), true);
            $query = $usesSoftDeletes ? $modelClass::withTrashed() : $modelClass::query();
            $record = $query
                ->where('account_id', $accountId)
                ->where('local_id', $localId)
                ->lockForUpdate()
                ->first();

            $incomingTimestamp = $this->normalizeTimestamp($payload['timestamp'] ?? null);

            if ($record) {
                $currentVersion = (int) ($record->sync_version ?? 0);

                // New clients use strict optimistic concurrency. Legacy clients
                // without _sync.base_version retain the previous timestamp policy.
                if ($baseVersion !== null && $baseVersion !== $currentVersion) {
                    return $this->conflict($entity, $localId, $record, $mutationId, $currentVersion, $operation);
                }

                if ($baseVersion === null && isset($payload['timestamp']) && (int) ($record->timestamp ?? 0) > (int) $incomingTimestamp) {
                    return $this->conflict($entity, $localId, $record, $mutationId, $currentVersion, $operation, 'STALE_TIMESTAMP');
                }
            } elseif ($baseVersion !== null && $baseVersion > 0) {
                return $this->rejected($entity, $localId, 'Referenced server version does not exist', 'STALE_BASE_VERSION', $mutationId);
            }

            $record ??= new $modelClass();
            $record->account_id = $accountId;
            $record->local_id = $localId;

            $data = $attributes($payload, $accountId, $record);
            if ($data instanceof \Throwable) {
                return $this->rejected($entity, $localId, $data->getMessage(), 'DEPENDENCY', $mutationId);
            }

            foreach ($data as $key => $value) {
                $record->{$key} = $value;
            }

            $record->timestamp = $incomingTimestamp;
            $nextVersion = (int) ($record->sync_version ?? 0) + 1;
            $record->sync_version = $nextVersion;
            $record->last_mutation_id = $mutationId;

            if ($operation === 'DELETE' || $this->isDeleted($payload)) {
                $record->deleted_at = $this->parseDeletedAt($payload['deleted_at'] ?? null) ?? now();
            } elseif ($operation === 'RESTORE' || !$this->isDeleted($payload)) {
                $record->deleted_at = null;
            }

            $record->save();

            $accepted = [
                'local_id' => $localId,
                'server_id' => (int) $record->id,
                'sync_version' => $nextVersion,
                'mutation_id' => $mutationId,
                'operation' => $operation,
                'server_deleted' => $record->deleted_at !== null,
            ];

            SyncMutation::create([
                'account_id' => $accountId,
                'mutation_id' => $mutationId,
                'entity' => $entity,
                'local_id' => $localId,
                'server_id' => (int) $record->id,
                'operation' => $operation,
                'sync_version' => $nextVersion,
                'response' => $accepted,
            ]);

            return [
                'status' => 'accepted',
                'accepted' => $accepted,
            ];
        });
    }

    private function conflict(string $entity, int $localId, Model $record, string $mutationId, int $version, string $operation, string $reason = 'STALE_BASE_VERSION'): array
    {
        return [
            'status' => 'conflict',
            'conflict' => [
                'entity' => $entity,
                'local_id' => $localId,
                'server_id' => (int) $record->id,
                'reason' => $reason,
                'mutation_id' => $mutationId,
                'operation' => $operation,
                'server_version' => $version,
                'server' => $record->toArray(),
            ],
        ];
    }

    private function rejected(string $entity, int $localId, string $reason, string $code, ?string $mutationId = null): array
    {
        return [
            'status' => 'rejected',
            'rejected' => [
                'entity' => $entity,
                'local_id' => $localId,
                'reason' => $reason,
                'code' => $code,
                'mutation_id' => $mutationId,
            ],
        ];
    }

    private function isDeleted(array $payload): bool
    {
        return !empty($payload['deleted_at']) || !empty($payload['is_deleted']);
    }

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
