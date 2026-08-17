<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Config;
use Illuminate\Support\Facades\DB;
use Tests\TestCase;

class SystemUpdateFlowTest extends TestCase
{
    use RefreshDatabase;

    private const PENDING_MIGRATION = '2026_08_10_000002_add_address_to_customers';

    protected function setUp(): void
    {
        parent::setUp();
        Config::set('safa.enforce_update_checks_in_tests', true);
        Config::set('safa.installed', true);
    }

    public function test_uninitialized_browser_traffic_redirects_to_system_update(): void
    {
        Config::set('safa.installed', false);

        $this->get('/')
            ->assertRedirect(route('system.update.show'));
    }

    public function test_uninitialized_api_traffic_remains_machine_readable(): void
    {
        Config::set('safa.installed', false);

        $this->getJson('/api/auth/health')
            ->assertStatus(503)
            ->assertJson([
                'status' => 'maintenance_required',
            ]);
    }

    public function test_pending_migration_redirects_normal_browser_traffic_to_system_update(): void
    {
        $this->markMigrationPending();

        $this->get('/')
            ->assertRedirect(route('system.update.show'));
    }

    public function test_pending_migration_returns_machine_readable_503_for_api_requests(): void
    {
        $this->markMigrationPending();

        $this->getJson('/api/auth/health')
            ->assertStatus(503)
            ->assertJson([
                'status' => 'update_required',
                'pending_count' => 1,
            ]);
    }

    public function test_initialized_guest_is_sent_to_login_before_opening_system_maintenance(): void
    {
        User::factory()->create([
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);

        $this->get('/system/update')
            ->assertRedirect(route('safa.login'));
    }

    public function test_non_superadmin_cannot_view_or_execute_maintenance_actions(): void
    {
        User::factory()->create([
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);
        $user = User::factory()->create([
            'role' => User::ROLE_ADMIN,
            'is_activated' => true,
        ]);

        $this->actingAs($user)->get('/system/update')->assertForbidden();
        $this->actingAs($user)->post('/system/update/run')->assertForbidden();
        $this->actingAs($user)->post('/system/update/migrate')->assertForbidden();
        $this->actingAs($user)->post('/system/update/seed')->assertForbidden();
    }

    public function test_superadmin_sees_one_click_update_and_applies_pending_migration(): void
    {
        $this->markMigrationPending();
        $superAdmin = User::factory()->create([
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);

        $this->actingAs($superAdmin)
            ->get('/system/update')
            ->assertOk()
            ->assertSee('Database Update')
            ->assertSee(self::PENDING_MIGRATION)
            ->assertSee('Run Database Update')
            ->assertDontSee('Run Migration')
            ->assertDontSee('Run Seed')
            ->assertSeeHtml('data-database-update-state="pending"');

        $this->actingAs($superAdmin)
            ->post('/system/update/run')
            ->assertRedirect(route('system.update.show'));

        $this->assertDatabaseHas('migrations', ['migration' => self::PENDING_MIGRATION]);
    }

    public function test_installed_maintenance_page_keeps_one_idempotent_update_action_when_current(): void
    {
        $superAdmin = User::factory()->create([
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);

        $this->actingAs($superAdmin)
            ->get('/system/update')
            ->assertOk()
            ->assertSee('Run Database Update')
            ->assertSeeHtml('data-database-update-state="current"')
            ->assertDontSee('Run Migration')
            ->assertDontSee('Run Seed');
    }

    public function test_empty_database_recovery_write_requires_the_server_maintenance_key(): void
    {
        Config::set('safa.maintenance_token', 'correct-maintenance-key');
        $this->markMigrationPending();

        $this->get('/system/update')
            ->assertOk()
            ->assertSee('Recovery mode')
            ->assertSee('Maintenance key')
            ->assertSee('Run Migration')
            ->assertSee('Run Seed')
            ->assertDontSee('Run Database Update');

        $this->post('/system/update/migrate', [
            'maintenance_token' => 'wrong-key',
        ])->assertForbidden();
        $this->assertDatabaseMissing('migrations', ['migration' => self::PENDING_MIGRATION]);

        $this->post('/system/update/migrate', [
            'maintenance_token' => 'correct-maintenance-key',
        ])->assertRedirect(route('system.update.show'));
        $this->assertDatabaseHas('migrations', ['migration' => self::PENDING_MIGRATION]);
    }

    private function markMigrationPending(): void
    {
        $deleted = DB::table('migrations')
            ->where('migration', self::PENDING_MIGRATION)
            ->delete();

        $this->assertSame(1, $deleted, 'Expected the fixture migration to exist before marking it pending.');
    }
}
