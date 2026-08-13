<?php

namespace Tests\Feature;

use App\Http\Controllers\InstallerController;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Artisan;
use Tests\TestCase;

class Phase2InstallerTest extends TestCase
{
    use RefreshDatabase;

    public function test_public_installer_and_update_controls_are_not_exposed(): void
    {
        $this->get('/install')->assertNotFound();
        $this->get('/install/update')->assertNotFound();
        $this->postJson('/update-db')->assertNotFound();
    }

    public function test_installer_database_probe_and_process_are_unreachable_even_with_database_input(): void
    {
        $payload = [
            'app_name' => 'SAFA',
            'app_url' => 'https://example.invalid',
            'db_host' => '127.0.0.1',
            'db_port' => 3306,
            'db_name' => 'private_db',
            'db_user' => 'probe_user',
            'db_pass' => 'probe_password',
        ];

        foreach (['/install/test-db', '/install/process', '/install/update-process'] as $path) {
            $response = $this->postJson($path, $payload);
            $response->assertNotFound();
            $response->assertJson(['status' => 'not_found']);
            $this->assertStringNotContainsString('Exception:', $response->getContent());
            $this->assertStringNotContainsString('PDO', $response->getContent());
            $this->assertStringNotContainsString('probe_password', $response->getContent());
        }
    }

    public function test_retired_installer_controller_methods_cannot_execute_database_or_update_work(): void
    {
        $controller = new InstallerController();

        foreach (['index', 'testDb', 'process', 'success', 'updateView', 'updateProcess'] as $method) {
            $response = $controller->{$method}();
            $this->assertSame(404, $response->getStatusCode());
            $payload = $response->getData(true);
            $this->assertSame('not_found', $payload['status'] ?? null);
            $this->assertStringNotContainsString('PDO', $response->getContent());
            $this->assertStringNotContainsString('Exception:', $response->getContent());
        }
    }

    public function test_pending_migrations_are_empty_after_migration(): void
    {
        Artisan::call('migrate', ['--force' => true]);
        $this->assertEmpty(InstallerController::getPendingMigrations());
    }

    public function test_branding_assets_exist(): void
    {
        $this->assertFileExists(public_path('safa-logo.png'));
        $this->assertFileExists(public_path('favicon.svg'));
        $this->assertGreaterThan(0, filesize(public_path('safa-logo.png')));
    }
}
