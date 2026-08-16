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

    public function test_guest_is_sent_to_login_before_opening_system_updater(): void
    {
        $this->markMigrationPending();

        $this->get('/system/update')
            ->assertRedirect('/login');
    }

    public function test_non_superadmin_cannot_view_or_execute_system_update(): void
    {
        $this->markMigrationPending();
        $user = User::factory()->create([
            'role' => User::ROLE_ADMIN,
            'is_activated' => true,
        ]);

        $this->actingAs($user)->get('/system/update')->assertForbidden();
        $this->actingAs($user)->post('/system/update')->assertForbidden();

        $this->assertDatabaseMissing('migrations', ['migration' => self::PENDING_MIGRATION]);
    }

    public function test_superadmin_can_run_update_and_normal_navigation_resumes(): void
    {
        $this->markMigrationPending();
        $superAdmin = User::factory()->create([
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);

        $this->actingAs($superAdmin)
            ->get('/system/update')
            ->assertOk()
            ->assertSee('System Update Required')
            ->assertSee(self::PENDING_MIGRATION)
            ->assertSee('Run Update');

        $this->actingAs($superAdmin)
            ->post('/system/update')
            ->assertRedirect(route('safa.app'));

        $this->assertDatabaseHas('migrations', ['migration' => self::PENDING_MIGRATION]);

        $this->actingAs($superAdmin)
            ->get('/')
            ->assertRedirect(route('safa.app'));
    }

    public function test_system_update_page_disappears_when_nothing_is_pending(): void
    {
        $superAdmin = User::factory()->create([
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);

        $this->actingAs($superAdmin)
            ->get('/system/update')
            ->assertRedirect(route('safa.app'));
    }

    private function markMigrationPending(): void
    {
        $deleted = DB::table('migrations')
            ->where('migration', self::PENDING_MIGRATION)
            ->delete();

        $this->assertSame(1, $deleted, 'Expected the fixture migration to exist before marking it pending.');
    }
}
