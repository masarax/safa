<?php

namespace App\Services;

use App\Models\Transaction;
use App\Models\WalletBatch;
use App\Support\DecimalMath;
use DomainException;
use Illuminate\Support\Collection;

final class TransactionWalletAccounting
{
    /**
     * Apply the complete old->new wallet inventory transition after the caller
     * has locked the transaction row. Every involved wallet row is acquired in
     * ascending primary-key order before any balance changes are made, removing
     * the opposite A->B / B->A lock-order cycle.
     */
    public function applyTransition(
        ?Transaction $existing,
        int $accountId,
        ?int $newBatchId,
        mixed $newAmountBdt,
        string $newStatus
    ): void {
        $oldBatchId = $this->restorableBatchId($existing);
        $needsDebit = $newBatchId !== null && $newBatchId > 0 && $newStatus !== 'Cancelled';

        $batchIds = array_values(array_unique(array_filter([
            $oldBatchId,
            $needsDebit ? $newBatchId : null,
        ], static fn ($id) => is_int($id) && $id > 0)));
        sort($batchIds, SORT_NUMERIC);

        /** @var Collection<int, WalletBatch> $locked */
        $locked = WalletBatch::withTrashed()
            ->where('account_id', $accountId)
            ->whereIn('id', $batchIds)
            ->orderBy('id')
            ->lockForUpdate()
            ->get()
            ->keyBy(fn (WalletBatch $batch) => (int) $batch->id);

        if ($oldBatchId !== null) {
            /** @var WalletBatch|null $oldBatch */
            $oldBatch = $locked->get($oldBatchId);
            if (!$oldBatch) throw new DomainException('Referenced wallet stock is no longer available.');
            $oldBatch->remaining_bdt = DecimalMath::addAmount($oldBatch->remaining_bdt, $existing?->amount_bdt ?? '0.00');
            $oldBatch->save();
        }

        if ($needsDebit) {
            /** @var WalletBatch|null $newBatch */
            $newBatch = $locked->get((int) $newBatchId);
            if (!$newBatch || $newBatch->trashed()) throw new DomainException('Selected wallet stock is not available.');
            if (DecimalMath::compareAmount($newBatch->remaining_bdt, $newAmountBdt) < 0) {
                throw new DomainException('Selected wallet stock does not have enough remaining BDT.');
            }

            $newBatch->remaining_bdt = DecimalMath::subtractAmount($newBatch->remaining_bdt, $newAmountBdt);
            $newBatch->save();
        }
    }

    /**
     * Backward-compatible single-side operations. New transaction mutations
     * should use applyTransition() so all involved rows are locked together.
     */
    public function restoreExisting(Transaction $transaction, int $accountId): void
    {
        $this->applyTransition($transaction, $accountId, null, '0.00', 'Cancelled');
    }

    public function debitNew(int $accountId, ?int $batchId, mixed $amountBdt, string $status): void
    {
        $this->applyTransition(null, $accountId, $batchId, $amountBdt, $status);
    }

    private function restorableBatchId(?Transaction $transaction): ?int
    {
        if (!$transaction || $transaction->trashed() || (string) $transaction->type === 'Cancelled' || !$transaction->wallet_batch_id) {
            return null;
        }

        $id = (int) $transaction->wallet_batch_id;
        return $id > 0 ? $id : null;
    }
}
