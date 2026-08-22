<?php

namespace App\Observers;

use App\Models\SyncChange;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

class SyncChangeObserver
{
    public function saving(Model $model): void
    {
        // Existing direct web/API updates do not advance sync_version, so do it
        // here only for persisted rows. Fresh records intentionally remain at
        // the established version 0 baseline unless the reconciliation service
        // explicitly supplies version 1 for a mobile mutation.
        if ($model->exists && !$model->isDirty('sync_version')) {
            $model->setAttribute('sync_version', (int) $model->getOriginal('sync_version') + 1);
        }
    }

    public function saved(Model $model): void
    {
        $this->record($model);
    }

    public function deleted(Model $model): void
    {
        // Laravel SoftDeletes updates deleted_at with a direct query and does
        // not fire saving/saved. Persist the version bump explicitly before
        // recording the tombstone snapshot.
        $nextVersion = (int) $model->getAttribute('sync_version') + 1;
        DB::table($model->getTable())
            ->where($model->getKeyName(), $model->getKey())
            ->update(['sync_version' => $nextVersion]);
        $model->setAttribute('sync_version', $nextVersion);

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
