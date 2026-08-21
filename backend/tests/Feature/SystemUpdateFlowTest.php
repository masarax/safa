<?php

namespace Tests\Feature;

use App\Support\ReleaseUpdateState;
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
        Config::set('safa.enforce_release_update_in_tests', true);
        ReleaseUpdateState::markApplied();
    }

    public function test_current_release_has_normal_site_flow_and_update_url_redirects_normally(): void
    {
        $this->assertFalse(ReleaseUpdateState::required());
        $this->get('/')->assertRedirect(route('safa.login'));
        $this->get('/login')->assertOk();
        $this->getJson('/api/auth/health')->assertOk();
        $this->get('/update')->assertRedirect(route('safa.login'));
        $this->post('/update/run')->assertRedirect(route('safa.login'));
    }

    public function test_pending_migration_redirects_browser_traffic_to_clean_update_gate(): void
    {
        $this->markMigrationPending();

        $this->get('/')->assertRedirect(route('system.update.show'));
        $this->get('/login')->assertRedirect(route('system.update.show'));
        $this->get('/update')
            ->assertOk()
            ->assertSee('System Update Ready')
            ->assertSee('Run Update')
            ->assertDontSee(self::PENDING_MIGRATION)
            ->assertDontSee('Pending migration')
            ->assertDontSee('setup code');
    }

    public function test_pending_release_returns_machine_readable_503_for_api_requests(): void
    {
        $this->markMigrationPending();

        $this->getJson('/api/auth/health')
            ->assertStatus(503)
            ->assertJson([
                'status' => 'update_required',
                'update_path' => '/update',
            ])
            ->assertJsonMissingPath('pending_count');
    }

    public function test_one_click_release_update_applies_pending_migration_and_returns_to_normal_login(): void
    {
        $this->markMigrationPending();

        $this->post('/update/run', ['language' => 'en'])
            ->assertRedirect(route('safa.login', ['lang' => 'en']));

        $this->assertDatabaseHas('migrations', ['migration' => self::PENDING_MIGRATION]);
        $this->assertFalse(ReleaseUpdateState::required());
        $this->get('/update')->assertRedirect(route('safa.login'));
        $this->get('/login')->assertOk();
    }

    public function test_release_fingerprint_change_reopens_update_gate_without_exposing_internals(): void
    {
        DB::table(ReleaseUpdateState::TABLE)->where('id', 1)->update([
            'release_fingerprint' => str_repeat('0', 64),
            'updated_at' => now(),
        ]);

        $this->assertTrue(ReleaseUpdateState::required());
        $this->get('/')->assertRedirect(route('system.update.show'));
        $this->get('/update')->assertOk()->assertSee('Run Update');
    }

    public function test_legacy_installer_urls_are_hard_closed(): void
    {
        foreach ([
            '/system/update',
            '/system/update/run',
            '/system/update/migrate',
            '/system/update/seed',
            '/install',
            '/index',
            '/data-migration',
            '/setup',
            '/setup/database',
            '/setup/admin',
        ] as $path) {
            $this->get($path)->assertNotFound();
            $this->post($path)->assertNotFound();
        }
    }

    private function markMigrationPending(): void
    {
        $deleted = DB::table('migrations')
            ->where('migration', self::PENDING_MIGRATION)
            ->delete();

        $this->assertSame(1, $deleted, 'Expected the fixture migration to exist before marking it pending.');
    }
}
