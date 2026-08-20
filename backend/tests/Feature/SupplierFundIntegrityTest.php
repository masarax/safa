<?php

namespace Tests\Feature;

use App\Http\Controllers\WebMobileFlowController;
use App\Models\Account;
use App\Models\Supplier;
use App\Models\SupplierDeposit;
use App\Models\Transaction;
use App\Models\User;
use App\Models\WalletBatch;
use App\Models\WalletLedger;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Http\Request;
use Tests\TestCase;

class SupplierFundIntegrityTest extends TestCase
{
    use RefreshDatabase;

    public function test_partial_consumption_blocks_shrinking_purchase_below_consumed_stock(): void
    {
        [$user, $account, $ledger, $deposit, $batch] = $this->supplierFund(1000, 400);

        $response = app(WebMobileFlowController::class)->updateSupplierFund($this->request('PUT', [
            'account_id' => $account->id,
            'transaction_type' => 'SAR_GIVEN',
            'amount_sar' => '50.00',
            'rate' => '10.0000',
            'paid_bdt' => '500.00',
            'ledger_id' => $ledger->id,
        ], $user), $deposit->id);

        $this->assertSame(404, $response->getStatusCode());
        $this->assertSame('1000.00', $deposit->fresh()->amount_bdt);
        $this->assertSame('1000.00', $batch->fresh()->initial_bdt);
        $this->assertSame('400.00', $batch->fresh()->remaining_bdt);
    }

    public function test_full_consumption_blocks_any_purchase_shrink(): void
    {
        [$user, $account, $ledger, $deposit, $batch] = $this->supplierFund(1000, 0);

        $response = app(WebMobileFlowController::class)->updateSupplierFund($this->request('PUT', [
            'account_id' => $account->id,
            'transaction_type' => 'SAR_DEPOSIT',
            'amount_sar' => '90.00',
            'rate' => '10.0000',
            'paid_bdt' => '900.00',
            'ledger_id' => $ledger->id,
        ], $user), $deposit->id);

        $this->assertSame(404, $response->getStatusCode());
        $this->assertSame('1000.00', $batch->fresh()->initial_bdt);
        $this->assertSame('0.00', $batch->fresh()->remaining_bdt);
        $this->assertSame('1000.00', $deposit->fresh()->amount_bdt);
    }

    public function test_purchase_to_settlement_conversion_cannot_delete_referenced_stock(): void
    {
        [$user, $account, $ledger, $deposit, $batch] = $this->supplierFund(1000, 900);
        $this->transaction($account->id, $batch->id, 100);

        $response = app(WebMobileFlowController::class)->updateSupplierFund($this->request('PUT', [
            'account_id' => $account->id,
            'transaction_type' => 'SAR_RECEIVED',
            'amount_sar' => '100.00',
            'rate' => '10.0000',
            'paid_bdt' => '1000.00',
        ], $user), $deposit->id);

        $this->assertSame(404, $response->getStatusCode());
        $this->assertSame('SAR_GIVEN', $deposit->fresh()->transaction_type);
        $this->assertNull($batch->fresh()->deleted_at);
        $this->assertSame('900.00', $batch->fresh()->remaining_bdt);
    }

    public function test_delete_supplier_fund_rolls_back_when_stock_was_consumed(): void
    {
        [$user, $account, , $deposit, $batch] = $this->supplierFund(1000, 750);

        $response = app(WebMobileFlowController::class)->deleteSupplierFund($this->request('DELETE', [
            'account_id' => $account->id,
            'confirmed' => true,
        ], $user), $deposit->id);

        $this->assertSame(404, $response->getStatusCode());
        $this->assertNull($deposit->fresh()->deleted_at);
        $this->assertNull($batch->fresh()->deleted_at);
        $this->assertSame('750.00', $batch->fresh()->remaining_bdt);
    }

    public function test_unconsumed_unreferenced_supplier_fund_can_still_be_deleted(): void
    {
        [$user, $account, , $deposit, $batch] = $this->supplierFund(1000, 1000);

        $response = app(WebMobileFlowController::class)->deleteSupplierFund($this->request('DELETE', [
            'account_id' => $account->id,
            'confirmed' => true,
        ], $user), $deposit->id);

        $this->assertSame(200, $response->getStatusCode());
        $this->assertNotNull($deposit->fresh()->deleted_at);
        $this->assertNotNull($batch->fresh()->deleted_at);
    }

    private function supplierFund(int $initial, int $remaining): array
    {
        $user = User::factory()->create([
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);
        $account = Account::create([
            'name' => 'Supplier Fund Integrity',
            'balance' => 0,
            'owner_user_id' => $user->id,
        ]);
        $supplier = Supplier::create([
            'account_id' => $account->id,
            'local_id' => 7001,
            'name' => 'Supplier',
            'phone' => '0500007001',
            'timestamp' => time(),
        ]);
        $ledger = WalletLedger::create([
            'account_id' => $account->id,
            'local_id' => 7002,
            'name' => 'Supplier Wallet',
            'timestamp' => time(),
        ]);
        $deposit = SupplierDeposit::create([
            'account_id' => $account->id,
            'local_id' => 7003,
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
            'local_id' => 7004,
            'ledger_id' => $ledger->id,
            'rate' => '10.0000',
            'initial_bdt' => number_format($initial, 2, '.', ''),
            'remaining_bdt' => number_format($remaining, 2, '.', ''),
            'supplier_id' => $supplier->id,
            'supplier_deposit_id' => $deposit->id,
            'timestamp' => time(),
        ]);

        return [$user, $account, $ledger, $deposit, $batch];
    }

    private function transaction(int $accountId, int $batchId, int $amount): Transaction
    {
        return Transaction::create([
            'account_id' => $accountId,
            'local_id' => 7100,
            'type' => 'Pending',
            'amount' => number_format($amount, 2, '.', ''),
            'amount_sar' => number_format($amount, 2, '.', ''),
            'amount_bdt' => number_format($amount, 2, '.', ''),
            'customer_rate' => '1.0000',
            'supplier_rate' => '1.0000',
            'sar_collected' => number_format($amount, 2, '.', ''),
            'bdt_disbursed' => number_format($amount, 2, '.', ''),
            'wallet_batch_id' => $batchId,
            'timestamp' => time(),
        ]);
    }

    private function request(string $method, array $payload, User $user): Request
    {
        $request = Request::create('/web/mobile/supplier-fund', $method, $payload);
        $request->setUserResolver(fn () => $user);
        return $request;
    }
}
