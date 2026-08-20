<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\Supplier;
use App\Models\User;
use App\Models\UserAccountShare;
use App\Models\WalletLedger;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class SyncPermissionBoundaryTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        $this->withoutMiddleware();
    }

    public function test_normal_user_cannot_read_or_mutate_supplier_and_wallet_sync_entities(): void
    {
        $user = User::factory()->create(['role' => User::ROLE_USER, 'is_activated' => true]);
        $account = Account::create(['name' => 'Normal User Account', 'balance' => 0, 'owner_user_id' => $user->id]);
        Supplier::create(['account_id' => $account->id, 'local_id' => 9001, 'name' => 'Hidden Supplier', 'phone' => '', 'timestamp' => time()]);
        WalletLedger::create(['account_id' => $account->id, 'local_id' => 9002, 'name' => 'Hidden Wallet', 'timestamp' => time()]);

        $down = $this->actingAs($user)
            ->withHeader('X-SAFA-ACCOUNT-ID', (string) $account->id)
            ->getJson('/api/sync/down')
            ->assertOk();

        $this->assertSame([], $down->json('suppliers'));
        $this->assertSame([], $down->json('wallet_ledgers'));
        $this->assertFalse($down->json('permissions.can_view_suppliers'));
        $this->assertFalse($down->json('permissions.can_manage_wallet'));

        $up = $this->actingAs($user)
            ->withHeader('X-SAFA-ACCOUNT-ID', (string) $account->id)
            ->postJson('/api/sync/up', [
                'suppliers' => [['local_id' => 9010, 'name' => 'Blocked Supplier', 'timestamp' => time()]],
                'wallet_ledgers' => [['local_id' => 9011, 'name' => 'Blocked Wallet', 'timestamp' => time()]],
            ])
            ->assertOk();

        $this->assertSame('FORBIDDEN', $up->json('rejected.0.code'));
        $this->assertSame('FORBIDDEN', $up->json('rejected.1.code'));
        $this->assertDatabaseMissing('suppliers', ['account_id' => $account->id, 'local_id' => 9010]);
        $this->assertDatabaseMissing('wallet_ledgers', ['account_id' => $account->id, 'local_id' => 9011]);
    }

    public function test_share_overrides_narrow_admin_sync_permissions(): void
    {
        $owner = User::factory()->create(['role' => User::ROLE_SUPERADMIN, 'is_activated' => true]);
        $member = User::factory()->create(['role' => User::ROLE_ADMIN, 'is_activated' => true]);
        $account = Account::create(['name' => 'Shared Account', 'balance' => 0, 'owner_user_id' => $owner->id]);
        UserAccountShare::create([
            'owner_user_id' => $owner->id,
            'account_id' => $account->id,
            'shared_with_user_id' => $member->id,
            'permissions_override' => ['can_view_suppliers' => false, 'can_manage_wallet' => false],
        ]);
        Supplier::create(['account_id' => $account->id, 'local_id' => 9101, 'name' => 'Restricted Supplier', 'phone' => '', 'timestamp' => time()]);
        WalletLedger::create(['account_id' => $account->id, 'local_id' => 9102, 'name' => 'Restricted Wallet', 'timestamp' => time()]);

        $down = $this->actingAs($member)
            ->withHeader('X-SAFA-ACCOUNT-ID', (string) $account->id)
            ->getJson('/api/v1/sync/down?page=1&per_page=50')
            ->assertOk();

        $this->assertSame([], $down->json('suppliers'));
        $this->assertSame([], $down->json('wallet_ledgers'));
        $this->assertSame(0, $down->json('meta.suppliers.total'));
        $this->assertFalse($down->json('permissions.can_view_suppliers'));
        $this->assertFalse($down->json('permissions.can_manage_wallet'));
    }
}
