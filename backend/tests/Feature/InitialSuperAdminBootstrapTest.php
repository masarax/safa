<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class InitialSuperAdminBootstrapTest extends TestCase
{
    use RefreshDatabase;

    public function test_public_web_superadmin_bootstrap_is_permanently_retired(): void
    {
        foreach (['/install', '/install/super-admin', '/install/test-db', '/install/process', '/install/update', '/install/update-process', '/update-db'] as $path) {
            $this->get($path)->assertNotFound();
            $this->post($path)->assertNotFound();
        }

        $this->assertFalse(User::query()->where('role', User::ROLE_SUPERADMIN)->exists());
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

    public function test_database_update_surface_requires_an_authenticated_activated_superadmin(): void
    {
        $this->get('/system/update')->assertRedirect(route('safa.login'));
        $this->post('/system/update/run')->assertRedirect(route('safa.login'));

        $admin = User::factory()->create([
            'role' => User::ROLE_ADMIN,
            'is_activated' => true,
        ]);
        $this->actingAs($admin)->get('/system/update')->assertForbidden();
        $this->actingAs($admin)->post('/system/update/run')->assertForbidden();

        $superAdmin = User::factory()->create([
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);
        $this->actingAs($superAdmin)
            ->get('/system/update')
            ->assertOk()
            ->assertSee('Database Update')
            ->assertDontSee('Maintenance key')
            ->assertDontSee('Create Super Admin')
            ->assertDontSee('Run Migration')
            ->assertDontSee('Run Seed');
    }
}
