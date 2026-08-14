<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\Customer;
use App\Models\Transaction;
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

    public function test_transaction_money_round_trip_is_exact_idempotent_and_keeps_settlements(): void
    {
        $account = $this->account('Exact Money Account');
        $payload = [
            'transactions' => [[
                'local_id' => 9301,
                'type' => 'Pending',
                'amount_sar' => '0.30000000000000004',
                'customer_rate' => '32.12345',
                'supplier_rate' => '32',
                'amount_bdt' => '9.637',
                'sar_collected' => '-0.105',
                'bdt_disbursed' => '9.637',
                'timestamp' => 1700000600,
            ]],
        ];

        $first = $this->sync($payload)->assertOk();
        $second = $this->sync($payload)->assertOk();
        $this->assertTrue((bool) $second->json('accepted.transactions.0.idempotent'));

        $transaction = Transaction::where('account_id', $account->id)
            ->where('local_id', 9301)
            ->firstOrFail();
        $this->assertSame('0.30', $transaction->amount_sar);
        $this->assertSame('32.1235', $transaction->customer_rate);
        $this->assertSame('32.0000', $transaction->supplier_rate);
        $this->assertSame('9.64', $transaction->amount_bdt);
        $this->assertSame('-0.11', $transaction->sar_collected);
        $this->assertSame('9.64', $transaction->bdt_disbursed);

        $download = $this->withHeaders(['X-SAFA-API-KEY' => $this->apiKey])
            ->getJson('/api/sync/down')
            ->assertOk();
        $row = collect($download->json('transactions'))->firstWhere('local_id', 9301);
        $this->assertSame('0.30', $row['amount_sar']);
        $this->assertSame('-0.11', $row['sar_collected']);
        $this->assertSame('9.64', $row['bdt_disbursed']);
        $this->assertSame(1, Transaction::where('account_id', $account->id)->where('local_id', 9301)->count());
    }
}
