<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\Customer;
use App\Models\SafaApiKey;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

/**
 * Phase 2 reconciliation contract tests.
 *
 * These tests deliberately exercise the failure modes that matter for a
 * local-first client: stale writes, repeated delivery, soft deletes and
 * account isolation. The API must acknowledge a mutation without allowing a
 * stale client snapshot to overwrite a newer server snapshot.
 */
class SyncReconciliationPhase2Test extends TestCase
{
    use RefreshDatabase;

    private string $apiKey = 'phase2-sync-key';

    protected function setUp(): void
    {
        parent::setUp();
        $this->withoutMiddleware(\App\Http\Middleware\CheckApiSecurityKey::class);
    }

    private function account(string $name): Account
    {
        $account = Account::create(['name' => $name]);

        SafaApiKey::create([
            'account_id' => $account->id,
            'api_key' => $this->apiKey,
            'api_secret' => 'phase2-test-secret',
            'client_name' => 'Phase 2 Test Client',
            'is_active' => true,
        ]);

        return $account;
    }

    private function sync(array $payload): \Illuminate\Testing\TestResponse
    {
        return $this->withHeaders([
            'X-SAFA-API-KEY' => $this->apiKey,
        ])->postJson('/api/sync/up', $payload);
    }

    public function test_stale_client_write_is_acknowledged_without_overwriting_newer_server_state(): void
    {
        $account = $this->account('Phase 2 Conflict Account');

        $first = $this->sync([
            'customers' => [[
                'local_id' => 9001,
                'name' => 'Server Newer Name',
                'phone' => '01700009001',
                'timestamp' => 1700000200,
            ]],
        ]);

        $first->assertOk();
        $serverId = $first->json('accepted.customers.0.server_id');
        $this->assertGreaterThan(0, $serverId);

        $stale = $this->sync([
            'customers' => [[
                'local_id' => 9001,
                'name' => 'Stale Client Name',
                'phone' => '01700009001',
                'timestamp' => 1700000100,
            ]],
        ]);

        $stale->assertOk();
        $this->assertSame($serverId, $stale->json('accepted.customers.0.server_id'));

        $customer = Customer::where('account_id', $account->id)
            ->where('local_id', 9001)
            ->firstOrFail();

        $this->assertSame('Server Newer Name', $customer->name);
        $this->assertSame('01700009001', $customer->phone);
        $this->assertSame(1700000200, (int) $customer->timestamp);
    }

    public function test_repeated_soft_delete_is_idempotent_and_does_not_create_duplicates(): void
    {
        $account = $this->account('Phase 2 Delete Account');

        $create = $this->sync([
            'customers' => [[
                'local_id' => 9101,
                'name' => 'Delete Me',
                'phone' => '01700009101',
                'timestamp' => 1700000300,
            ]],
        ]);

        $create->assertOk();
        $serverId = $create->json('accepted.customers.0.server_id');

        $deletePayload = [
            'customers' => [[
                'local_id' => 9101,
                'name' => 'Delete Me',
                'phone' => '01700009101',
                'timestamp' => 1700000400,
                'deleted_at' => 1700000400,
            ]],
        ];

        $delete1 = $this->sync($deletePayload);
        $delete1->assertOk();
        $this->assertSame($serverId, $delete1->json('accepted.customers.0.server_id'));

        $delete2 = $this->sync($deletePayload);
        $delete2->assertOk();
        $this->assertSame($serverId, $delete2->json('accepted.customers.0.server_id'));

        $this->assertSame(
            1,
            Customer::withTrashed()
                ->where('account_id', $account->id)
                ->where('local_id', 9101)
                ->count()
        );

        $customer = Customer::withTrashed()->findOrFail($serverId);
        $this->assertTrue($customer->trashed());
    }

    public function test_same_local_id_is_isolated_between_accounts(): void
    {
        $accountA = $this->account('Phase 2 Account A');

        $first = $this->sync([
            'customers' => [[
                'local_id' => 9201,
                'name' => 'Account A Customer',
                'phone' => '01700009201',
                'timestamp' => 1700000500,
            ]],
        ]);

        $first->assertOk();
        $serverIdA = $first->json('accepted.customers.0.server_id');

        // A different API key/account is required to prove tenant isolation.
        $accountB = Account::create(['name' => 'Phase 2 Account B']);
        $apiKeyB = 'phase2-sync-key-b';
        SafaApiKey::create([
            'account_id' => $accountB->id,
            'api_key' => $apiKeyB,
            'api_secret' => 'phase2-test-secret-b',
            'client_name' => 'Phase 2 Test Client B',
            'is_active' => true,
        ]);

        $second = $this->withHeaders([
            'X-SAFA-API-KEY' => $apiKeyB,
        ])->postJson('/api/sync/up', [
            'customers' => [[
                'local_id' => 9201,
                'name' => 'Account B Customer',
                'phone' => '01700009202',
                'timestamp' => 1700000500,
            ]],
        ]);

        $second->assertOk();
        $serverIdB = $second->json('accepted.customers.0.server_id');

        $this->assertNotSame($serverIdA, $serverIdB);
        $this->assertSame(1, Customer::where('account_id', $accountA->id)->where('local_id', 9201)->count());
        $this->assertSame(1, Customer::where('account_id', $accountB->id)->where('local_id', 9201)->count());
        $this->assertSame('Account A Customer', Customer::findOrFail($serverIdA)->name);
        $this->assertSame('Account B Customer', Customer::findOrFail($serverIdB)->name);
    }
}
