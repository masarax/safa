<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\Customer;
use App\Models\SafaApiKey;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class SyncCursorPaginationTest extends TestCase
{
    use RefreshDatabase;

    private string $apiKey = 'cursor-sync-test-key';
    private Account $account;

    protected function setUp(): void
    {
        parent::setUp();
        $this->withoutMiddleware();

        $this->account = Account::create(['name' => 'Cursor Sync Account']);
        SafaApiKey::create([
            'account_id' => $this->account->id,
            'api_key' => $this->apiKey,
            'api_secret' => 'cursor-sync-test-secret',
            'client_name' => 'Cursor Sync Test',
            'is_active' => true,
        ]);
    }

    private function getSync(string $query): \Illuminate\Testing\TestResponse
    {
        return $this->withHeader('X-SAFA-API-KEY', $this->apiKey)
            ->getJson('/api/v1/sync/down?' . $query);
    }

    private function customer(int $localId, string $name): Customer
    {
        return Customer::create([
            'account_id' => $this->account->id,
            'local_id' => $localId,
            'name' => $name,
            'phone' => '',
            'timestamp' => time(),
        ]);
    }

    public function test_cursor_bootstrap_is_bounded_deterministic_and_resumable(): void
    {
        $this->customer(1001, 'One');
        $this->customer(1002, 'Two');
        $this->customer(1003, 'Three');

        $first = $this->getSync('cursor=0&per_page=2')->assertOk();
        $first->assertJsonPath('protocol', 'cursor-v1');
        $first->assertJsonPath('cursor', 0);
        $first->assertJsonPath('has_more', true);
        $this->assertCount(2, $first->json('customers'));
        $cursor = (int) $first->json('next_cursor');
        $this->assertGreaterThan(0, $cursor);

        // A client crash before durable cursor commit must be safely replayable.
        $replay = $this->getSync('cursor=0&per_page=2')->assertOk();
        $this->assertSame($first->json('customers'), $replay->json('customers'));
        $this->assertSame($first->json('next_cursor'), $replay->json('next_cursor'));

        $second = $this->getSync("cursor={$cursor}&per_page=2")->assertOk();
        $second->assertJsonPath('has_more', false);
        $this->assertCount(1, $second->json('customers'));
        $finalCursor = (int) $second->json('next_cursor');
        $this->assertGreaterThan($cursor, $finalCursor);

        // Unchanged accounts return only protocol metadata, never historical rows.
        $idle = $this->getSync("cursor={$finalCursor}&per_page=2")->assertOk();
        $idle->assertJsonPath('next_cursor', $finalCursor);
        $idle->assertJsonPath('has_more', false);
        $this->assertSame([], $idle->json('customers'));
        $this->assertSame([], $idle->json('transactions'));
        $this->assertSame([], $idle->json('suppliers'));
    }

    public function test_direct_server_update_and_delete_advance_version_and_emit_only_deltas(): void
    {
        $customer = $this->customer(2001, 'Before');
        $bootstrap = $this->getSync('cursor=0&per_page=50')->assertOk();
        $cursor = (int) $bootstrap->json('next_cursor');
        $version = (int) $bootstrap->json('customers.0.sync_version');
        $this->assertGreaterThan(0, $version);

        $customer->name = 'After';
        $customer->save();

        $updated = $this->getSync("cursor={$cursor}&per_page=50")->assertOk();
        $this->assertCount(1, $updated->json('customers'));
        $updated->assertJsonPath('customers.0.name', 'After');
        $updatedVersion = (int) $updated->json('customers.0.sync_version');
        $this->assertGreaterThan($version, $updatedVersion);
        $cursor = (int) $updated->json('next_cursor');

        $customer->delete();

        $deleted = $this->getSync("cursor={$cursor}&per_page=50")->assertOk();
        $this->assertCount(1, $deleted->json('customers'));
        $this->assertNotNull($deleted->json('customers.0.deleted_at'));
        $this->assertGreaterThan($updatedVersion, (int) $deleted->json('customers.0.sync_version'));
    }

    public function test_legacy_snapshot_route_is_bounded_and_deprecated(): void
    {
        $this->customer(3001, 'One');
        $this->customer(3002, 'Two');
        $this->customer(3003, 'Three');

        $response = $this->withHeader('X-SAFA-API-KEY', $this->apiKey)
            ->getJson('/api/sync/down?per_page=2')
            ->assertOk();

        $response->assertJsonPath('protocol', 'legacy-page-v1');
        $response->assertJsonPath('has_more', true);
        $this->assertCount(2, $response->json('customers'));
        $this->assertSame('true', $response->headers->get('Deprecation'));
        $this->assertStringContainsString('/api/v1/sync/down', (string) $response->headers->get('Link'));
        $this->assertNotNull($response->headers->get('Sunset'));
    }
}
