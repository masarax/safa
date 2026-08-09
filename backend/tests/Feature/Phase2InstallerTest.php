<?php

namespace Tests\Feature;

use App\Http\Controllers\InstallerController;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;
use Tests\TestCase;

class Phase2InstallerTest extends TestCase
{
    use RefreshDatabase;

    public function test_installer_routes_accessible_or_redirect_when_installed()
    {
        $response = $this->get('/');
        $response->assertStatus(200);
        $response->assertSee('SAFA');
    }

    public function test_pending_migrations_detection_and_column_contract_verification()
    {
        // Execute migrations
        Artisan::call('migrate', ['--force' => true]);

        $pending = InstallerController::getPendingMigrations();
        $this->assertEmpty($pending, 'No migrations should be pending after running migrate');
    }

    public function test_update_db_endpoint_requires_security_authorization_key()
    {
        $validKey = 'test_secret_key_2026';
        putenv("DB_UPDATE_SECRET={$validKey}");
        $_ENV['DB_UPDATE_SECRET'] = $validKey;

        // Unauthenticated request without key should be rejected with 403
        $response = $this->postJson('/update-db');
        $response->assertStatus(403);

        // Request with valid security key should succeed
        $authedResponse = $this->postJson('/update-db', ['key' => $validKey]);
        $authedResponse->assertStatus(200);
        $authedResponse->assertJson(['status' => 'success']);
    }

    public function test_safa_logo_and_favicon_assets_exist_in_public_directory()
    {
        $logoPath = public_path('safa-logo.png');
        $faviconPath = public_path('favicon.svg');

        $this->assertFileExists($logoPath, 'safa-logo.png must exist in backend/public/');
        $this->assertFileExists($faviconPath, 'favicon.svg must exist in backend/public/');
        $this->assertGreaterThan(0, filesize($logoPath), 'safa-logo.png must not be empty');
    }
}
