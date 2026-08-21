<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\Customer;
use App\Models\User;
use App\Support\FirstRunSetupState;
use App\Support\OneTimeFrontendMigrationState;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Config;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class OneTimeFrontendDataMigrationTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        Config::set('safa.enforce_update_checks_in_tests', true);
        Config::set('safa.enforce_frontend_migration_in_tests', true);
    }

    public function test_existing_database_data_still_sees_the_one_time_frontend_migration_and_is_preserved(): void
    {
        $passwordHash = Hash::make('existing-password');
        $pinHash = Hash::make('123456');
        $user = User::factory()->create([
            'name' => 'Existing Owner',
            'mobile' => '0500000197',
            'email' => 'existing-197@safa.test',
            'password' => $passwordHash,
            'pin_hash' => $pinHash,
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);
        $account = Account::create([
            'owner_user_id' => $user->id,
            'name' => 'Existing Account',
            'balance' => '777.50',
        ]);
        $customer = Customer::create([
            'account_id' => $account->id,
            'local_id' => 197,
            'name' => 'Existing Customer',
            'phone' => '0500000297',
        ]);

        $this->assertTrue(OneTimeFrontendMigrationState::required());
        $this->get('/')->assertRedirect(route('frontend.migration.show'));
        $this->get('/data-migration?lang=bn')
            ->assertOk()
            ->assertSee('প্রথমবার ডাটা মাইগ্রেশন')
            ->assertSee('ডাটা মাইগ্রেশন চালান');

        $this->post('/data-migration', ['language' => 'en'])
            ->assertRedirect('/');

        $this->assertFalse(OneTimeFrontendMigrationState::required());
        $this->assertDatabaseHas(OneTimeFrontendMigrationState::TABLE, ['id' => 1]);
        $this->assertDatabaseHas('users', ['id' => $user->id, 'email' => 'existing-197@safa.test']);
        $this->assertDatabaseHas('accounts', [
            'id' => $account->id,
            'name' => 'Existing Account',
            'balance' => '777.50',
        ]);
        $this->assertDatabaseHas('customers', [
            'id' => $customer->id,
            'account_id' => $account->id,
            'name' => 'Existing Customer',
        ]);

        $user->refresh();
        $this->assertSame($passwordHash, $user->password);
        $this->assertSame($pinHash, $user->pin_hash);

        $this->get('/data-migration')->assertNotFound();
        $this->post('/data-migration')->assertNotFound();
    }

    public function test_consumed_frontend_migration_never_reopens_for_future_pending_migrations(): void
    {
        OneTimeFrontendMigrationState::markCompleted();
        DB::table(FirstRunSetupState::TABLE)->updateOrInsert(
            ['id' => 1],
            [
                'bootstrap_claim_hash' => hash('sha256', str_repeat('c', 64)),
                'database_initialized_at' => now(),
                'completed_at' => now(),
                'created_at' => now(),
                'updated_at' => now(),
            ]
        );

        $migration = DB::table('migrations')
            ->where('migration', '!=', '2026_08_21_010000_create_frontend_migration_state')
            ->orderByDesc('migration')
            ->value('migration');
        $this->assertNotNull($migration);
        DB::table('migrations')->where('migration', $migration)->delete();

        $this->assertFalse(OneTimeFrontendMigrationState::required());
        $this->get('/data-migration')->assertNotFound();
        $this->post('/data-migration')->assertNotFound();
        $this->get('/')->assertRedirect(route('system.update.show'));
    }

    public function test_api_is_blocked_with_machine_readable_migration_required_state_until_consumed(): void
    {
        $this->getJson('/api/auth/health')
            ->assertStatus(503)
            ->assertJson([
                'status' => 'data_migration_required',
                'migration_path' => '/data-migration',
            ]);
    }
}
