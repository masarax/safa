<?php

namespace App\Services;

use App\Models\Transaction;
use App\Models\WalletBatch;
use App\Support\DecimalMath;
use DomainException;

final class TransactionWalletAccounting
{
    public function restoreExisting(Transaction $transaction, int $accountId): void
    {
        if ((string) $transaction->type === 'Cancelled' || !$transaction->wallet_batch_id) return;

        $batch = WalletBatch::withTrashed()
            ->where('account_id', $accountId)
            ->whereKey((int) $transaction->wallet_batch_id)
            ->lockForUpdate()
            ->first();
        if (!$batch) return;

        $batch->remaining_bdt = DecimalMath::addAmount($batch->remaining_bdt, $transaction->amount_bdt);
        $batch->save();
    }

    public function debitNew(int $accountId, ?int $batchId, mixed $amountBdt, string $status): void
    {
        if (!$batchId || $status === 'Cancelled') return;

        $batch = WalletBatch::query()
            ->where('account_id', $accountId)
            ->whereKey($batchId)
            ->lockForUpdate()
            ->first();
        if (!$batch) throw new DomainException('Selected wallet stock is not available.');
        if (DecimalMath::compareAmount($batch->remaining_bdt, $amountBdt) < 0) {
            throw new DomainException('Selected wallet stock does not have enough remaining BDT.');
        }

        $batch->remaining_bdt = DecimalMath::subtractAmount($batch->remaining_bdt, $amountBdt);
        $batch->save();
    }
}
