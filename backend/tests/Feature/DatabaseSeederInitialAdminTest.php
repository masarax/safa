<?php

namespace Tests\Feature;

use App\Models\AppVersion;
use App\Models\User;
use Database\Seeders\DatabaseSeeder;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Artisan;
use Tests\TestCase;

class DatabaseSeederInitialAdminTest extends TestCase
{
    use RefreshDatabase;

    public function test_programmatic_database_seed_skips_identity_and_keeps_reference_updates(): void
    {
        $this->seed(DatabaseSeeder::class);

        $this->assertSame(0, User::query()->where('role', User::ROLE_SUPERADMIN)->count());
        $this->assertTrue(AppVersion::query()->where('platform', 'android')->exists());
    }

    public function test_first_admin_credentials_are_not_part_of_application_or_env_configuration(): void
    {
        $config = (string) file_get_contents(config_path('safa.php'));
        $envExample = (string) file_get_contents(base_path('.env.example'));

        foreach ([
            'SAFA_INITIAL_ADMIN_NAME',
            'SAFA_INITIAL_ADMIN_MOBILE',
            'SAFA_INITIAL_ADMIN_EMAIL',
            'SAFA_INITIAL_ADMIN_PIN',
            "'initial_admin'",
        ] as $forbidden) {
            $this->assertStringNotContainsString($forbidden, $config);
            $this->assertStringNotContainsString($forbidden, $envExample);
        }
    }

    public function test_interactive_database_seeder_is_the_only_supported_first_superadmin_cli_path(): void
    {
        $this->assertFileDoesNotExist(app_path('Console/Commands/ProvisionSuperAdmin.php'));

        Artisan::call('list', ['--raw' => true]);
        $this->assertStringNotContainsString('safa:provision-admin', Artisan::output());

        $rootReadme = (string) file_get_contents(base_path('../README.md'));
        $backendReadme = (string) file_get_contents(base_path('README.md'));

        foreach ([$rootReadme, $backendReadme] as $documentation) {
            $this->assertStringNotContainsString('safa:provision-admin', $documentation);
            $this->assertStringNotContainsString('SAFA_INITIAL_ADMIN_', $documentation);
        }

        $this->assertStringContainsString('php artisan db:seed', $rootReadme);
        $this->assertStringContainsString('php artisan db:seed', $backendReadme);
    }
}
