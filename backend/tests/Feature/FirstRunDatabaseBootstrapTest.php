<?php

namespace Tests\Feature;

use App\Models\User;
use App\Support\FirstRunSetupState;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\Config;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;
use Tests\TestCase;

class FirstRunDatabaseBootstrapTest extends TestCase
{
    private string $databasePath;
    private string $originalConnection;
    private string $originalSessionDriver;
    private string $originalCacheStore;

    protected function setUp(): void
    {
        parent::setUp();

        $this->originalConnection = (string) config('database.default');
        $this->originalSessionDriver = (string) config('session.driver');
        $this->originalCacheStore = (string) config('cache.default');
        $this->databasePath = storage_path('framework/first-run-test-' . bin2hex(random_bytes(8)) . '.sqlite');
        if (!is_dir(dirname($this->databasePath))) mkdir(dirname($this->databasePath), 0775, true);
        touch($this->databasePath);

        Config::set('database.connections.first_run_test', [
            'driver' => 'sqlite',
            'url' => null,
            'database' => $this->databasePath,
            'prefix' => '',
            'foreign_key_constraints' => true,
            'busy_timeout' => 5000,
            'journal_mode' => null,
            'synchronous' => null,
        ]);
        Config::set('database.default', 'first_run_test');
        Config::set('safa.enforce_update_checks_in_tests', true);
        DB::purge('first_run_test');
    }

    protected function tearDown(): void
    {
        DB::disconnect('first_run_test');
        DB::purge('first_run_test');
        Config::set('database.default', $this->originalConnection);
        Config::set('session.driver', $this->originalSessionDriver);
        Config::set('cache.default', $this->originalCacheStore);
        @unlink($this->databasePath);

        parent::tearDown();
    }

    public function test_pristine_database_can_be_initialized_once_then_normal_login_takes_over(): void
    {
        $this->assertFalse(Schema::hasTable('migrations'));
        $this->assertFalse(Schema::hasTable('users'));

        $this->get('/')->assertRedirect(route('setup.database.show'));
        $this->get('/setup/database')
            ->assertOk()
            ->assertSee('Initialize Database')
            ->assertSeeHtml('data-first-run-action="initialize-database"');

        $this->post('/setup/database')
            ->assertRedirect(route('setup.admin.show'));

        $this->assertTrue(Schema::hasTable('migrations'));
        $this->assertTrue(Schema::hasTable(FirstRunSetupState::TABLE));
        $this->assertDatabaseHas(FirstRunSetupState::TABLE, ['id' => 1, 'completed_at' => null]);

        // The migration surface disappears immediately after the schema exists.
        $this->get('/setup/database')->assertNotFound();
        $this->post('/setup/database')->assertNotFound();

        $this->get('/setup/admin')
            ->assertOk()
            ->assertSee('Create First SuperAdmin')
            ->assertSeeHtml('data-first-run-action="create-superadmin"');

        $this->post('/setup/admin', [
            'name' => 'Initial Owner',
            'mobile' => '0536308965',
            'email' => 'owner@safa.test',
            'pin' => '123456',
            'pin_confirmation' => '123456',
        ])->assertRedirect(route('safa.login'));

        $this->assertDatabaseHas('users', [
            'email' => 'owner@safa.test',
            'mobile' => '0536308965',
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => 1,
        ]);
        $ownerId = (int) User::query()->where('email', 'owner@safa.test')->value('id');
        $this->assertDatabaseHas('accounts', ['owner_user_id' => $ownerId, 'name' => 'SAFA Account']);
        $this->assertNotNull(DB::table(FirstRunSetupState::TABLE)->where('id', 1)->value('completed_at'));

        // First-run endpoints can never be replayed after successful completion.
        $this->get('/setup/database')->assertNotFound();
        $this->post('/setup/database')->assertNotFound();
        $this->get('/setup/admin')->assertNotFound();
        $this->post('/setup/admin')->assertNotFound();
        $this->get('/login')->assertOk();
    }

    public function test_ordinary_future_pending_migration_never_reopens_public_first_run_setup(): void
    {
        $this->assertSame(0, Artisan::call('migrate', ['--force' => true]));
        $latest = DB::table('migrations')->orderByDesc('batch')->orderByDesc('migration')->value('migration');
        $this->assertNotNull($latest);
        DB::table('migrations')->where('migration', $latest)->delete();

        $this->assertFalse(FirstRunSetupState::databaseInitializationRequired());
        $this->get('/setup/database')->assertNotFound();
        $this->post('/setup/database')->assertNotFound();
        $this->get('/setup/admin')->assertNotFound();
        $this->get('/')->assertRedirect(route('system.update.show'));
    }

    public function test_api_reports_first_run_required_without_exposing_installer_routes(): void
    {
        $this->getJson('/api/auth/health')
            ->assertStatus(503)
            ->assertJson([
                'status' => 'setup_required',
                'phase' => 'database',
            ]);

        foreach (['/install', '/install/process', '/system/update', '/update-db'] as $path) {
            $this->get($path)->assertNotFound();
            $this->post($path)->assertNotFound();
        }
    }
}
