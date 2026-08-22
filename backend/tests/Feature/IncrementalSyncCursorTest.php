<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\Customer;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Tests\TestCase;

class IncrementalSyncCursorTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        $this->withoutMiddleware();
    }

    public function test_bootstrap_cursor_then_only_changed_rows_are_downloaded(): void
    {
        [$user, $account] = $this->ownerAndAccount();
        $customer = Customer::create([
            'account_id' => $account->id,
            'local_id' => 101,
            'name' => 'Before',
            'phone' => '1',
            'timestamp' => time(),
        ]);

        $bootstrap = $this->actingAs($user)
            ->withHeader('X-SAFA-ACCOUNT-ID', (string) $account->id)
            ->getJson('/api/v1/sync/down?page=1&per_page=50')
            ->assertOk();

        $cursor = (int) $bootstrap->json('snapshot_cursor');
        $this->assertGreaterThan(0, $cursor);

        $unchanged = $this->actingAs($user)
            ->withHeader('X-SAFA-ACCOUNT-ID', (string) $account->id)
            ->getJson('/api/v1/sync/changes?cursor=' . $cursor)
            ->assertOk();
        $this->assertSame([], $unchanged->json('changes'));
        $this->assertSame($cursor, $unchanged->json('next_cursor'));

        $customer->update(['name' => 'After']);

        $delta = $this->actingAs($user)
            ->withHeader('X-SAFA-ACCOUNT-ID', (string) $account->id)
            ->getJson('/api/v1/sync/changes?cursor=' . $cursor . '&limit=10')
            ->assertOk()
            ->assertJsonPath('reset_required', false)
            ->assertJsonPath('changes.0.entity', 'customers')
            ->assertJsonPath('changes.0.row.id', $customer->id)
            ->assertJsonPath('changes.0.row.name', 'After');

        $next = (int) $delta->json('next_cursor');
        $this->assertGreaterThan($cursor, $next);

        $customer->delete();
        $deleted = $this->actingAs($user)
            ->withHeader('X-SAFA-ACCOUNT-ID', (string) $account->id)
            ->getJson('/api/v1/sync/changes?cursor=' . $next)
            ->assertOk();

        $this->assertSame('customers', $deleted->json('changes.0.entity'));
        $this->assertNotNull($deleted->json('changes.0.row.deleted_at'));
    }

    public function test_journal_compaction_forces_old_clients_to_safe_rebootstrap(): void
    {
        [$user, $account] = $this->ownerAndAccount();
        Customer::create([
            'account_id' => $account->id,
            'local_id' => 201,
            'name' => 'Old change',
            'phone' => '',
            'timestamp' => time(),
        ]);

        DB::table('sync_changes')
            ->where('account_id', $account->id)
            ->update(['created_at' => now()->subDays(120), 'updated_at' => now()->subDays(120)]);

        $this->artisan('safa:prune-sync-changes --days=90')->assertSuccessful();
        $floor = (int) DB::table('sync_change_floors')->where('account_id', $account->id)->value('floor_cursor');
        $this->assertGreaterThan(0, $floor);

        $this->actingAs($user)
            ->withHeader('X-SAFA-ACCOUNT-ID', (string) $account->id)
            ->getJson('/api/v1/sync/changes?cursor=0')
            ->assertOk()
            ->assertJsonPath('status', 'reset_required')
            ->assertJsonPath('reset_required', true)
            ->assertJsonPath('floor_cursor', $floor);
    }

    public function test_deprecated_full_snapshot_refuses_large_accounts_instead_of_loading_without_bound(): void
    {
        [$user, $account] = $this->ownerAndAccount();
        $now = now();
        $rows = [];
        for ($i = 1; $i <= 501; $i++) {
            $rows[] = [
                'account_id' => $account->id,
                'local_id' => 10_000 + $i,
                'name' => 'Customer ' . $i,
                'phone' => '',
                'timestamp' => time(),
                'created_at' => $now,
                'updated_at' => $now,
            ];
        }
        foreach (array_chunk($rows, 100) as $chunk) DB::table('customers')->insert($chunk);

        $this->actingAs($user)
            ->withHeader('X-SAFA-ACCOUNT-ID', (string) $account->id)
            ->getJson('/api/sync/down')
            ->assertStatus(426)
            ->assertHeader('Deprecation', 'true')
            ->assertJsonPath('status', 'upgrade_required');
    }

    private function ownerAndAccount(): array
    {
        $user = User::factory()->create(['role' => User::ROLE_SUPERADMIN, 'is_activated' => true]);
        $account = Account::create(['name' => 'Cursor Account', 'balance' => 0, 'owner_user_id' => $user->id]);
        return [$user, $account];
    }
}
