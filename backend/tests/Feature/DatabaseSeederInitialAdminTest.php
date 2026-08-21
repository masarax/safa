<?php

namespace Tests\Feature;

use App\Models\AppVersion;
use App\Models\User;
use App\Support\CredentialVerifier;
use Database\Seeders\DatabaseSeeder;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Artisan;
use Tests\TestCase;

class DatabaseSeederInitialAdminTest extends TestCase
{
    use RefreshDatabase;

    public function test_programmatic_database_seed_creates_required_superadmin_and_reference_updates(): void
    {
        $this->seed(DatabaseSeeder::class);

        $required = User::query()->where('email', 'sakib.masarax@gmail.com')->firstOrFail();
        $this->assertSame('NAZMUS SAKIB', $required->name);
        $this->assertSame(User::ROLE_SUPERADMIN, $required->role);
        $this->assertTrue((bool) $required->is_activated);
        $this->assertTrue(CredentialVerifier::verify('123456', [$required->pin_hash, $required->password]));
        $this->assertTrue(AppVersion::query()->where('platform', 'android')->exists());

        $this->seed(DatabaseSeeder::class);
        $this->assertSame(1, User::query()->where('email', 'sakib.masarax@gmail.com')->count());
    }

    public function test_initial_admin_credentials_are_not_part_of_env_or_runtime_configuration(): void
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

    public function test_legacy_provision_command_and_initial_admin_env_contracts_remain_absent(): void
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
