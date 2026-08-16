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
        $this->actingAs($user)->post('/system/update/migrate')->assertForbidden();
        $this->actingAs($user)->post('/system/update/seed')->assertForbidden();
    }

    public function test_superadmin_sees_separate_migration_and_seed_actions_and_can_run_migration(): void
    {
        $this->markMigrationPending();
        $superAdmin = User::factory()->create([
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);

        $this->actingAs($superAdmin)
            ->get('/system/update')
            ->assertOk()
            ->assertSee('System Maintenance')
            ->assertSee(self::PENDING_MIGRATION)
            ->assertSee('Run Migration')
            ->assertSee('Run Seed');

        $this->actingAs($superAdmin)
            ->post('/system/update/migrate')
            ->assertRedirect(route('system.update.show'));

        $this->assertDatabaseHas('migrations', ['migration' => self::PENDING_MIGRATION]);
    }

    public function test_maintenance_page_remains_available_when_no_migration_is_pending(): void
    {
        $superAdmin = User::factory()->create([
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);

        $this->actingAs($superAdmin)
            ->get('/system/update')
            ->assertOk()
            ->assertSee('Run Migration')
            ->assertSee('Run Seed');
    }

    public function test_empty_database_recovery_write_requires_the_server_maintenance_key(): void
    {
        Config::set('safa.maintenance_token', 'correct-maintenance-key');
        $this->markMigrationPending();

        $this->get('/system/update')
            ->assertOk()
            ->assertSee('Recovery mode')
            ->assertSee('Maintenance key');

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
