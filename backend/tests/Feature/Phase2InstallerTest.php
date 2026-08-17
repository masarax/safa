<?php

namespace Tests\Feature;

use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class Phase2InstallerTest extends TestCase
{
    use RefreshDatabase;

    public function test_all_legacy_installer_and_database_probe_paths_are_hard_closed(): void
    {
        $payload = [
            'db_host' => '127.0.0.1',
            'db_port' => 3306,
            'db_name' => 'private_db',
            'db_user' => 'probe_user',
            'db_pass' => 'probe_password',
        ];

        foreach (['/install', '/install/super-admin', '/install/test-db', '/install/process', '/install/update', '/install/update-process', '/update-db'] as $path) {
            $this->get($path)->assertNotFound();
            $response = $this->postJson($path, $payload);
            $response->assertNotFound();
            $response->assertJson(['status' => 'not_found']);
            $this->assertStringNotContainsString('PDO', $response->getContent());
            $this->assertStringNotContainsString('probe_password', $response->getContent());
        }
    }

    public function test_installer_controller_and_routes_are_removed_from_runtime_source(): void
    {
        $routes = (string) file_get_contents(base_path('routes/web.php'));
        $bootstrap = (string) file_get_contents(base_path('bootstrap/app.php'));

        $this->assertStringNotContainsString('InstallerController', $routes);
        $this->assertStringNotContainsString('InitialSuperAdminController', $routes);
        $this->assertStringNotContainsString('ensure.not.installed', $bootstrap);
    }

    public function test_branding_assets_exist(): void
    {
        $this->assertFileExists(public_path('safa-logo.png'));
        $this->assertFileExists(public_path('favicon.svg'));
        $this->assertGreaterThan(0, filesize(public_path('safa-logo.png')));
    }
}
