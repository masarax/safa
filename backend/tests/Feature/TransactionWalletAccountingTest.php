<?php

namespace Tests\Feature;

use App\Http\Controllers\TransactionController;
use App\Http\Controllers\VersionedApiProxyController;
use App\Http\Controllers\WebMobileFlowController;
use App\Models\Account;
use App\Models\Customer;
use App\Models\Transaction;
use App\Models\User;
use App\Models\WalletBatch;
use App\Services\TransactionWalletAccounting;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Tests\TestCase;

class TransactionWalletAccountingTest extends TestCase
{
    use RefreshDatabase;

    public function test_generic_create_debits_wallet_stock_atomically(): void
    {
        [$user, $account] = $this->ownerAndAccount();
        $batch = $this->batch($account->id, 1000, 11);

        $response = app(TransactionController::class)->store($this->request('POST', '/api/transactions', [
            'account_id' => $account->id,
            'local_id' => 101,
            'type' => 'Pending',
            'amount_sar' => '10.00',
            'amount_bdt' => '100.00',
            'wallet_batch_id' => $batch->id,
        ], $user));

        $this->assertSame(201, $response->getStatusCode());
        $this->assertSame('900.00', $batch->fresh()->remaining_bdt);
        $this->assertSame('100.00', Transaction::where('local_id', 101)->firstOrFail()->amount_bdt);
    }

    public function test_generic_update_restores_old_batch_and_debits_new_batch_once(): void
    {
        [$user, $account] = $this->ownerAndAccount();
        $oldBatch = $this->batch($account->id, 900, 21, 1000);
        $newBatch = $this->batch($account->id, 1000, 22);
        $transaction = $this->transaction($account->id, $oldBatch->id, 100, 201);

        $response = app(TransactionController::class)->update($this->request('PUT', '/api/transactions/' . $transaction->id, [
            'account_id' => $account->id,
            'amount_bdt' => '150.00',
            'wallet_batch_id' => $newBatch->id,
        ], $user), $transaction->id);

        $this->assertSame(200, $response->getStatusCode());
        $this->assertSame('1000.00', $oldBatch->fresh()->remaining_bdt);
        $this->assertSame('850.00', $newBatch->fresh()->remaining_bdt);
        $this->assertSame($newBatch->id, (int) $transaction->fresh()->wallet_batch_id);
        $this->assertSame('150.00', $transaction->fresh()->amount_bdt);
    }

    public function test_transition_locks_all_wallet_rows_in_deterministic_primary_key_order(): void
    {
        [, $account] = $this->ownerAndAccount();
        $low = $this->batch($account->id, 900, 23, 1000);
        $high = $this->batch($account->id, 1000, 24);
        $transaction = $this->transaction($account->id, $high->id, 100, 202);
        $queries = [];
        DB::listen(function ($query) use (&$queries): void {
            if (str_contains(strtolower($query->sql), 'wallet_batches')) $queries[] = strtolower($query->sql);
        });

        app(TransactionWalletAccounting::class)->applyTransition(
            $transaction,
            $account->id,
            $low->id,
            '150.00',
            'Delivered',
        );

        $lockingQuery = collect($queries)->first(fn (string $sql) => str_contains($sql, 'order by') && str_contains($sql, 'wallet_batches'));
        $this->assertNotNull($lockingQuery);
        $this->assertStringContainsString('order by', $lockingQuery);
        $this->assertStringContainsString('id', $lockingQuery);
        $this->assertSame('750.00', $low->fresh()->remaining_bdt);
        $this->assertSame('1100.00', $high->fresh()->remaining_bdt);
    }

    public function test_insufficient_new_stock_rolls_back_transaction_and_both_batches(): void
    {
        [$user, $account] = $this->ownerAndAccount();
        $oldBatch = $this->batch($account->id, 900, 31, 1000);
        $newBatch = $this->batch($account->id, 50, 32, 50);
        $transaction = $this->transaction($account->id, $oldBatch->id, 100, 301);

        $response = app(TransactionController::class)->update($this->request('PUT', '/api/transactions/' . $transaction->id, [
            'account_id' => $account->id,
            'amount_bdt' => '150.00',
            'wallet_batch_id' => $newBatch->id,
        ], $user), $transaction->id);

        $this->assertSame(422, $response->getStatusCode());
        $this->assertSame('900.00', $oldBatch->fresh()->remaining_bdt);
        $this->assertSame('50.00', $newBatch->fresh()->remaining_bdt);
        $this->assertSame($oldBatch->id, (int) $transaction->fresh()->wallet_batch_id);
        $this->assertSame('100.00', $transaction->fresh()->amount_bdt);
    }

    public function test_delete_restores_inventory_exactly_once(): void
    {
        [$user, $account] = $this->ownerAndAccount();
        $batch = $this->batch($account->id, 900, 41, 1000);
        $transaction = $this->transaction($account->id, $batch->id, 100, 401);
        $controller = app(TransactionController::class);

        $first = $controller->destroy($this->request('DELETE', '/api/transactions/' . $transaction->id, [
            'account_id' => $account->id,
            'confirmed' => true,
        ], $user), $transaction->id);
        $this->assertSame(200, $first->getStatusCode());
        $this->assertSame('1000.00', $batch->fresh()->remaining_bdt);

        $second = $controller->destroy($this->request('DELETE', '/api/transactions/' . $transaction->id, [
            'account_id' => $account->id,
            'confirmed' => true,
        ], $user), $transaction->id);
        $this->assertSame(404, $second->getStatusCode());
        $this->assertSame('1000.00', $batch->fresh()->remaining_bdt);
    }

