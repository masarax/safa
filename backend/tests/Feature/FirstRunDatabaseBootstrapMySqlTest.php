<?php

namespace Tests\Feature;

use App\Models\User;
use App\Support\FirstRunSetupState;
use App\Support\OneTimeFrontendMigrationState;
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
    }

    public function test_empty_strict_mysql_database_completes_the_real_frontend_migration_and_bootstrap(): void
    {
        $this->assertFalse(Schema::hasTable('migrations'));
        $this->assertFalse(Schema::hasTable('users'));

        $this->get('/')->assertRedirect(route('frontend.migration.show'));
        $this->get('/data-migration')
            ->assertOk()
            ->assertSee('First-time Data Migration')
            ->assertSee('Run Data Migration');

        $this->post('/data-migration', ['language' => 'en'])
            ->assertRedirect(route('setup.admin.show', ['lang' => 'en']));

        $this->assertTrue(Schema::hasTable('migrations'));
        $this->assertTrue(Schema::hasTable(FirstRunSetupState::TABLE));
        $this->assertTrue(Schema::hasTable(OneTimeFrontendMigrationState::TABLE));
        $this->assertNotNull(DB::table(OneTimeFrontendMigrationState::TABLE)->where('id', 1)->value('completed_at'));
        $this->assertDatabaseHas(FirstRunSetupState::TABLE, ['id' => 1, 'completed_at' => null]);

        $this->get('/data-migration')->assertNotFound();
        $this->post('/data-migration')->assertNotFound();

        $this->get('/setup/admin')->assertOk();
        $this->post('/setup/admin', [
            'language' => 'en',
            'name' => 'MySQL First Owner',
            'mobile' => '0536308965',
            'email' => 'mysql-owner@safa.test',
            'pin' => '123456',
            'pin_confirmation' => '123456',
        ])->assertRedirect(route('safa.login', ['lang' => 'en']));

        $this->assertDatabaseHas('users', [
            'email' => 'mysql-owner@safa.test',
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => 1,
        ]);
        $this->assertNotNull(DB::table(FirstRunSetupState::TABLE)->where('id', 1)->value('completed_at'));
        $this->get('/data-migration')->assertNotFound();
        $this->get('/setup')->assertNotFound();
        $this->get('/setup/database')->assertNotFound();
        $this->get('/setup/admin')->assertNotFound();
        $this->getJson('/api/setup/status')->assertOk()->assertJson(['status' => 'ready']);
        $this->get('/login')->assertOk();
    }
}
