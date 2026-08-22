<?php

namespace App\Observers;

use App\Models\SyncChange;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Support\Facades\Schema;

class SyncChangeObserver
{
    public function saved(Model $model): void
    {
        $this->record($model);
    }

    public function deleted(Model $model): void
    {
        $this->record($model, 'DELETE');
    }

    private function record(Model $model, ?string $operation = null): void
    {
        if (!Schema::hasTable('sync_changes')) return;

        $accountId = (int) $model->getAttribute('account_id');
        $recordId = (int) $model->getKey();
        if ($accountId <= 0 || $recordId <= 0) return;

        $snapshot = $model->toArray();
        $deletedAt = $model->getAttribute('deleted_at');

        SyncChange::create([
            'account_id' => $accountId,
            'entity' => $model->getTable(),
            'record_id' => $recordId,
            'operation' => $operation ?? (empty($deletedAt) ? 'UPSERT' : 'DELETE'),
            'snapshot' => $snapshot,
            'created_at' => now(),
        ]);
    }
}
