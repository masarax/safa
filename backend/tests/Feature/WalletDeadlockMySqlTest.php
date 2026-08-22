<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\Transaction;
use App\Models\User;
use App\Models\WalletBatch;
use App\Services\TransactionWalletAccounting;
use Illuminate\Support\Facades\DB;
use Tests\TestCase;

class WalletDeadlockMySqlTest extends TestCase
{
    public function test_opposing_wallet_moves_complete_without_deadlock_or_balance_drift(): void
    {
        if (DB::connection()->getDriverName() !== 'mysql') {
            $this->markTestSkipped('MySQL concurrency regression only.');
        }

        $this->assertTrue(
            function_exists('pcntl_fork') && function_exists('pcntl_waitpid'),
            'MySQL CI must provide pcntl so the wallet deadlock regression runs concurrently.'
        );

        $user = User::factory()->create([
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);
        $account = Account::create([
            'name' => 'Concurrent Wallet Accounting ' . bin2hex(random_bytes(4)),
            'balance' => 0,
            'owner_user_id' => $user->id,
        ]);

        $seed = random_int(100_000, 900_000);
        $batchA = $this->batch($account->id, 900, $seed + 1);
        $batchB = $this->batch($account->id, 900, $seed + 2);
        $transactionA = $this->transaction($account->id, $batchA->id, 100, $seed + 3);
        $transactionB = $this->transaction($account->id, $batchB->id, 100, $seed + 4);

        $barrier = storage_path('framework/testing/wallet-deadlock-' . bin2hex(random_bytes(8)) . '.start');
        $errorA = $barrier . '.a.err';
        $errorB = $barrier . '.b.err';
        @mkdir(dirname($barrier), 0777, true);
        @unlink($barrier);
        @unlink($errorA);
        @unlink($errorB);

        // Never share a live PDO socket across forked workers.
        DB::disconnect();

        $pidA = $this->forkMove(
            $barrier,
            $errorA,
            $account->id,
            $transactionA->id,
            $batchB->id,
        );
        $pidB = $this->forkMove(
            $barrier,
            $errorB,
            $account->id,
            $transactionB->id,
            $batchA->id,
        );

        touch($barrier);

        $statusA = 0;
        $statusB = 0;
        pcntl_waitpid($pidA, $statusA);
        pcntl_waitpid($pidB, $statusB);

        DB::purge();
        DB::reconnect();

        $messageA = is_file($errorA) ? trim((string) file_get_contents($errorA)) : '';
        $messageB = is_file($errorB) ? trim((string) file_get_contents($errorB)) : '';

        @unlink($barrier);
        @unlink($errorA);
        @unlink($errorB);

        $this->assertTrue(pcntl_wifexited($statusA) && pcntl_wexitstatus($statusA) === 0, $messageA ?: 'First concurrent wallet move failed.');
        $this->assertTrue(pcntl_wifexited($statusB) && pcntl_wexitstatus($statusB) === 0, $messageB ?: 'Second concurrent wallet move failed.');

        $this->assertSame('900.00', $batchA->fresh()->remaining_bdt);
        $this->assertSame('900.00', $batchB->fresh()->remaining_bdt);
        $this->assertSame((int) $batchB->id, (int) $transactionA->fresh()->wallet_batch_id);
        $this->assertSame((int) $batchA->id, (int) $transactionB->fresh()->wallet_batch_id);
    }

    private function forkMove(
        string $barrier,
        string $errorFile,
        int $accountId,
        int $transactionId,
        int $targetBatchId,
    ): int {
        $pid = pcntl_fork();
        $this->assertGreaterThanOrEqual(0, $pid, 'Unable to fork MySQL concurrency worker.');

        if ($pid !== 0) return $pid;

        try {
            DB::purge();
            DB::reconnect();

            $deadline = microtime(true) + 10;
            while (!is_file($barrier)) {
                if (microtime(true) >= $deadline) {
                    throw new \RuntimeException('Timed out waiting for concurrent wallet start barrier.');
                }
                usleep(10_000);
            }

            DB::transaction(function () use ($accountId, $transactionId, $targetBatchId): void {
                $transaction = Transaction::query()
                    ->where('account_id', $accountId)
                    ->whereKey($transactionId)
                    ->lockForUpdate()
                    ->firstOrFail();

                app(TransactionWalletAccounting::class)->applyTransition(
                    $transaction,
                    $accountId,
                    $targetBatchId,
                    (string) $transaction->amount_bdt,
                    (string) $transaction->type,
                );

                // Keep the ordered wallet locks briefly so the other worker has
                // to contend with this transaction instead of running serially.
                usleep(150_000);

                $transaction->wallet_batch_id = $targetBatchId;
                $transaction->save();
            }, 3);

            DB::disconnect();
            exit(0);
        } catch (\Throwable $e) {
            file_put_contents($errorFile, get_class($e) . ': ' . $e->getMessage());
            DB::disconnect();
            exit(1);
        }
    }

    private function batch(int $accountId, int $remaining, int $localId): WalletBatch
    {
        return WalletBatch::create([
            'account_id' => $accountId,
            'local_id' => $localId,
            'rate' => '1.0000',
            'initial_bdt' => '1000.00',
            'remaining_bdt' => number_format($remaining, 2, '.', ''),
            'timestamp' => time(),
        ]);
    }

    private function transaction(int $accountId, int $batchId, int $amount, int $localId): Transaction
    {
        $money = number_format($amount, 2, '.', '');

        return Transaction::create([
            'account_id' => $accountId,
            'local_id' => $localId,
            'type' => 'Pending',
            'amount' => $money,
            'amount_sar' => $money,
            'customer_rate' => '1.0000',
            'supplier_rate' => '1.0000',
            'amount_bdt' => $money,
            'sar_collected' => $money,
            'bdt_disbursed' => $money,
            'wallet_batch_id' => $batchId,
            'timestamp' => time(),
        ]);
    }
}
