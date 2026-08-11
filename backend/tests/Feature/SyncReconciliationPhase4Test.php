<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\Customer;
use App\Models\SafaApiKey;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

/**
 * Phase 4 contract tests model the real multi-device lifecycle:
 * upload -> concurrent server edit -> stale conflict -> local rebase -> retry,
 * followed by an explicit delete using the latest server revision.
 */
class SyncReconciliationPhase4Test extends TestCase
{
    use RefreshDatabase;

    private string $apiKey = 'phase4-sync-key';

    protected function setUp(): void
    {
        parent::setUp();
        $this->withoutMiddleware(\App\Http\Middleware\CheckApiSecurityKey::class);
    }

    private function account(): Account
    {
        $account = Account::create(['name' => 'Phase 4 E2E Account']);
        SafaApiKey::create([
            'account_id' => $account->id,
            'api_key' => $this->apiKey,
            'api_secret' => 'phase4-test-secret',
            'client_name' => 'Phase 4 Test Client',
            'is_active' => true,
        ]);
        return $account;
    }

    private function sync(array $payload): \Illuminate\Testing\TestResponse
    {
        return $this->withHeaders(['X-SAFA-API-KEY' => $this->apiKey])->postJson('/api/sync/up', $payload);
    }

    public function test_stale_update_rebases_against_latest_server_revision_then_succeeds(): void
    {
        $account = $this->account();

        $create = $this->sync([
            'customers' => [[
                'local_id' => 8101,
                'name' => 'Initial',
                'phone' => '01700008101',
                'timestamp' => 1700001000,
                '_sync' => ['mutation_id' => 'p4-create-8101', 'base_version' => 0, 'operation' => 'CREATE'],
            ]],
        ])->assertOk();
        $serverId = $create->json('accepted.customers.0.server_id');
        $this->assertSame(1, $create->json('accepted.customers.0.sync_version'));

        $serverUpdate = $this->sync([
            'customers' => [[
                'local_id' => 8101,
                'name' => 'Device B',
                'phone' => '01700008101',
                'timestamp' => 1700001100,
                '_sync' => ['mutation_id' => 'p4-server-8101', 'base_version' => 1, 'operation' => 'UPDATE'],
            ]],
        ])->assertOk();
        $this->assertSame(2, $serverUpdate->json('accepted.customers.0.sync_version'));

        $conflict = $this->sync([
            'customers' => [[
                'local_id' => 8101,
                'name' => 'Device A Newer Local Edit',
                'phone' => '01700008101',
                'timestamp' => 1700001200,
                '_sync' => ['mutation_id' => 'p4-local-8101-v1', 'base_version' => 1, 'operation' => 'UPDATE'],
            ]],
        ])->assertOk();

        $conflict->assertJsonPath('status', 'conflict');
        $conflict->assertJsonPath('conflicts.0.server_id', $serverId);
        $conflict->assertJsonPath('conflicts.0.server_version', 2);

        $rebased = $this->sync([
            'customers' => [[
                'local_id' => 8101,
                'name' => 'Device A Newer Local Edit',
                'phone' => '01700008101',
                'timestamp' => 1700001200,
                '_sync' => ['mutation_id' => 'p4-local-8101-v2', 'base_version' => 2, 'operation' => 'UPDATE'],
            ]],
        ])->assertOk();

        $rebased->assertJsonPath('accepted.customers.0.server_id', $serverId);
        $rebased->assertJsonPath('accepted.customers.0.sync_version', 3);
        $this->assertSame(
            'Device A Newer Local Edit',
            Customer::where('account_id', $account->id)->where('local_id', 8101)->value('name')
        );
    }

    public function test_delete_requires_latest_revision_and_then_remains_idempotent(): void
    {
        $account = $this->account();

        $create = $this->sync([
            'customers' => [[
                'local_id' => 8201,
                'name' => 'Delete Lifecycle',
                'phone' => '01700008201',
                'timestamp' => 1700001300,
                '_sync' => ['mutation_id' => 'p4-create-8201', 'base_version' => 0, 'operation' => 'CREATE'],
            ]],
        ])->assertOk();
        $serverId = $create->json('accepted.customers.0.server_id');

        $this->sync([
            'customers' => [[
                'local_id' => 8201,
                'name' => 'Updated Before Delete',
                'phone' => '01700008201',
                'timestamp' => 1700001400,
                '_sync' => ['mutation_id' => 'p4-update-8201', 'base_version' => 1, 'operation' => 'UPDATE'],
            ]],
        ])->assertOk();

        $staleDelete = $this->sync([
            'customers' => [[
                'local_id' => 8201,
                'name' => 'Delete Lifecycle',
                'phone' => '01700008201',
                'timestamp' => 1700001500,
                'deleted_at' => 1700001500,
                '_sync' => ['mutation_id' => 'p4-delete-stale-8201', 'base_version' => 1, 'operation' => 'DELETE'],
            ]],
        ])->assertOk();

        $staleDelete->assertJsonPath('status', 'conflict');
        $staleDelete->assertJsonPath('conflicts.0.server_version', 2);
        $this->assertFalse(Customer::withTrashed()->findOrFail($serverId)->trashed());

        $delete = $this->sync([
            'customers' => [[
                'local_id' => 8201,
                'name' => 'Updated Before Delete',
                'phone' => '01700008201',
                'timestamp' => 1700001500,
                'deleted_at' => 1700001500,
                '_sync' => ['mutation_id' => 'p4-delete-8201', 'base_version' => 2, 'operation' => 'DELETE'],
            ]],
        ])->assertOk();

        $delete->assertJsonPath('accepted.customers.0.server_id', $serverId);
        $delete->assertJsonPath('accepted.customers.0.server_deleted', true);

        $deleteAgain = $this->sync([
            'customers' => [[
                'local_id' => 8201,
                'name' => 'Updated Before Delete',
                'phone' => '01700008201',
                'timestamp' => 1700001500,
                'deleted_at' => 1700001500,
                '_sync' => ['mutation_id' => 'p4-delete-8201', 'base_version' => 2, 'operation' => 'DELETE'],
            ]],
        ])->assertOk();

        $deleteAgain->assertJsonPath('accepted.customers.0.idempotent', true);
        $this->assertSame(1, Customer::withTrashed()->where('account_id', $account->id)->where('local_id', 8201)->count());
        $this->assertTrue(Customer::withTrashed()->findOrFail($serverId)->trashed());
    }
}
