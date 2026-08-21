<?php

namespace Tests\Feature;

use App\Models\User;
use App\Support\ReleaseUpdateState;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Config;
use Tests\TestCase;

class ProductionRecoveryTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        Config::set('safa.enforce_update_checks_in_tests', true);
        Config::set('safa.enforce_release_update_in_tests', true);
    }

    public function test_project_root_product_assets_are_served_with_browser_content_types(): void
    {
        $this->get('/safa-web.css')
            ->assertOk()
            ->assertHeader('Content-Type', 'text/css; charset=utf-8');

        $this->get('/safa-web-product.css')
            ->assertOk()
            ->assertHeader('Content-Type', 'text/css; charset=utf-8');

        $this->assertStringContainsString(
            '--brand-green',
            (string) file_get_contents(public_path('safa-web-product.css'))
        );

        $this->get('/safa-web-product.js')
            ->assertOk()
            ->assertHeader('Content-Type', 'application/javascript; charset=utf-8');
    }

    public function test_index_and_installer_setup_surfaces_are_completely_removed(): void
    {
        foreach (['/index', '/install', '/install/super-admin', '/install/test-db', '/install/process', '/install/update', '/install/update-process', '/update-db', '/data-migration', '/setup', '/setup/database', '/setup/admin'] as $path) {
            $this->get($path)->assertNotFound();
            $this->post($path)->assertNotFound();
        }

        $routes = (string) file_get_contents(base_path('routes/web.php'));
        $this->assertStringNotContainsString('SetupController', $routes);
        $this->assertStringNotContainsString('InstallerController', $routes);
        $this->assertStringNotContainsString('InitialSuperAdminController', $routes);
    }

    public function test_release_update_page_contains_no_recovery_or_database_secrets(): void
    {
        $this->get('/update')
            ->assertOk()
            ->assertSee('System Update Ready')
            ->assertSee('Run Update')
            ->assertDontSee('Recovery mode')
            ->assertDontSee('Maintenance key')
            ->assertDontSee('Run Migration')
            ->assertDontSee('Run Seed')
            ->assertDontSee('Create Super Admin')
            ->assertDontSee('setup code')
            ->assertDontSee('database password');
    }

    public function test_completed_release_returns_guest_and_authenticated_users_to_normal_site_flow(): void
    {
        ReleaseUpdateState::markApplied();

        $this->get('/update')->assertRedirect(route('safa.login'));
        $this->post('/update/run')->assertRedirect(route('safa.login'));
        $this->get('/system/update')->assertNotFound();
        $this->post('/system/update/run')->assertNotFound();

        $admin = User::factory()->create([
            'role' => User::ROLE_ADMIN,
            'is_activated' => true,
        ]);

        $this->actingAs($admin)->get('/update')->assertRedirect(route('safa.app'));
        $this->actingAs($admin)->post('/update/run')->assertRedirect(route('safa.app'));
    }
}