    public function test_recreating_soft_deleted_local_id_does_not_restore_stock_twice(): void
    {
        [$user, $account] = $this->ownerAndAccount();
        $batch = $this->batch($account->id, 1000, 51);
        $transaction = $this->transaction($account->id, $batch->id, 100, 501);
        $transaction->delete();

        $response = app(TransactionController::class)->store($this->request('POST', '/api/transactions', [
            'account_id' => $account->id,
            'local_id' => 501,
            'type' => 'Pending',
            'amount_sar' => '5.00',
            'amount_bdt' => '50.00',
            'wallet_batch_id' => $batch->id,
        ], $user));

        $this->assertSame(201, $response->getStatusCode());
        $this->assertSame('950.00', $batch->fresh()->remaining_bdt);
        $this->assertNull(Transaction::where('local_id', 501)->firstOrFail()->deleted_at);
    }

    public function test_v1_transactions_continue_through_versioned_proxy_to_canonical_mutation_route(): void
    {
        $router = app('router');
        $v1 = $router->getRoutes()->match(Request::create('/api/v1/transactions/123', 'PUT'));
        $legacy = $router->getRoutes()->match(Request::create('/api/transactions/123', 'PUT'));

        $this->assertSame(VersionedApiProxyController::class, $v1->getActionName());
        $this->assertSame(TransactionController::class . '@update', $legacy->getActionName());
    }

    public function test_purpose_built_customer_sale_still_debits_wallet_once(): void
    {
        [$user, $account] = $this->ownerAndAccount();
        $customer = Customer::create([
            'account_id' => $account->id,
            'local_id' => 601,
            'name' => 'Wallet Customer',
            'phone' => '0500000601',
            'timestamp' => time(),
        ]);
        $batch = $this->batch($account->id, 1000, 61);

        $response = app(WebMobileFlowController::class)->customerSale($this->request('POST', '/web/mobile/customer-sale', [
            'account_id' => $account->id,
            'customer_id' => $customer->id,
            'wallet_batch_id' => $batch->id,
            'amount_sar' => '10.00',
            'customer_rate' => '10.0000',
            'receiver_account_type' => 'Cash',
        ], $user));

        $this->assertSame(201, $response->getStatusCode());
        $this->assertSame('900.00', $batch->fresh()->remaining_bdt);
    }

    public function test_financial_transaction_boundaries_use_bounded_deadlock_retries(): void
    {
        $controller = (string) file_get_contents(app_path('Http/Controllers/TransactionController.php'));
        $sync = (string) file_get_contents(app_path('Services/SyncReconciliationService.php'));
        $accounting = (string) file_get_contents(app_path('Services/TransactionWalletAccounting.php'));

        $this->assertStringContainsString('DB_TRANSACTION_ATTEMPTS = 3', $controller);
        $this->assertStringContainsString('DB_TRANSACTION_ATTEMPTS = 3', $sync);
        $this->assertStringContainsString("orderBy('id')", $accounting);
        $this->assertStringContainsString('lockForUpdate()', $accounting);
        $this->assertStringContainsString("'retryable_error'", $controller);
    }

    private function ownerAndAccount(): array
    {
        $user = User::factory()->create([
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);
        $account = Account::create([
            'name' => 'Wallet Accounting',
            'balance' => 0,
            'owner_user_id' => $user->id,
        ]);

        return [$user, $account];
    }

    private function batch(int $accountId, int $remaining, int $localId, ?int $initial = null): WalletBatch
    {
        return WalletBatch::create([
            'account_id' => $accountId,
            'local_id' => $localId,
            'rate' => '1.0000',
            'initial_bdt' => number_format($initial ?? $remaining, 2, '.', ''),
            'remaining_bdt' => number_format($remaining, 2, '.', ''),
            'timestamp' => time(),
        ]);
    }

    private function transaction(int $accountId, int $batchId, int $amount, int $localId): Transaction
    {
        return Transaction::create([
            'account_id' => $accountId,
            'local_id' => $localId,
            'type' => 'Pending',
            'amount' => number_format($amount, 2, '.', ''),
            'amount_sar' => number_format($amount, 2, '.', ''),
            'customer_rate' => '1.0000',
            'supplier_rate' => '1.0000',
            'amount_bdt' => number_format($amount, 2, '.', ''),
            'sar_collected' => number_format($amount, 2, '.', ''),
            'bdt_disbursed' => number_format($amount, 2, '.', ''),
            'wallet_batch_id' => $batchId,
            'timestamp' => time(),
        ]);
    }

    private function request(string $method, string $uri, array $payload, User $user): Request
    {
        $request = Request::create($uri, $method, $payload);
        $request->setUserResolver(fn () => $user);
        return $request;
    }
}
