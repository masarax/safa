<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\Customer;
use App\Models\User;
use App\Services\DatabaseUpdateService;
use App\Support\OneTimeFrontendMigrationState;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Config;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\File;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class InstalledDatabaseUpdateTest extends TestCase
{
    use RefreshDatabase;

    private const PENDING_MIGRATION = '2026_08_10_000002_add_address_to_customers';

    protected function setUp(): void
    {
        parent::setUp();
        Config::set('safa.enforce_update_checks_in_tests', true);
        OneTimeFrontendMigrationState::markCompleted();
    }

    public function test_only_activated_superadmin_can_execute_database_update(): void
    {
        $activeSuperAdmin = $this->superAdmin('0500000001', 'super@example.test');
        $admin = User::factory()->create([
            'mobile' => '0500000002',
            'email' => 'admin@example.test',
            'role' => User::ROLE_ADMIN,
            'is_activated' => true,
        ]);
        $inactiveSuperAdmin = User::factory()->create([
            'mobile' => '0500000003',
            'email' => 'inactive-super@example.test',
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => false,
        ]);

        $this->post('/update/run')->assertRedirect(route('safa.login'));
        $this->actingAs($admin)->post('/update/run')->assertForbidden();
        $this->actingAs($inactiveSuperAdmin)->post('/update/run')->assertForbidden();
        $this->actingAs($activeSuperAdmin)->post('/update/run')->assertRedirect(route('safa.app'));
    }

    public function test_database_update_setting_is_visible_only_to_superadmin_in_both_languages(): void
    {
        $superAdmin = $this->superAdmin('0500000011', 'settings-super@example.test');
        $admin = User::factory()->create([
            'mobile' => '0500000012',
            'email' => 'settings-admin@example.test',
            'role' => User::ROLE_ADMIN,
            'is_activated' => true,
        ]);

        $this->actingAs($superAdmin)
            ->withSession(['safa_web_language' => 'en'])
            ->get('/app')
            ->assertOk()
            ->assertSeeHtml('id="database-update-settings"')
            ->assertSee('Database Update');

        $this->actingAs($superAdmin)
            ->withSession(['safa_web_language' => 'bn'])
            ->get('/app')
            ->assertOk()
            ->assertSee('ডাটাবেজ আপডেট');

        $this->actingAs($admin)
            ->get('/app')
            ->assertOk()
            ->assertDontSeeHtml('id="database-update-settings"')
            ->assertDontSee('Run Database Update');
    }

    public function test_one_click_update_applies_pending_migration_without_changing_business_data_or_credentials(): void
    {
        $passwordHash = Hash::make('server-password');
        $pinHash = Hash::make('123456');
        $superAdmin = User::factory()->create([
            'name' => 'Protected SuperAdmin',
            'mobile' => '0500000021',
            'email' => 'protected-super@example.test',
            'password' => $passwordHash,
            'pin_hash' => $pinHash,
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);
        $account = Account::create([
            'owner_user_id' => $superAdmin->id,
            'name' => 'Existing Business',
            'balance' => '1250.50',
        ]);
        $customer = Customer::create([
            'account_id' => $account->id,
            'local_id' => 91,
            'name' => 'Existing Customer',
            'phone' => '0500000091',
            'address' => 'Existing Address',
        ]);
        $this->markMigrationPending();

        $this->actingAs($superAdmin)
            ->post('/update/run')
            ->assertRedirect(route('safa.app'));

        $this->assertDatabaseHas('migrations', ['migration' => self::PENDING_MIGRATION]);
        $this->assertDatabaseHas('accounts', [
            'id' => $account->id,
            'name' => 'Existing Business',
            'balance' => '1250.50',
        ]);
        $this->assertDatabaseHas('customers', [
            'id' => $customer->id,
            'account_id' => $account->id,
            'local_id' => 91,
            'name' => 'Existing Customer',
            'phone' => '0500000091',
            'address' => 'Existing Address',
        ]);

        $superAdmin->refresh();
        $this->assertSame($passwordHash, $superAdmin->password);
        $this->assertSame($pinHash, $superAdmin->pin_hash);
    }

    public function test_repeated_database_update_is_idempotent_for_business_rows_and_admin_credentials(): void
    {
        $passwordHash = Hash::make('unchanged-password');
        $pinHash = Hash::make('654321');
        $superAdmin = User::factory()->create([
            'mobile' => '0500000031',
            'email' => 'idempotent-super@example.test',
            'password' => $passwordHash,
            'pin_hash' => $pinHash,
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);
        $account = Account::create([
            'owner_user_id' => $superAdmin->id,
            'name' => 'Stable Account',
            'balance' => '900.00',
        ]);
        Customer::create([
            'account_id' => $account->id,
            'local_id' => 101,
            'name' => 'Stable Customer',
            'phone' => '0500000101',
        ]);

        $this->actingAs($superAdmin)->post('/update/run')->assertRedirect(route('safa.app'));
        $this->actingAs($superAdmin)->post('/update/run')->assertRedirect(route('safa.app'));

        $this->assertSame(1, Account::query()->whereKey($account->id)->count());
        $this->assertSame(1, Customer::query()->where('account_id', $account->id)->where('local_id', 101)->count());
        $this->assertSame(1, User::query()->whereKey($superAdmin->id)->count());

        $superAdmin->refresh();
        $this->assertSame($passwordHash, $superAdmin->password);
        $this->assertSame($pinHash, $superAdmin->pin_hash);
    }

    public function test_concurrent_database_update_is_rejected_without_running_a_second_update(): void
    {
        $superAdmin = $this->superAdmin('0500000041', 'locked-super@example.test');
        $lockPath = storage_path(DatabaseUpdateService::LOCK_FILE);
        File::ensureDirectoryExists(dirname($lockPath));
        $handle = fopen($lockPath, 'c+');
        $this->assertNotFalse($handle);
        $this->assertTrue(flock($handle, LOCK_EX | LOCK_NB));

        try {
            $this->actingAs($superAdmin)
                ->followingRedirects()
                ->post('/update/run')
                ->assertOk()
                ->assertSee('A database update is already running. Try again after it finishes.');
        } finally {
            flock($handle, LOCK_UN);
            fclose($handle);
        }
    }

    public function test_database_update_runner_has_no_destructive_framework_command_path(): void
    {
        $source = (string) file_get_contents(app_path('Services/DatabaseUpdateService.php'));

        $this->assertStringContainsString("Artisan::call('migrate'", $source);
        $this->assertStringContainsString('ReleaseDataUpdateSeeder::class', $source);
        foreach (['migrate:fresh', 'migrate:reset', 'migrate:refresh', 'migrate:rollback', "Artisan::call('db:wipe'", 'truncate('] as $forbidden) {
            $this->assertStringNotContainsString($forbidden, $source);
        }
    }

    private function superAdmin(string $mobile, string $email): User
    {
        return User::factory()->create([
            'mobile' => $mobile,
            'email' => $email,
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);
    }

    private function markMigrationPending(): void
    {
        $deleted = DB::table('migrations')
            ->where('migration', self::PENDING_MIGRATION)
            ->delete();

        $this->assertSame(1, $deleted, 'Expected the fixture migration to exist before marking it pending.');
    }
}
