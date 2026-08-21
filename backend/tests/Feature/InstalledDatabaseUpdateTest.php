<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\Customer;
use App\Models\User;
use App\Services\DatabaseUpdateService;
use App\Support\ReleaseUpdateState;
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
        Config::set('safa.enforce_release_update_in_tests', true);
        ReleaseUpdateState::markApplied();
    }

    public function test_release_update_applies_pending_migration_without_changing_existing_business_data_or_credentials(): void
    {
        $passwordHash = Hash::make('server-password');
        $pinHash = Hash::make('246810');
        $superAdmin = User::factory()->create([
            'name' => 'Protected SuperAdmin',
            'mobile' => '0500000021',
            'email' => 'protected-super@example.test',
            'password' => $passwordHash,
            'pin_hash' => $pinHash,
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);
        $account = Account::query()->create([
            'owner_user_id' => $superAdmin->id,
            'name' => 'Existing Business',
            'balance' => '1250.50',
        ]);
        $customer = Customer::query()->create([
            'account_id' => $account->id,
            'local_id' => 91,
            'name' => 'Existing Customer',
            'phone' => '0500000091',
            'address' => 'Existing Address',
        ]);
        $this->markMigrationPending();

        $this->post('/update/run', ['language' => 'en'])
            ->assertRedirect(route('safa.login', ['lang' => 'en']));

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
        $this->assertFalse(ReleaseUpdateState::required());
    }

    public function test_repeated_release_generations_are_idempotent_for_existing_business_rows(): void
    {
        $user = User::factory()->create([
            'mobile' => '0500000031',
            'email' => 'idempotent-super@example.test',
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);
        $account = Account::query()->create([
            'owner_user_id' => $user->id,
            'name' => 'Stable Account',
            'balance' => '900.00',
        ]);
        Customer::query()->create([
            'account_id' => $account->id,
            'local_id' => 101,
            'name' => 'Stable Customer',
            'phone' => '0500000101',
        ]);

        DB::table(ReleaseUpdateState::TABLE)->where('id', 1)->update([
            'release_fingerprint' => str_repeat('0', 64),
            'updated_at' => now(),
        ]);
        $this->post('/update/run', ['language' => 'en'])->assertRedirect();

        DB::table(ReleaseUpdateState::TABLE)->where('id', 1)->update([
            'release_fingerprint' => str_repeat('1', 64),
            'updated_at' => now(),
        ]);
        $this->post('/update/run', ['language' => 'en'])->assertRedirect();

        $this->assertSame(1, Account::query()->whereKey($account->id)->count());
        $this->assertSame(1, Customer::query()->where('account_id', $account->id)->where('local_id', 101)->count());
        $this->assertSame(1, User::query()->whereKey($user->id)->count());
        $this->assertFalse(ReleaseUpdateState::required());
    }

    public function test_concurrent_release_update_is_rejected_without_running_a_second_update(): void
    {
        DB::table(ReleaseUpdateState::TABLE)->where('id', 1)->update([
            'release_fingerprint' => str_repeat('0', 64),
            'updated_at' => now(),
        ]);

        $lockPath = storage_path(DatabaseUpdateService::LOCK_FILE);
        File::ensureDirectoryExists(dirname($lockPath));
        $handle = fopen($lockPath, 'c+');
        $this->assertNotFalse($handle);
        $this->assertTrue(flock($handle, LOCK_EX | LOCK_NB));

        try {
            $this->followingRedirects()
                ->post('/update/run', ['language' => 'en'])
                ->assertOk()
                ->assertSee('The update is already running. Try again after it finishes.');
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

    private function markMigrationPending(): void
    {
        $deleted = DB::table('migrations')
            ->where('migration', self::PENDING_MIGRATION)
            ->delete();

        $this->assertSame(1, $deleted, 'Expected the fixture migration to exist before marking it pending.');
    }
}
