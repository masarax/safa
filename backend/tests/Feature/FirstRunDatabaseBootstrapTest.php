<?php

namespace Tests\Feature;

use App\Models\User;
use App\Support\CredentialVerifier;
use App\Support\ReleaseUpdateState;
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
        $this->databasePath = storage_path('framework/release-update-test-' . bin2hex(random_bytes(8)) . '.sqlite');
        if (!is_dir(dirname($this->databasePath))) {
            mkdir(dirname($this->databasePath), 0775, true);
        }
        touch($this->databasePath);

        Config::set('database.connections.release_update_test', [
            'driver' => 'sqlite',
            'url' => null,
            'database' => $this->databasePath,
            'prefix' => '',
            'foreign_key_constraints' => true,
            'busy_timeout' => 5000,
            'journal_mode' => null,
            'synchronous' => null,
        ]);
        Config::set('database.default', 'release_update_test');
        Config::set('safa.enforce_update_checks_in_tests', true);
        Config::set('safa.enforce_release_update_in_tests', true);
        DB::purge('release_update_test');
    }

    protected function tearDown(): void
    {
        DB::disconnect('release_update_test');
        DB::purge('release_update_test');
        Config::set('database.default', $this->originalConnection);
        Config::set('session.driver', $this->originalSessionDriver);
        Config::set('cache.default', $this->originalCacheStore);
        @unlink($this->databasePath);

        parent::tearDown();
    }

    public function test_pristine_database_is_initialized_from_the_clean_release_update_page(): void
    {
        $this->assertFalse(Schema::hasTable('migrations'));
        $this->assertFalse(Schema::hasTable('users'));

        $this->get('/')->assertRedirect(route('system.update.show'));
        $this->get('/update')
            ->assertOk()
            ->assertSee('System Update Ready')
            ->assertSee('Run Update')
            ->assertDontSee('setup code')
            ->assertDontSee('Pending migration');

        $this->post('/update/run', ['language' => 'en'])
            ->assertRedirect(route('safa.login', ['lang' => 'en']));

        $this->assertTrue(Schema::hasTable('migrations'));
        $this->assertTrue(Schema::hasTable(ReleaseUpdateState::TABLE));
        $this->assertFalse(ReleaseUpdateState::required());

        $required = User::query()->where('email', 'sakib.masarax@gmail.com')->firstOrFail();
        $this->assertSame('NAZMUS SAKIB', $required->name);
        $this->assertTrue($required->isSuperAdmin());
        $this->assertTrue(CredentialVerifier::verify('123456', [$required->pin_hash, $required->password]));

        $this->get('/update')->assertRedirect(route('safa.login'));
        $this->get('/data-migration')->assertNotFound();
        $this->get('/setup')->assertNotFound();
        $this->get('/login')->assertOk();
    }

    public function test_prepared_empty_schema_uses_same_release_update_without_resetting_schema(): void
    {
        $this->assertSame(0, Artisan::call('migrate', ['--force' => true]));
        $this->assertTrue(Schema::hasTable('users'));
        $this->assertSame(0, DB::table('users')->count());
        $this->assertTrue(ReleaseUpdateState::required());

        $this->get('/update')->assertOk()->assertSee('Run Update');
        $this->post('/update/run', ['language' => 'en'])->assertRedirect(route('safa.login', ['lang' => 'en']));

        $this->assertSame([], \App\Http\Controllers\DatabaseUpdateController::pendingMigrations());
        $this->assertDatabaseHas('users', [
            'name' => 'NAZMUS SAKIB',
            'email' => 'sakib.masarax@gmail.com',
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => 1,
        ]);
        $this->assertFalse(ReleaseUpdateState::required());
        $this->get('/update')->assertRedirect(route('safa.login'));
    }

    public function test_api_reports_release_update_required_without_installer_metadata(): void
    {
        $this->getJson('/api/auth/health')
            ->assertStatus(503)
            ->assertJson([
                'status' => 'update_required',
                'update_path' => '/update',
            ])
            ->assertJsonMissingPath('setup_path')
            ->assertJsonMissingPath('migration_path')
            ->assertJsonMissingPath('pending_count');
    }
}
