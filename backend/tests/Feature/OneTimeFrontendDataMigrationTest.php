<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\Customer;
use App\Models\User;
use App\Services\RequiredInitialSuperAdminService;
use App\Support\CredentialVerifier;
use App\Support\FirstRunSetupCode;
use App\Support\FirstRunSetupState;
use App\Support\OneTimeFrontendMigrationState;
use App\Support\RequiredInitialSuperAdminState;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Config;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Schema;
use Tests\TestCase;

class OneTimeFrontendDataMigrationTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        Config::set('safa.enforce_update_checks_in_tests', true);
        Config::set('safa.enforce_frontend_migration_in_tests', true);
        FirstRunSetupCode::destroy();
    }

    protected function tearDown(): void
    {
        FirstRunSetupCode::destroy();
        parent::tearDown();
    }

    public function test_existing_database_data_is_preserved_and_required_superadmin_is_created_once(): void
    {
        $passwordHash = Hash::make('existing-password');
        $pinHash = Hash::make('654321');
        $existingUser = User::factory()->create([
            'name' => 'Existing Owner',
            'mobile' => '0500000197',
            'email' => 'existing-197@safa.test',
            'password' => $passwordHash,
            'pin_hash' => $pinHash,
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);
        $account = Account::create([
            'owner_user_id' => $existingUser->id,
            'name' => 'Existing Account',
            'balance' => '777.50',
        ]);
        $customer = Customer::create([
            'account_id' => $account->id,
            'local_id' => 207,
            'name' => 'Existing Customer',
            'phone' => '0500000297',
        ]);

        $this->assertTrue(OneTimeFrontendMigrationState::required());
        $this->get('/')->assertRedirect(route('frontend.migration.show'));
        $this->get('/data-migration?lang=bn')
            ->assertOk()
            ->assertSee('প্রথমবার ডাটা মাইগ্রেশন')
            ->assertSee('ডাটা মাইগ্রেশন চালান')
            ->assertSeeHtml('data-testid="required-superadmin-ownership-proof"')
            ->assertSeeHtml('data-testid="frontend-migration-setup-code"');

        $this->assertFileExists(FirstRunSetupCode::path());
        $setupCode = trim((string) file_get_contents(FirstRunSetupCode::path()));

        $this->post('/data-migration', [
            'language' => 'en',
            'setup_code' => str_repeat('0', 32),
        ])->assertRedirect(route('frontend.migration.show', ['lang' => 'en']));
        $this->assertTrue(OneTimeFrontendMigrationState::required());
        $this->assertDatabaseMissing('users', ['email' => RequiredInitialSuperAdminService::EMAIL]);

        $this->post('/data-migration', [
            'language' => 'en',
            'setup_code' => $setupCode,
        ])->assertRedirect(route('safa.login', ['lang' => 'en']));

        $required = User::query()->where('email', RequiredInitialSuperAdminService::EMAIL)->firstOrFail();
        $this->assertSame(RequiredInitialSuperAdminService::NAME, $required->name);
        $this->assertTrue($required->isSuperAdmin());
        $this->assertTrue((bool) $required->is_activated);
        $this->assertTrue(CredentialVerifier::verify(RequiredInitialSuperAdminService::INITIAL_PIN, [
            $required->pin_hash,
            $required->password,
        ]));
        $this->assertSame(1, User::query()->where('email', RequiredInitialSuperAdminService::EMAIL)->count());
        $this->assertTrue(RequiredInitialSuperAdminState::completed());

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

        $existingUser->refresh();
        $this->assertSame($passwordHash, $existingUser->password);
        $this->assertSame($pinHash, $existingUser->pin_hash);

        $this->post('/login', [
            'identity' => RequiredInitialSuperAdminService::EMAIL,
            'credential' => RequiredInitialSuperAdminService::INITIAL_PIN,
            'language' => 'en',
        ])->assertRedirect(route('safa.app'));

        $this->assertFalse(OneTimeFrontendMigrationState::required());
        $this->assertFileDoesNotExist(FirstRunSetupCode::path());
        $this->get('/data-migration')->assertNotFound();
        $this->post('/data-migration')->assertNotFound();
    }

    public function test_legacy_consumed_install_without_required_superadmin_reopens_exactly_once_for_repair(): void
    {
        $existingUser = User::factory()->create([
            'name' => 'Legacy Owner',
            'email' => 'legacy-owner@safa.test',
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);
        $account = Account::create([
            'owner_user_id' => $existingUser->id,
            'name' => 'Legacy Business',
            'balance' => '500.00',
        ]);

        OneTimeFrontendMigrationState::markCompleted();
        Schema::dropIfExists(RequiredInitialSuperAdminState::TABLE);
        DB::table('migrations')
            ->where('migration', '2026_08_21_020000_create_required_superadmin_state')
            ->delete();

        $this->assertTrue(OneTimeFrontendMigrationState::required());
        $this->get('/')->assertRedirect(route('frontend.migration.show'));
        $this->get('/data-migration')
            ->assertOk()
            ->assertSee(FirstRunSetupCode::operatorPath())
            ->assertSeeHtml('data-testid="frontend-migration-setup-code"');

        $setupCode = trim((string) file_get_contents(FirstRunSetupCode::path()));
        $this->post('/data-migration', [
            'language' => 'en',
            'setup_code' => $setupCode,
        ])->assertRedirect(route('safa.login', ['lang' => 'en']));

        $this->assertTrue(Schema::hasTable(RequiredInitialSuperAdminState::TABLE));
        $this->assertTrue(RequiredInitialSuperAdminState::completed());
        $this->assertDatabaseHas('migrations', [
            'migration' => '2026_08_21_020000_create_required_superadmin_state',
        ]);
        $this->assertDatabaseHas('users', [
            'name' => RequiredInitialSuperAdminService::NAME,
            'email' => RequiredInitialSuperAdminService::EMAIL,
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => 1,
        ]);
        $this->assertDatabaseHas('accounts', [
            'id' => $account->id,
            'name' => 'Legacy Business',
            'balance' => '500.00',
        ]);

        User::query()->where('email', RequiredInitialSuperAdminService::EMAIL)->delete();
        $this->assertTrue(RequiredInitialSuperAdminState::completed());
        $this->get('/data-migration')->assertNotFound();
        $this->post('/data-migration')->assertNotFound();
    }

    public function test_matching_email_is_reconciled_without_creating_a_duplicate(): void
    {
        $existing = User::factory()->create([
            'name' => 'Wrong Name',
            'email' => RequiredInitialSuperAdminService::EMAIL,
            'password' => Hash::make('wrong-password'),
            'pin_hash' => Hash::make('999999'),
            'role' => User::ROLE_USER,
            'is_activated' => false,
        ]);

        $this->get('/data-migration')->assertOk();
        $setupCode = trim((string) file_get_contents(FirstRunSetupCode::path()));

        $this->post('/data-migration', [
            'language' => 'en',
            'setup_code' => $setupCode,
        ])->assertRedirect(route('safa.login', ['lang' => 'en']));

        $existing->refresh();
        $this->assertSame(RequiredInitialSuperAdminService::NAME, $existing->name);
        $this->assertSame(RequiredInitialSuperAdminService::EMAIL, $existing->email);
        $this->assertTrue($existing->isSuperAdmin());
        $this->assertTrue((bool) $existing->is_activated);
        $this->assertTrue(CredentialVerifier::verify(RequiredInitialSuperAdminService::INITIAL_PIN, [
            $existing->pin_hash,
            $existing->password,
        ]));
        $this->assertSame(1, User::query()->where('email', RequiredInitialSuperAdminService::EMAIL)->count());
        $this->assertTrue(RequiredInitialSuperAdminState::completed());
    }

    public function test_already_correct_required_superadmin_is_not_reset_during_first_migration(): void
    {
        $hash = Hash::make(RequiredInitialSuperAdminService::INITIAL_PIN);
        $required = User::factory()->create([
            'name' => RequiredInitialSuperAdminService::NAME,
            'email' => RequiredInitialSuperAdminService::EMAIL,
            'password' => $hash,
            'pin_hash' => $hash,
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);

        $this->get('/data-migration')
            ->assertOk()
            ->assertDontSee('data-testid="frontend-migration-setup-code"', false);
        $this->assertFileDoesNotExist(FirstRunSetupCode::path());

        $this->post('/data-migration', ['language' => 'en'])
            ->assertRedirect(route('safa.login', ['lang' => 'en']));

        $required->refresh();
        $this->assertSame($hash, $required->password);
        $this->assertSame($hash, $required->pin_hash);
        $this->assertSame(1, User::query()->where('email', RequiredInitialSuperAdminService::EMAIL)->count());
        $this->assertTrue(RequiredInitialSuperAdminState::completed());
    }

    public function test_consumed_frontend_migration_never_reopens_or_resets_required_superadmin(): void
    {
        $hash = Hash::make(RequiredInitialSuperAdminService::INITIAL_PIN);
        $required = User::factory()->create([
            'name' => RequiredInitialSuperAdminService::NAME,
            'email' => RequiredInitialSuperAdminService::EMAIL,
            'password' => $hash,
            'pin_hash' => $hash,
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);
        OneTimeFrontendMigrationState::markCompleted();
        RequiredInitialSuperAdminState::markCompleted();
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
            ->whereNotIn('migration', [
                '2026_08_21_010000_create_frontend_migration_state',
                '2026_08_21_020000_create_required_superadmin_state',
            ])
            ->orderByDesc('migration')
            ->value('migration');
        $this->assertNotNull($migration);
        DB::table('migrations')->where('migration', $migration)->delete();

        $this->get('/data-migration')->assertNotFound();
        $this->post('/data-migration')->assertNotFound();
        $this->get('/')->assertRedirect(route('system.update.show'));

        $required->refresh();
        $this->assertSame($hash, $required->password);
        $this->assertSame($hash, $required->pin_hash);
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
