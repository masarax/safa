<?php

namespace Tests\Feature;

use App\Models\User;
use App\Support\ReleaseUpdateState;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Config;
use Tests\TestCase;

class InitialSuperAdminBootstrapTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        Config::set('safa.enforce_update_checks_in_tests', true);
        Config::set('safa.enforce_release_update_in_tests', true);
    }

    public function test_public_web_installer_surfaces_are_permanently_retired(): void
    {
        foreach (['/install', '/install/super-admin', '/install/test-db', '/install/process', '/install/update', '/install/update-process', '/update-db', '/data-migration', '/setup', '/setup/database', '/setup/admin'] as $path) {
            $this->get($path)->assertNotFound();
            $this->post($path)->assertNotFound();
        }
    }

    public function test_bootstrap_secret_and_controller_contracts_are_absent(): void
    {
        $routes = (string) file_get_contents(base_path('routes/web.php'));
        $config = (string) file_get_contents(config_path('safa.php'));
        $envExample = (string) file_get_contents(base_path('.env.example'));

        foreach ([
            'InitialSuperAdminController',
            'install.superadmin',
            'system.update.migrate',
            'system.update.seed',
            'maintenance_token',
            'SAFA_MAINTENANCE_TOKEN',
            'SAFA_SETUP_TOKEN',
        ] as $forbidden) {
            $this->assertStringNotContainsString($forbidden, $routes);
            $this->assertStringNotContainsString($forbidden, $config);
            $this->assertStringNotContainsString($forbidden, $envExample);
        }
    }

    public function test_release_update_gate_is_public_only_while_current_release_is_unapplied(): void
    {
        $this->assertTrue(ReleaseUpdateState::required());

        $this->get('/update')
            ->assertOk()
            ->assertSee('System Update Ready')
            ->assertSee('Run Update')
            ->assertDontSee('Maintenance key')
            ->assertDontSee('Create Super Admin')
            ->assertDontSee('Run Migration')
            ->assertDontSee('Run Seed');

        $admin = User::factory()->create([
            'role' => User::ROLE_ADMIN,
            'is_activated' => true,
        ]);
        $this->actingAs($admin)->get('/update')->assertOk();

        ReleaseUpdateState::markApplied();
        $this->get('/update')->assertRedirect(route('safa.app'));
    }
}
