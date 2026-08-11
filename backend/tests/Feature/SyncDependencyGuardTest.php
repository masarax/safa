<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\SafaApiKey;
use App\Models\Customer;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class SyncDependencyGuardTest extends TestCase
{
    use RefreshDatabase;

    private string $apiKey = 'dependency-guard-key';

    protected function setUp(): void
    {
        parent::setUp();
        $this->withoutMiddleware(\App\Http\Middleware\CheckApiSecurityKey::class);
    }

    private function account(): Account
    {
        $account = Account::create(['name' => 'Dependency Guard Account']);
        SafaApiKey::create([
            'account_id' => $account->id,
            'api_key' => $this->apiKey,
            'api_secret' => 'dependency-guard-secret',
            'client_name' => 'Dependency Guard Test',
            'is_active' => true,
        ]);
        return $account;
    }

    public function test_child_can_reference_parent_in_same_batch(): void
    {
        $this->account();

        $response = $this->withHeaders(['X-SAFA-API-KEY' => $this->apiKey])->postJson('/api/sync/up', [
            'customers' => [[
                'local_id' => 10001,
                'name' => 'Same Batch Customer',
                'timestamp' => 1700001000,
            ]],
            'transactions' => [[
                'local_id' => 10002,
                'customer_id' => 10001,
                'supplier_id' => 0,
                'wallet_batch_id' => 0,
                'type' => 'Pending',
                'amount' => 100,
                'timestamp' => 1700001001,
            ]],
        ]);

        $response->assertOk();
        $response->assertJsonPath('status', 'success');
    }

    public function test_missing_parent_returns_retryable_dependency_response(): void
    {
        $this->account();

        $response = $this->withHeaders(['X-SAFA-API-KEY' => $this->apiKey])->postJson('/api/sync/up', [
            'transactions' => [[
                'local_id' => 10003,
                'customer_id' => 999999,
                'supplier_id' => 0,
                'wallet_batch_id' => 0,
                'type' => 'Pending',
                'amount' => 100,
                'timestamp' => 1700001002,
            ]],
        ]);

        $response->assertStatus(429);
        $response->assertJsonPath('code', 'SYNC_DEPENDENCY_PENDING');
        $response->assertHeader('Retry-After', '2');
        $this->assertSame(0, Customer::where('local_id', 999999)->count());
    }
}
