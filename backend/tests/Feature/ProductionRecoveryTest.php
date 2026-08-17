<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class ProductionRecoveryTest extends TestCase
{
    use RefreshDatabase;

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
        foreach (['/index', '/install', '/install/super-admin', '/install/test-db', '/install/process', '/install/update', '/install/update-process', '/update-db'] as $path) {
            $this->get($path)->assertNotFound();
            $this->post($path)->assertNotFound();
        }

        $routes = (string) file_get_contents(base_path('routes/web.php'));
        $this->assertStringNotContainsString('SetupController', $routes);
        $this->assertStringNotContainsString('InstallerController', $routes);
        $this->assertStringNotContainsString('InitialSuperAdminController', $routes);
    }

    public function test_login_remains_available_without_install_or_recovery_copy(): void
    {
        $this->get('/login?lang=en')
            ->assertOk()
            ->assertSee('Sign in')
            ->assertSee('Mobile number or email')
            ->assertSee('PIN / password')
            ->assertDontSee('Maintenance key')
            ->assertDontSee('Create the first Super Admin');

        $this->get('/login?lang=bn')
            ->assertOk()
            ->assertDontSee('Maintenance key')
            ->assertDontSee('Create Super Admin');
    }

    public function test_guest_and_non_superadmin_cannot_reach_database_update_writes(): void
    {
        $this->get('/update')->assertRedirect(route('safa.login'));
        $this->post('/update/run')->assertRedirect(route('safa.login'));
        $this->get('/system/update')->assertNotFound();
        $this->post('/system/update/run')->assertNotFound();
        $this->post('/system/update/migrate')->assertNotFound();
        $this->post('/system/update/seed')->assertNotFound();

        $admin = User::factory()->create([
            'role' => User::ROLE_ADMIN,
            'is_activated' => true,
        ]);

        $this->actingAs($admin)->get('/update')->assertForbidden();
        $this->actingAs($admin)->post('/update/run')->assertForbidden();
    }

    public function test_superadmin_update_page_contains_no_guest_recovery_controls(): void
    {
        $superAdmin = User::factory()->create([
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);

        $this->actingAs($superAdmin)
            ->get('/update')
            ->assertOk()
            ->assertSee('Update Database')
            ->assertDontSee('Recovery mode')
            ->assertDontSee('Maintenance key')
            ->assertDontSee('Run Migration')
            ->assertDontSee('Run Seed')
            ->assertDontSee('Create Super Admin');
    }
}
