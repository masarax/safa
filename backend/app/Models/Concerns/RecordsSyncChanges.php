<?php

namespace App\Models\Concerns;

use Illuminate\Database\Eloquent\Model;
use Illuminate\Support\Facades\DB;

/**
 * Writes an account-scoped monotonic change cursor after every durable model
 * mutation. The row is inserted on the same database connection/transaction as
 * the business write, so a committed mutation cannot exist without a cursor.
 */
trait RecordsSyncChanges
{
    public static function bootRecordsSyncChanges(): void
    {
        static::saved(static function (Model $model): void {
            self::recordSyncChange($model);
        });

        static::deleted(static function (Model $model): void {
            self::recordSyncChange($model);
        });

        static::restored(static function (Model $model): void {
            self::recordSyncChange($model);
        });
    }

    private static function recordSyncChange(Model $model): void
    {
        $accountId = (int) $model->getAttribute('account_id');
        $entityId = (int) $model->getKey();
        if ($accountId <= 0 || $entityId <= 0) return;

        $now = now();
        DB::table('sync_changes')->insert([
            'account_id' => $accountId,
            'entity' => $model->getTable(),
            'entity_id' => $entityId,
            'created_at' => $now,
            'updated_at' => $now,
        ]);
    }
}
