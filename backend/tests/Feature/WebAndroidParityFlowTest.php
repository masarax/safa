<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\Customer;
use App\Models\Supplier;
use App\Models\SupplierDeposit;
use App\Models\SystemSetting;
use App\Models\Transaction;
use App\Models\User;
use App\Models\WalletBatch;
use App\Models\WalletLedger;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class WebAndroidParityFlowTest extends TestCase
{
    use RefreshDatabase;

    private function user(string $role, string $mobile): array
    {
        $hash = Hash::make('123456');
        $user = User::factory()->create([
            'name' => User::roleLabel($role) . ' Flow Tester',
            'email' => $mobile . '@example.test',
            'mobile' => $mobile,
            'pin_hash' => $hash,
            'password' => $hash,
            'role' => $role,
            'is_activated' => true,
        ]);
        $account = Account::create(['owner_user_id' => $user->id, 'name' => 'Parity Business', 'balance' => '0.00']);
        return [$user, $account];
    }

    private function customer(Account $account): Customer
    {
        return Customer::create(['account_id' => $account->id, 'local_id' => 101, 'name' => 'Customer One', 'phone' => '01700000001', 'address' => 'Dhaka', 'timestamp' => time()]);
    }

    private function supplier(Account $account): Supplier
    {
        return Supplier::create(['account_id' => $account->id, 'local_id' => 201, 'name' => 'Supplier One', 'phone' => '01800000001', 'address' => 'Riyadh', 'timestamp' => time()]);
    }

    private function ledger(Account $account, string $name = 'Main Wallet'): WalletLedger
    {
        return WalletLedger::create(['account_id' => $account->id, 'local_id' => random_int(1000, 9999), 'name' => $name, 'timestamp' => time()]);
    }

    private function batch(Account $account, WalletLedger $ledger, string $amount, string $rate = '32.0000'): WalletBatch
    {
        return WalletBatch::create([
            'account_id' => $account->id,
            'local_id' => random_int(10000, 99999),
            'ledger_id' => $ledger->id,
            'rate' => $rate,
            'initial_bdt' => $amount,
            'remaining_bdt' => $amount,
            'timestamp' => time(),
        ]);
    }

    public function test_web_primary_navigation_matches_android_main_screens_and_settings_is_a_subpage(): void
    {
        [$admin] = $this->user(User::ROLE_ADMIN, '0536309201');
        $html = (string) $this->actingAs($admin)->get('/app')->assertOk()->getContent();

        foreach (['dashboard', 'customers', 'suppliers', 'wallet', 'expenses'] as $screen) {
            $this->assertStringContainsString('data-nav="' . $screen . '"', $html);
        }
        foreach (['transactions', 'settings', 'users', 'reports'] as $notMain) {
            $this->assertStringNotContainsString('data-nav="' . $notMain . '"', $html);
        }
        $this->assertStringContainsString('data-open-settings', $html);
        $this->assertStringContainsString('id="profile-settings-form"', $html);
        $this->assertStringContainsString('id="brand-business-config"', $html);
        $this->assertStringNotContainsString('name="captain_name"', $html);

        preg_match('/<article[^>]+id="brand-business-config".*?<\/article>/s', $html, $brandSection);
        $this->assertNotEmpty($brandSection);
        $this->assertStringNotContainsString('name="name"', $brandSection[0]);
        $this->assertStringNotContainsString('name="mobile"', $brandSection[0]);
    }

    public function test_own_name_and_mobile_are_updated_only_through_personal_profile_settings(): void
    {
        [$admin] = $this->user(User::ROLE_ADMIN, '0536309202');
        SystemSetting::create(['app_name' => 'SAFA Business', 'app_logo_url' => '/safa-logo.png', 'app_version' => '1.0.0', 'local_currency' => 'BDT', 'foreign_currency' => 'SAR', 'rate_based_mode' => true, 'supplier_rate_enabled' => true, 'wallet_rate_enabled' => true]);

        $this->actingAs($admin)->postJson('/app/api/settings/profile', [
            'name' => 'Updated Personal Name',
            'mobile' => '0536309299',
        ])->assertOk()->assertJsonPath('user.name', 'Updated Personal Name');

        $admin->refresh();
        $this->assertSame('Updated Personal Name', $admin->name);
        $this->assertSame('0536309299', $admin->mobile);
        $this->assertSame('SAFA Business', SystemSetting::firstOrFail()->app_name);
    }

    public function test_customer_sale_due_adjustment_status_and_delete_reconcile_wallet_like_android(): void
    {
        [$admin, $account] = $this->user(User::ROLE_ADMIN, '0536309203');
        $customer = $this->customer($account);
        $ledger = $this->ledger($account);
        $batch = $this->batch($account, $ledger, '1000.00', '32.0000');

        $response = $this->actingAs($admin)->postJson('/app/api/mobile/customer-sale', [
            'customer_id' => $customer->id,
            'wallet_batch_id' => $batch->id,
            'amount_sar' => '10.00',
            'customer_rate' => '32.1000',
            'sar_collected' => '8.00',
            'receiver_account_type' => 'Bkash',
            'receiver_account_no' => '01711111111',
            'receiver_phone' => '01711111111',
            'notes' => 'Android style sale',
            'due_adjustment_type' => 'due',
            'due_adjustment_amount' => '3.00',
        ])->assertCreated();

        $saleId = (int) $response->json('transaction.id');
        $this->assertSame('679.00', $batch->fresh()->remaining_bdt);
        $this->assertDatabaseHas('transactions', ['id' => $saleId, 'type' => 'Pending', 'amount_sar' => '10.00', 'amount_bdt' => '321.00', 'sar_collected' => '8.00']);
        $this->assertDatabaseHas('transactions', ['account_id' => $account->id, 'customer_id' => $customer->id, 'type' => 'Delivered', 'amount_sar' => '0.00', 'sar_collected' => '3.00', 'receiver_name' => 'Due Payment']);

        $this->actingAs($admin)->patchJson("/app/api/mobile/transactions/{$saleId}/status", ['status' => 'Cancelled'])->assertOk();
        $this->assertSame('1000.00', $batch->fresh()->remaining_bdt);

        $this->actingAs($admin)->patchJson("/app/api/mobile/transactions/{$saleId}/status", ['status' => 'Pending'])->assertOk();
        $this->assertSame('679.00', $batch->fresh()->remaining_bdt);

        $this->actingAs($admin)->deleteJson("/app/api/mobile/transactions/{$saleId}", ['confirmed' => true])->assertOk();
        $this->assertSame('1000.00', $batch->fresh()->remaining_bdt);
        $this->assertSoftDeleted('transactions', ['id' => $saleId]);
    }

    public function test_advance_return_is_a_zero_principal_negative_collection_entry(): void
    {
        [$admin, $account] = $this->user(User::ROLE_ADMIN, '0536309204');
        $customer = $this->customer($account);

        $this->actingAs($admin)->postJson('/app/api/mobile/customer-adjustment', [
            'customer_id' => $customer->id,
            'kind' => 'advance',
            'amount_sar' => '7.50',
        ])->assertCreated();

        $this->assertDatabaseHas('transactions', [
            'account_id' => $account->id,
            'customer_id' => $customer->id,
            'type' => 'Delivered',
            'amount_sar' => '0.00',
            'sar_collected' => '-7.50',
            'receiver_name' => 'Advance Return',
        ]);
    }

    public function test_supplier_purchase_creates_linked_wallet_batch_but_settlement_does_not(): void
    {
        [$admin, $account] = $this->user(User::ROLE_ADMIN, '0536309205');
        $supplier = $this->supplier($account);
        $ledger = $this->ledger($account);

        $purchase = $this->actingAs($admin)->postJson('/app/api/mobile/supplier-funds', [
            'supplier_id' => $supplier->id,
            'transaction_type' => 'SAR_GIVEN',
            'amount_sar' => '10.00',
            'rate' => '32.0000',
            'paid_bdt' => '300.00',
            'ledger_id' => $ledger->id,
            'notes' => 'Fund purchase',
        ])->assertCreated();

        $depositId = (int) $purchase->json('supplier_deposit.id');
        $batchId = (int) $purchase->json('wallet_batch.id');
        $this->assertDatabaseHas('supplier_deposits', ['id' => $depositId, 'amount_bdt' => '320.00', 'paid_bdt' => '300.00', 'transaction_type' => 'SAR_GIVEN']);
        $this->assertDatabaseHas('wallet_batches', ['id' => $batchId, 'supplier_deposit_id' => $depositId, 'initial_bdt' => '320.00', 'remaining_bdt' => '320.00']);

        $settlement = $this->actingAs($admin)->postJson('/app/api/mobile/supplier-funds', [
            'supplier_id' => $supplier->id,
            'transaction_type' => 'SAR_RECEIVED',
            'amount_sar' => '2.00',
            'rate' => '32.0000',
            'paid_bdt' => '64.00',
        ])->assertCreated();
        $this->assertNull($settlement->json('wallet_batch'));
        $this->assertSame(1, WalletBatch::query()->where('account_id', $account->id)->whereNull('deleted_at')->count());

        $this->actingAs($admin)->patchJson("/app/api/mobile/supplier-funds/{$depositId}", [
            'transaction_type' => 'SAR_GIVEN', 'amount_sar' => '12.00', 'rate' => '32.0000', 'paid_bdt' => '300.00', 'ledger_id' => $ledger->id,
        ])->assertOk();
        $this->assertDatabaseHas('wallet_batches', ['id' => $batchId, 'initial_bdt' => '384.00', 'remaining_bdt' => '384.00']);

        $this->actingAs($admin)->deleteJson("/app/api/mobile/supplier-funds/{$depositId}", ['confirmed' => true])->assertOk();
        $this->assertSoftDeleted('supplier_deposits', ['id' => $depositId]);
        $this->assertSoftDeleted('wallet_batches', ['id' => $batchId]);
    }

    public function test_wallet_withdrawal_is_fifo_and_nonempty_ledger_cannot_be_deleted(): void
    {
        [$admin, $account] = $this->user(User::ROLE_ADMIN, '0536309206');
        $ledger = $this->ledger($account);
        $first = $this->batch($account, $ledger, '100.00');
        $first->timestamp = time() - 100; $first->save();
        $second = $this->batch($account, $ledger, '50.00');

        $this->actingAs($admin)->postJson('/app/api/mobile/wallet-withdraw', ['ledger_id' => $ledger->id, 'amount_bdt' => '120.00'])->assertOk();
        $this->assertSame('0.00', $first->fresh()->remaining_bdt);
        $this->assertSame('30.00', $second->fresh()->remaining_bdt);

        $this->actingAs($admin)->deleteJson("/app/api/mobile/wallet-ledgers/{$ledger->id}", ['confirmed' => true])->assertUnprocessable();
        $this->actingAs($admin)->postJson('/app/api/mobile/wallet-withdraw', ['ledger_id' => $ledger->id, 'amount_bdt' => '30.00'])->assertOk();
        $this->actingAs($admin)->deleteJson("/app/api/mobile/wallet-ledgers/{$ledger->id}", ['confirmed' => true])->assertOk();
        $this->assertSoftDeleted('wallet_ledgers', ['id' => $ledger->id]);
    }

    public function test_business_user_workspace_can_read_transaction_wallet_stock_and_supplier_ledger_without_wallet_management_permission(): void
    {
        [$business, $account] = $this->user(User::ROLE_BUSINESS_USER, '0536309207');
        $supplier = $this->supplier($account);
        $ledger = $this->ledger($account);
        $batch = $this->batch($account, $ledger, '500.00');
        SupplierDeposit::create(['account_id' => $account->id, 'local_id' => 999, 'supplier_id' => $supplier->id, 'amount_sar' => '5.00', 'rate' => '32.0000', 'amount_bdt' => '160.00', 'paid_bdt' => '160.00', 'transaction_type' => 'SAR_GIVEN', 'timestamp' => time()]);

        $response = $this->actingAs($business)->getJson('/app/api/mobile/workspace')->assertOk();
        $this->assertFalse((bool) $response->json('permissions.can_manage_wallet'));
        $this->assertSame($ledger->id, $response->json('wallet_ledgers.0.id'));
        $this->assertSame($batch->id, $response->json('wallet_batches.0.id'));
        $this->assertSame($supplier->id, $response->json('supplier_deposits.0.supplier_id'));
    }

    public function test_release_build_configuration_allows_unsigned_local_assemble_release_without_weakening_release_hardening(): void
    {
        $gradle = (string) file_get_contents(base_path('../app/build.gradle.kts'));
        $this->assertStringContainsString('releaseSigningConfigured', $gradle);
        $this->assertStringContainsString('if (releaseSigningConfigured) signingConfig = signingConfigs.getByName("release")', $gradle);
        $this->assertStringContainsString('isMinifyEnabled = true', $gradle);
        $this->assertStringContainsString('isShrinkResources = true', $gradle);
        $workflow = (string) file_get_contents(base_path('../.github/workflows/android-ci.yml'));
        $this->assertStringContainsString('testDebugUnitTest lintDebug', $workflow);
        $this->assertStringContainsString('assembleRelease', $workflow);
    }
}
