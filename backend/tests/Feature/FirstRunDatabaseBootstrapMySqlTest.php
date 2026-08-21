<?php

namespace Tests\Feature;

use App\Models\User;
use App\Services\RequiredInitialSuperAdminService;
use App\Support\CredentialVerifier;
use App\Support\FirstRunSetupCode;
use App\Support\FirstRunSetupState;
use App\Support\OneTimeFrontendMigrationState;
use App\Support\RequiredInitialSuperAdminState;
use Illuminate\Support\Facades\Config;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;
use Tests\TestCase;

class FirstRunDatabaseBootstrapMySqlTest extends TestCase
{
    protected function setUp(): void
    {
        parent::setUp();

        if (env('SAFA_MYSQL_FIRST_RUN_SMOKE') !== '1') {
            $this->markTestSkipped('Dedicated empty-MySQL first-run smoke only.');
        }

        $this->assertSame('mysql', DB::connection()->getDriverName());
        Config::set('safa.enforce_update_checks_in_tests', true);
        Config::set('safa.enforce_frontend_migration_in_tests', true);
        FirstRunSetupCode::destroy();
    }

    protected function tearDown(): void
    {
        FirstRunSetupCode::destroy();
        parent::tearDown();
    }

    public function test_empty_strict_mysql_database_auto_provisions_the_required_superadmin_once(): void
    {
        $this->assertFalse(Schema::hasTable('migrations'));
        $this->assertFalse(Schema::hasTable('users'));

        $this->get('/')->assertRedirect(route('frontend.migration.show'));
        $this->get('/data-migration')
            ->assertOk()
            ->assertSee('First-time Data Migration')
            ->assertSee('Run Data Migration')
            ->assertSee(FirstRunSetupCode::operatorPath())
            ->assertSeeHtml('data-testid="frontend-migration-setup-code"');

        $this->assertFileExists(FirstRunSetupCode::path());
        $setupCode = trim((string) file_get_contents(FirstRunSetupCode::path()));
        $this->assertMatchesRegularExpression('/^[A-F0-9]{32}$/', $setupCode);

        $this->post('/data-migration', [
            'language' => 'en',
            'setup_code' => str_repeat('0', 32),
        ])->assertRedirect(route('frontend.migration.show', ['lang' => 'en']));
        $this->assertFalse(Schema::hasTable('migrations'));

        $this->post('/data-migration', [
            'language' => 'en',
            'setup_code' => $setupCode,
        ])->assertRedirect(route('safa.login', ['lang' => 'en']));

        $this->assertTrue(Schema::hasTable('migrations'));
        $this->assertTrue(Schema::hasTable(FirstRunSetupState::TABLE));
        $this->assertTrue(Schema::hasTable(OneTimeFrontendMigrationState::TABLE));
        $this->assertTrue(Schema::hasTable(RequiredInitialSuperAdminState::TABLE));
        $this->assertNotNull(DB::table(OneTimeFrontendMigrationState::TABLE)->where('id', 1)->value('completed_at'));
        $this->assertTrue(RequiredInitialSuperAdminState::completed());
        $this->assertNotNull(DB::table(FirstRunSetupState::TABLE)->where('id', 1)->value('completed_at'));
        $this->assertFileDoesNotExist(FirstRunSetupCode::path());

        $required = User::query()->where('email', RequiredInitialSuperAdminService::EMAIL)->firstOrFail();
        $this->assertSame(RequiredInitialSuperAdminService::NAME, $required->name);
        $this->assertTrue($required->isSuperAdmin());
        $this->assertTrue((bool) $required->is_activated);
        $this->assertTrue(CredentialVerifier::verify(RequiredInitialSuperAdminService::INITIAL_PIN, [
            $required->pin_hash,
            $required->password,
        ]));
        $this->assertSame(1, User::query()->where('email', RequiredInitialSuperAdminService::EMAIL)->count());
        $this->assertDatabaseHas('accounts', [
            'owner_user_id' => $required->id,
            'name' => 'SAFA Account',
        ]);

        $this->get('/data-migration')->assertNotFound();
        $this->post('/data-migration')->assertNotFound();
        $this->get('/setup')->assertNotFound();
        $this->get('/setup/database')->assertNotFound();
        $this->get('/setup/admin')->assertNotFound();
        $this->getJson('/api/setup/status')->assertOk()->assertJson(['status' => 'ready']);

        $this->post('/login', [
            'identity' => RequiredInitialSuperAdminService::EMAIL,
            'credential' => RequiredInitialSuperAdminService::INITIAL_PIN,
            'language' => 'en',
        ])->assertRedirect(route('safa.app'));
    }
}
