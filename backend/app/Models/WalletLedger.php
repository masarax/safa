<?php

namespace App\Models;

use App\Models\Concerns\RecordsSyncChanges;
use App\Models\Concerns\UsesCollisionSafeLocalId;
use App\Support\DecimalMath;
use DomainException;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\SoftDeletes;

class WalletLedger extends Model
{
    use SoftDeletes, UsesCollisionSafeLocalId, RecordsSyncChanges;

    protected $fillable = [
        'account_id',
        'local_id',
        'name',
        'timestamp',
        'deleted_at',
    ];

    protected static function booted(): void
    {
        static::saving(function (self $ledger): void {
            if (
                $ledger->exists
                && $ledger->isDirty('deleted_at')
                && $ledger->getRawOriginal('deleted_at') === null
                && $ledger->deleted_at !== null
            ) {
                self::prepareDeletion($ledger);
            }
        });

        static::deleting(function (self $ledger): void {
            if (!$ledger->exists || $ledger->isForceDeleting()) return;
            self::prepareDeletion($ledger);
        });
    }

    private static function prepareDeletion(self $ledger): void
    {
        $batches = WalletBatch::query()
            ->where('account_id', $ledger->account_id)
            ->where('ledger_id', $ledger->id)
            ->whereNull('deleted_at')
            ->get();

        $balance = '0.00';
        foreach ($batches as $batch) {
            $balance = DecimalMath::addAmount($balance, $batch->remaining_bdt ?? '0.00');
        }

        if (DecimalMath::compareAmount($balance, '0') > 0) {
            throw new DomainException('Wallet ledger cannot be deleted while it still has a balance.');
        }

        foreach ($batches as $batch) {
            $batch->delete();
        }
    }
}
