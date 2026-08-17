<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\AppVersion;
use App\Models\User;
use Database\Seeders\DatabaseSeeder;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Config;
use RuntimeException;
use Tests\TestCase;

class DatabaseSeederInitialAdminTest extends TestCase
{
    use RefreshDatabase;

    public function test_database_seed_succeeds_when_initial_admin_configuration_is_completely_absent(): void
    {
        Config::set('safa.initial_admin', $this->initialAdminConfig());

        $this->seed(DatabaseSeeder::class);

        $this->assertSame(0, User::query()->where('role', User::ROLE_SUPERADMIN)->count());
        $this->assertTrue(AppVersion::query()->where('platform', 'android')->exists());
    }

    public function test_database_seed_rejects_partial_initial_admin_configuration(): void
    {
        Config::set('safa.initial_admin', $this->initialAdminConfig([
            'name' => 'Incomplete Admin',
        ]));

        $this->expectException(RuntimeException::class);
        $this->expectExceptionMessage('Initial Super Admin server configuration is incomplete or invalid.');

        $this->seed(DatabaseSeeder::class);
    }

    public function test_database_seed_creates_configured_initial_superadmin_and_workspace(): void
    {
        Config::set('safa.initial_admin', $this->initialAdminConfig([
            'name' => 'Configured Admin',
            'mobile' => '+880 1712-345678',
            'email' => 'Configured@Example.Test',
            'pin' => '654321',
        ]));

        $this->seed(DatabaseSeeder::class);

        $admin = User::query()->where('email', 'configured@example.test')->firstOrFail();
        $this->assertTrue($admin->isSuperAdmin());
        $this->assertTrue((bool) $admin->is_activated);
        $this->assertSame('01712345678', $admin->mobile);

        $account = Account::query()->firstOrFail();
        $this->assertSame($admin->id, (int) $account->owner_user_id);
    }

    private function initialAdminConfig(array $overrides = []): array
    {
        return array_merge([
            'name' => '',
            'mobile' => '',
            'email' => '',
            'pin' => '',
        ], $overrides);
    }
}
