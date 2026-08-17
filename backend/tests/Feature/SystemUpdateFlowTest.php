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
    }

    public function test_no_pending_migration_means_normal_site_flow_even_without_legacy_installed_marker(): void
    {
        Config::set('safa.installed', false);

        $this->get('/')->assertRedirect(route('safa.login'));
        $this->get('/login')->assertOk();
        $this->getJson('/api/auth/health')->assertOk();
    }

    public function test_pending_migration_redirects_normal_browser_traffic_to_canonical_update_url_but_keeps_login_available(): void
    {
        $this->markMigrationPending();

        $this->get('/')->assertRedirect(route('system.update.show'));
        $this->assertSame(url('/update'), route('system.update.show'));
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

    public function test_guest_is_sent_to_login_before_opening_database_update(): void
    {
        $this->get('/update')->assertRedirect(route('safa.login'));
        $this->post('/update/run')->assertRedirect(route('safa.login'));
    }

    public function test_non_superadmin_sees_restricted_maintenance_state_and_cannot_execute_update(): void
    {
        $user = User::factory()->create([
            'role' => User::ROLE_ADMIN,
            'is_activated' => true,
        ]);

        $this->actingAs($user)
            ->get('/update')
            ->assertForbidden()
            ->assertSee('Database update required')
            ->assertSee('Only an activated SuperAdmin')
            ->assertDontSee('data-maintenance-action="database-update"', false);

        $this->actingAs($user)->post('/update/run')->assertForbidden();
    }

    public function test_legacy_installer_and_system_update_urls_are_hard_closed(): void
    {
        foreach (['/system/update', '/system/update/run', '/system/update/migrate', '/system/update/seed', '/install', '/index'] as $path) {
            $this->get($path)->assertNotFound();
            $this->post($path)->assertNotFound();
        }
    }

    public function test_superadmin_sees_one_click_update_and_applies_pending_migration(): void
    {
        $this->markMigrationPending();
        $superAdmin = User::factory()->create([
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);

        $this->actingAs($superAdmin)
            ->get('/update')
            ->assertOk()
            ->assertSee('Database Update')
            ->assertSee(self::PENDING_MIGRATION)
            ->assertSee('Update Database')
            ->assertDontSee('Run Migration')
            ->assertDontSee('Run Seed')
            ->assertDontSee('Maintenance key')
            ->assertSeeHtml('data-database-update-state="pending"');

        $this->actingAs($superAdmin)
            ->post('/update/run')
            ->assertRedirect(route('safa.app'));

        $this->assertDatabaseHas('migrations', ['migration' => self::PENDING_MIGRATION]);
    }

    public function test_superadmin_can_open_idempotent_update_center_when_schema_is_current(): void
    {
        $superAdmin = User::factory()->create([
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);

        $this->actingAs($superAdmin)
            ->get('/update')
            ->assertOk()
            ->assertSee('Update Database')
            ->assertSeeHtml('data-database-update-state="current"')
            ->assertDontSee('Run Migration')
            ->assertDontSee('Run Seed')
            ->assertDontSee('Recovery mode');
    }

    public function test_pending_database_has_no_public_recovery_write_path(): void
    {
        $this->markMigrationPending();

        $this->get('/update')->assertRedirect(route('safa.login'));
        $this->post('/update/run')->assertRedirect(route('safa.login'));
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
