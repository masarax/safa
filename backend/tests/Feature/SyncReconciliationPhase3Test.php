<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\Customer;
use App\Models\SafaApiKey;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class SyncReconciliationPhase3Test extends TestCase
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
        $account = Account::create(['name' => 'Phase 3 Account']);
        SafaApiKey::create([
            'account_id' => $account->id,
            'api_key' => $this->apiKey,
            'api_secret' => 'phase3-test-secret',
            'client_name' => 'Phase 3 Test Client',
            'is_active' => true,
        ]);
        return $account;
    }

    private function sync(array $payload)
    {
        return $this->withHeaders(['X-SAFA-API-KEY' => $this->apiKey])->postJson('/api/sync/up', $payload);
    }

    public function test_server_revision_increments_and_stale_base_version_is_rejected(): void
    {
        $account = $this->account();

        $create = $this->sync([
            'customers' => [[
                'local_id' => 7001,
                'name' => 'Original',
                'phone' => '01700007001',
                'timestamp' => 1700000100,
                '_sync' => ['mutation_id' => 'm-create-7001', 'base_version' => 0, 'operation' => 'CREATE'],
            ]],
        ])->assertOk();

        $create->assertJsonPath('accepted.customers.0.sync_version', 1);

        $update = $this->sync([
            'customers' => [[
                'local_id' => 7001,
                'name' => 'Server Update',
                'phone' => '01700007001',
                'timestamp' => 1700000200,
                '_sync' => ['mutation_id' => 'm-update-7001', 'base_version' => 1, 'operation' => 'UPDATE'],
            ]],
        ])->assertOk();

        $update->assertJsonPath('accepted.customers.0.sync_version', 2);

        $stale = $this->sync([
            'customers' => [[
                'local_id' => 7001,
                'name' => 'Stale Device Update',
                'phone' => '01700007001',
                'timestamp' => 1700000300,
                '_sync' => ['mutation_id' => 'm-stale-7001', 'base_version' => 1, 'operation' => 'UPDATE'],
            ]],
        ])->assertOk();

        $stale->assertJsonPath('status', 'conflict');
        $stale->assertJsonPath('conflicts.0.server_version', 2);
        $stale->assertJsonPath('conflicts.0.server.name', 'Server Update');

        $this->assertSame('Server Update', Customer::where('account_id', $account->id)->where('local_id', 7001)->value('name'));
        $this->assertSame(2, (int) Customer::where('account_id', $account->id)->where('local_id', 7001)->value('sync_version'));
    }

    public function test_duplicate_mutation_id_is_idempotent_and_does_not_increment_revision(): void
    {
        $account = $this->account();
        $payload = [
            'customers' => [[
                'local_id' => 7002,
                'name' => 'Idempotent',
                'phone' => '01700007002',
                'timestamp' => 1700000400,
                '_sync' => ['mutation_id' => 'same-mutation-7002', 'base_version' => 0, 'operation' => 'CREATE'],
            ]],
        ];

        $first = $this->sync($payload)->assertOk();
        $second = $this->sync($payload)->assertOk();

        $first->assertJsonPath('accepted.customers.0.sync_version', 1);
        $second->assertJsonPath('accepted.customers.0.sync_version', 1);
        $second->assertJsonPath('accepted.customers.0.idempotent', true);

        $this->assertSame(1, Customer::where('account_id', $account->id)->where('local_id', 7002)->count());
        $this->assertSame(1, (int) Customer::where('account_id', $account->id)->where('local_id', 7002)->value('sync_version'));
    }

    public function test_unresolved_foreign_key_is_rejected_instead_of_using_a_local_id_as_server_id(): void
    {
        $this->account();

        $response = $this->sync([
            'transactions' => [[
                'local_id' => 7003,
                'customer_id' => 999999,
                'amount' => 100,
                'timestamp' => 1700000500,
                '_sync' => ['mutation_id' => 'tx-unresolved-7003', 'base_version' => 0, 'operation' => 'CREATE'],
            ]],
        ])->assertOk();

        $response->assertJsonPath('rejected.0.code', 'DEPENDENCY');
        $this->assertDatabaseMissing('transactions', ['local_id' => 7003]);
    }
}
