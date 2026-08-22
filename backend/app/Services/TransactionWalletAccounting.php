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
     * Reconcile one transaction's old wallet consumption with its new state.
     *
     * Every wallet row involved in a move is locked in ascending primary-key
     * order before any balance is changed. Opposing A→B / B→A edits therefore
     * use the same lock order and cannot create an application-level lock cycle.
     */
    public function applyTransition(
        ?Transaction $existing,
        int $accountId,
        ?int $newBatchId,
        mixed $newAmountBdt,
        string $newStatus,
    ): void {
        $oldBatchId = $this->consumedBatchId($existing);
        $newBatchId = ($newBatchId && $newStatus !== 'Cancelled') ? $newBatchId : null;

        $ids = array_values(array_unique(array_filter([$oldBatchId, $newBatchId], static fn ($id) => is_int($id) && $id > 0)));
        sort($ids, SORT_NUMERIC);
        if ($ids === []) return;

        /** @var Collection<int, WalletBatch> $batches */
        $batches = WalletBatch::withTrashed()
            ->where('account_id', $accountId)
            ->whereIn('id', $ids)
            ->orderBy('id')
            ->lockForUpdate()
            ->get()
            ->keyBy(fn (WalletBatch $batch) => (int) $batch->id);

        if ($oldBatchId !== null) {
            /** @var WalletBatch|null $oldBatch */
            $oldBatch = $batches->get($oldBatchId);
            if (!$oldBatch) throw new DomainException('Referenced wallet stock is no longer available.');
            $oldBatch->remaining_bdt = DecimalMath::addAmount($oldBatch->remaining_bdt, $existing?->amount_bdt ?? '0.00');
        }

        if ($newBatchId !== null) {
            /** @var WalletBatch|null $newBatch */
            $newBatch = $batches->get($newBatchId);
            if (!$newBatch || $newBatch->trashed()) throw new DomainException('Selected wallet stock is not available.');
            if (DecimalMath::compareAmount($newBatch->remaining_bdt, $newAmountBdt) < 0) {
                throw new DomainException('Selected wallet stock does not have enough remaining BDT.');
            }
            $newBatch->remaining_bdt = DecimalMath::subtractAmount($newBatch->remaining_bdt, $newAmountBdt);
        }

        foreach ($ids as $id) {
            /** @var WalletBatch|null $batch */
            $batch = $batches->get($id);
            if ($batch?->isDirty('remaining_bdt')) $batch->save();
        }
    }

    public function restoreExisting(Transaction $transaction, int $accountId): void
    {
        $this->applyTransition($transaction, $accountId, null, '0.00', 'Cancelled');
    }

    public function debitNew(int $accountId, ?int $batchId, mixed $amountBdt, string $status): void
    {
        $this->applyTransition(null, $accountId, $batchId, $amountBdt, $status);
    }

    private function consumedBatchId(?Transaction $transaction): ?int
    {
        if (!$transaction || $transaction->trashed() || (string) $transaction->type === 'Cancelled' || !$transaction->wallet_batch_id) {
            return null;
        }

        $id = (int) $transaction->wallet_batch_id;
        return $id > 0 ? $id : null;
    }
}
