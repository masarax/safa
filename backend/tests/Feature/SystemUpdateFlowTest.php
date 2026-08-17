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

    public function test_uninitialized_browser_traffic_redirects_to_authenticated_update_entrypoint(): void
    {
        Config::set('safa.installed', false);

        $this->get('/')->assertRedirect(route('system.update.show'));
        $this->get('/system/update')->assertRedirect(route('safa.login'));
        $this->get('/login')->assertOk();
    }

    public function test_uninitialized_api_traffic_remains_machine_readable(): void
    {
        Config::set('safa.installed', false);

        $this->getJson('/api/auth/health')
            ->assertStatus(503)
            ->assertJson(['status' => 'maintenance_required']);
    }

    public function test_pending_migration_redirects_normal_browser_traffic_but_keeps_login_available(): void
    {
        $this->markMigrationPending();

        $this->get('/')->assertRedirect(route('system.update.show'));
        $this->get('/login')->assertOk();
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

    public function test_guest_is_sent_to_login_before_opening_system_maintenance(): void
    {
        $this->get('/system/update')->assertRedirect(route('safa.login'));
        $this->post('/system/update/run')->assertRedirect(route('safa.login'));
    }

    public function test_non_superadmin_cannot_view_or_execute_maintenance_actions(): void
    {
        $user = User::factory()->create([
            'role' => User::ROLE_ADMIN,
            'is_activated' => true,
        ]);

        $this->actingAs($user)->get('/system/update')->assertForbidden();
        $this->actingAs($user)->post('/system/update/run')->assertForbidden();
    }

    public function test_legacy_split_migrate_and_seed_web_actions_are_not_routed(): void
    {
        $this->post('/system/update/migrate')->assertNotFound();
        $this->post('/system/update/seed')->assertNotFound();
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
            ->assertDontSee('Maintenance key')
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
            ->assertDontSee('Run Seed')
            ->assertDontSee('Recovery mode');
    }

    public function test_empty_database_has_no_public_recovery_write_path(): void
    {
        Config::set('safa.installed', false);
        $this->markMigrationPending();

        $this->get('/system/update')->assertRedirect(route('safa.login'));
        $this->post('/system/update/migrate')->assertNotFound();
        $this->post('/system/update/seed')->assertNotFound();
        $this->assertDatabaseMissing('migrations', ['migration' => self::PENDING_MIGRATION]);
    }

    private function markMigrationPending(): void
    {
        $deleted = DB::table('migrations')
            ->where('migration', self::PENDING_MIGRATION)
            ->delete();

        $this->assertSame(1, $deleted, 'Expected the fixture migration to exist before marking it pending.');
    }
}
