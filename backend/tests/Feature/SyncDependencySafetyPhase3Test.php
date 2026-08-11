<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\Customer;
use App\Models\SafaApiKey;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class SyncDependencySafetyPhase3Test extends TestCase
{
    use RefreshDatabase;

    private string $apiKey = 'phase3-sync-key';

    protected function setUp(): void
    {
        parent::setUp();
        $this->withoutMiddleware(\App\Http\Middleware\CheckApiSecurityKey::class);
    }

    private function account(): Account
    {
        $account = Account::create(['name' => 'Phase 3 Dependency Account']);
        SafaApiKey::create([
            'account_id' => $account->id,
            'api_key' => $this->apiKey,
            'api_secret' => 'phase3-test-secret',
            'client_name' => 'Phase 3 Test Client',
            'is_active' => true,
        ]);
        return $account;
    }

    public function test_missing_parent_dependency_is_deferred(): void
    {
        $this->account();

        $response = $this->withHeaders([
            'X-SAFA-API-KEY' => $this->apiKey,
        ])->postJson('/api/sync/up', [
            'transactions' => [[
                'local_id' => 9301,
                'customer_id' => 999901,
                'supplier_id' => 0,
                'wallet_batch_id' => 0,
                'amount' => 100,
                'amount_sar' => 100,
                'timestamp' => 1700000600,
            ]],
        ]);

        $response->assertStatus(429)
            ->assertJsonPath('code', 'SYNC_DEPENDENCY_PENDING')
            ->assertJsonPath('dependency', 'customers')
            ->assertJsonPath('dependency_local_id', 999901);

        $this->assertSame(0, \App\Models\Transaction::count());
    }

    public function test_invalid_parent_row_cannot_satisfy_child_dependency(): void
    {
        $this->account();

        $response = $this->withHeaders([
            'X-SAFA-API-KEY' => $this->apiKey,
        ])->postJson('/api/sync/up', [
            'suppliers' => [[
                'local_id' => 9401,
                'phone' => '01700009401',
                'timestamp' => 1700000700,
            ]],
            'supplier_deposits' => [[
                'local_id' => 9402,
                'supplier_id' => 9401,
                'amount_sar' => 100,
                'rate' => 30,
                'amount_bdt' => 3000,
                'timestamp' => 1700000701,
            ]],
        ]);

        $response->assertStatus(429)
            ->assertJsonPath('code', 'SYNC_DEPENDENCY_PENDING')
            ->assertJsonPath('dependency', 'suppliers')
            ->assertJsonPath('dependency_local_id', 9401);

        $this->assertSame(0, \App\Models\Supplier::count());
        $this->assertSame(0, \App\Models\SupplierDeposit::count());
    }
}
