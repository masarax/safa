<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\Supplier;
use App\Models\SupplierDeposit;
use App\Models\Transaction;
use App\Models\User;
use App\Models\WalletBatch;
use App\Models\WalletLedger;
use App\Services\SyncReconciliationService;
use DomainException;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class SyncFinancialIntegrityRegressionTest extends TestCase
{
    use RefreshDatabase;

    public function test_sync_transaction_create_move_delete_and_replay_reconcile_wallet_exactly_once(): void
    {
        [$account, $ledger, $first, $second] = $this->walletFixture();
        $service = app(SyncReconciliationService::class);
        $batchMap = [101 => $first->id, 102 => $second->id];
        $attributes = $this->transactionAttributes($batchMap);

        $create = $this->transactionPayload(501, 101, '250.00', 'CREATE', 0, 'tx-create');
        $result = $service->apply($account->id, 'transactions', Transaction::class, $create, $attributes);
        $this->assertSame('accepted', $result['status']);
        $this->assertSame('750.00', $first->fresh()->remaining_bdt);

        $replay = $service->apply($account->id, 'transactions', Transaction::class, $create, $attributes);
        $this->assertSame('accepted', $replay['status']);
        $this->assertTrue((bool) ($replay['accepted']['idempotent'] ?? false));
        $this->assertSame('750.00', $first->fresh()->remaining_bdt);

        $update = $this->transactionPayload(501, 102, '100.00', 'UPDATE', 1, 'tx-move');
        $result = $service->apply($account->id, 'transactions', Transaction::class, $update, $attributes);
        $this->assertSame('accepted', $result['status']);
        $this->assertSame('1000.00', $first->fresh()->remaining_bdt);
        $this->assertSame('400.00', $second->fresh()->remaining_bdt);

        $delete = $this->transactionPayload(501, 102, '100.00', 'DELETE', 2, 'tx-delete');
        $result = $service->apply($account->id, 'transactions', Transaction::class, $delete, $attributes);
        $this->assertSame('accepted', $result['status']);
        $this->assertTrue((bool) $result['accepted']['server_deleted']);
        $this->assertSame('500.00', $second->fresh()->remaining_bdt);
        $this->assertNotNull(Transaction::withTrashed()->where('account_id', $account->id)->where('local_id', 501)->firstOrFail()->deleted_at);
    }

    public function test_sync_transaction_insufficient_stock_rolls_back_old_and_new_wallet_state(): void
    {
        [$account, , $first, $second] = $this->walletFixture();
        $service = app(SyncReconciliationService::class);
        $attributes = $this->transactionAttributes([101 => $first->id, 102 => $second->id]);

        $service->apply(
            $account->id,
            'transactions',
            Transaction::class,
            $this->transactionPayload(502, 101, '250.00', 'CREATE', 0, 'tx-create-rollback'),
            $attributes,
        );
        $this->assertSame('750.00', $first->fresh()->remaining_bdt);

        $rejected = $service->apply(
            $account->id,
            'transactions',
            Transaction::class,
            $this->transactionPayload(502, 102, '600.00', 'UPDATE', 1, 'tx-too-large'),
            $attributes,
        );

        $this->assertSame('rejected', $rejected['status']);
        $this->assertSame('750.00', $first->fresh()->remaining_bdt);
        $this->assertSame('500.00', $second->fresh()->remaining_bdt);
        $transaction = Transaction::query()->where('account_id', $account->id)->where('local_id', 502)->firstOrFail();
        $this->assertSame($first->id, (int) $transaction->wallet_batch_id);
        $this->assertSame('250.00', $transaction->amount_bdt);
    }

    public function test_wallet_ledger_with_positive_balance_cannot_be_deleted_directly_or_by_sync(): void
    {
        [$account, $ledger, $first] = $this->walletFixture();

        try {
            $ledger->delete();
            $this->fail('Positive-balance ledger delete should fail.');
        } catch (DomainException) {
            $this->assertNull($ledger->fresh()->deleted_at);
        }

        $result = app(SyncReconciliationService::class)->apply(
            $account->id,
            'wallet_ledgers',
            WalletLedger::class,
            [
                'local_id' => $ledger->local_id,
                'name' => $ledger->name,
                'timestamp' => time(),
                '_sync' => ['operation' => 'DELETE', 'base_version' => 0, 'mutation_id' => 'ledger-delete-blocked'],
            ],
            fn (array $payload) => ['name' => (string) $payload['name']],
        );

        $this->assertSame('rejected', $result['status']);
        $this->assertNull($ledger->fresh()->deleted_at);
        $this->assertNull($first->fresh()->deleted_at);
    }

    public function test_consumed_supplier_funded_wallet_batch_cannot_be_tombstoned_by_sync(): void
    {
        [$account, $ledger] = $this->walletFixture();
        $supplier = Supplier::create([
            'account_id' => $account->id,
            'local_id' => 301,
            'name' => 'Supplier',
            'phone' => '0500000301',
            'timestamp' => time(),
        ]);
        $deposit = SupplierDeposit::create([
            'account_id' => $account->id,
            'local_id' => 302,
            'supplier_id' => $supplier->id,
            'amount_sar' => '100.00',
            'rate' => '10.0000',
            'amount_bdt' => '1000.00',
            'paid_bdt' => '1000.00',
            'transaction_type' => 'SAR_GIVEN',
            'timestamp' => time(),
        ]);
        $batch = WalletBatch::create([
            'account_id' => $account->id,
            'local_id' => 303,
            'ledger_id' => $ledger->id,
            'rate' => '10.0000',
            'initial_bdt' => '1000.00',
            'remaining_bdt' => '500.00',
            'supplier_id' => $supplier->id,
            'supplier_deposit_id' => $deposit->id,
            'timestamp' => time(),
        ]);

        $result = app(SyncReconciliationService::class)->apply(
            $account->id,
            'wallet_batches',
            WalletBatch::class,
            [
                'local_id' => $batch->local_id,
                'ledger_id' => $ledger->local_id,
                'rate' => '10.0000',
                'initial_bdt' => '1000.00',
                'remaining_bdt' => '500.00',
                'supplier_id' => $supplier->local_id,
                'supplier_deposit_id' => $deposit->local_id,
                'timestamp' => time(),
                '_sync' => ['operation' => 'DELETE', 'base_version' => 0, 'mutation_id' => 'batch-delete-blocked'],
            ],
            fn () => [
                'ledger_id' => $ledger->id,
                'rate' => '10.0000',
                'initial_bdt' => '1000.00',
                'remaining_bdt' => '500.00',
                'supplier_id' => $supplier->id,
                'supplier_deposit_id' => $deposit->id,
            ],
        );

        $this->assertSame('rejected', $result['status']);
        $this->assertNull($batch->fresh()->deleted_at);
    }

    public function test_zero_balance_ledger_delete_tombstones_its_deletable_batches(): void
    {
        [$account] = $this->walletFixture();
        $ledger = WalletLedger::create([
            'account_id' => $account->id,
            'local_id' => 401,
            'name' => 'Empty Wallet',
            'timestamp' => time(),
        ]);
        $batch = WalletBatch::create([
            'account_id' => $account->id,
            'local_id' => 402,
            'ledger_id' => $ledger->id,
            'rate' => '1.0000',
            'initial_bdt' => '0.00',
            'remaining_bdt' => '0.00',
            'timestamp' => time(),
        ]);

        $ledger->delete();

        $this->assertNotNull($ledger->fresh()->deleted_at);
        $this->assertNotNull(WalletBatch::withTrashed()->findOrFail($batch->id)->deleted_at);
    }

    private function walletFixture(): array
    {
        $user = User::factory()->create(['is_activated' => true]);
        $account = Account::create(['name' => 'Wallet Audit', 'balance' => 0, 'owner_user_id' => $user->id]);
        $ledger = WalletLedger::create([
            'account_id' => $account->id,
            'local_id' => 100,
            'name' => 'Primary Wallet',
            'timestamp' => time(),
        ]);
        $first = WalletBatch::create([
            'account_id' => $account->id,
            'local_id' => 101,
            'ledger_id' => $ledger->id,
            'rate' => '1.0000',
            'initial_bdt' => '1000.00',
            'remaining_bdt' => '1000.00',
            'timestamp' => time(),
        ]);
        $second = WalletBatch::create([
            'account_id' => $account->id,
            'local_id' => 102,
            'ledger_id' => $ledger->id,
            'rate' => '1.0000',
            'initial_bdt' => '500.00',
            'remaining_bdt' => '500.00',
            'timestamp' => time(),
        ]);

        return [$account, $ledger, $first, $second];
    }

    private function transactionPayload(int $localId, int $batchLocalId, string $amountBdt, string $operation, int $baseVersion, string $mutationId): array
    {
        return [
            'local_id' => $localId,
            'type' => 'Pending',
            'amount' => '25.00',
            'amount_sar' => '25.00',
            'customer_rate' => '1.0000',
            'supplier_rate' => '1.0000',
            'amount_bdt' => $amountBdt,
            'sar_collected' => '25.00',
            'bdt_disbursed' => $amountBdt,
            'wallet_batch_id' => $batchLocalId,
            'timestamp' => time(),
            '_sync' => [
                'operation' => $operation,
                'base_version' => $baseVersion,
                'mutation_id' => $mutationId,
            ],
        ];
    }

    private function transactionAttributes(array $batchMap): callable
    {
        return function (array $payload) use ($batchMap): array {
            return [
                'type' => (string) ($payload['type'] ?? 'Pending'),
                'amount' => (string) ($payload['amount'] ?? $payload['amount_sar'] ?? '0.00'),
                'amount_sar' => (string) ($payload['amount_sar'] ?? '0.00'),
                'customer_rate' => (string) ($payload['customer_rate'] ?? '0.0000'),
                'supplier_rate' => (string) ($payload['supplier_rate'] ?? '0.0000'),
                'amount_bdt' => (string) ($payload['amount_bdt'] ?? '0.00'),
                'sar_collected' => (string) ($payload['sar_collected'] ?? '0.00'),
                'bdt_disbursed' => (string) ($payload['bdt_disbursed'] ?? '0.00'),
                'wallet_batch_id' => $batchMap[(int) ($payload['wallet_batch_id'] ?? 0)] ?? null,
            ];
        };
    }
}
