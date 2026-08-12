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

    public function test_pending_migrations_are_empty_after_migration(): void
    {
        Artisan::call('migrate', ['--force' => true]);
        $this->assertEmpty(InstallerController::getPendingMigrations());
    }

    public function test_branding_assets_exist(): void
    {
        $this->assertFileExists(public_path('safa-logo.png')); $this->assertFileExists(public_path('favicon.svg')); $this->assertGreaterThan(0, filesize(public_path('safa-logo.png')));
    }
}
