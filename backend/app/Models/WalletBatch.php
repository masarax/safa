<?php

namespace App\Models;

use App\Models\Concerns\RecordsSyncChanges;
use App\Models\Concerns\UsesCollisionSafeLocalId;
use App\Support\DecimalMath;
use DomainException;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\SoftDeletes;

class WalletBatch extends Model
{
    use SoftDeletes, UsesCollisionSafeLocalId, RecordsSyncChanges;

    protected $fillable = [
        'account_id', 'local_id', 'ledger_id', 'rate', 'initial_bdt', 'remaining_bdt',
        'supplier_id', 'supplier_deposit_id', 'notes', 'timestamp', 'deleted_at',
    ];

    protected $casts = [
        'rate' => 'decimal:4',
        'initial_bdt' => 'decimal:2',
        'remaining_bdt' => 'decimal:2',
    ];

    protected static function booted(): void
    {
        static::saving(function (self $batch): void {
            if (!$batch->exists) return;

            if (
                $batch->isDirty('deleted_at')
                && $batch->getRawOriginal('deleted_at') === null
                && $batch->deleted_at !== null
            ) {
                self::assertDeletionAllowed($batch);
            }

            $newInitial = $batch->initial_bdt ?? '0.00';
            $newRemaining = $batch->remaining_bdt ?? '0.00';
            if (DecimalMath::compareAmount($newRemaining, $newInitial) > 0) {
                throw new DomainException('Wallet remaining stock cannot exceed its initial stock.');
            }

            $originalInitial = $batch->getRawOriginal('initial_bdt') ?? '0.00';
            $originalRemaining = $batch->getRawOriginal('remaining_bdt') ?? '0.00';
            $consumed = DecimalMath::subtractAmount($originalInitial, $originalRemaining);
            if (DecimalMath::compareAmount($consumed, '0') > 0
                && DecimalMath::compareAmount($newInitial, $consumed) < 0) {
                throw new DomainException('Wallet initial stock cannot be reduced below already-consumed stock.');
            }
        });

        static::deleting(function (self $batch): void {
            if (!$batch->exists || $batch->isForceDeleting()) return;
            self::assertDeletionAllowed($batch);
        });
    }

    private static function assertDeletionAllowed(self $batch): void
    {
        if (!$batch->supplier_deposit_id) return;

        $consumed = DecimalMath::subtractAmount($batch->initial_bdt ?? '0.00', $batch->remaining_bdt ?? '0.00');
        $referenced = Transaction::withTrashed()
            ->where('account_id', $batch->account_id)
            ->where('wallet_batch_id', $batch->id)
            ->exists();

        if (DecimalMath::compareAmount($consumed, '0') > 0 || $referenced) {
            throw new DomainException('Supplier-funded wallet stock cannot be deleted after it has been consumed or referenced by a transaction.');
        }
    }
}
