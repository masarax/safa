<?php

namespace Tests\Feature;

use App\Models\User;
use App\Support\FirstRunSetupCode;
use App\Support\FirstRunSetupState;
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
        FirstRunSetupCode::destroy();
    }

    protected function tearDown(): void
    {
        FirstRunSetupCode::destroy();
        parent::tearDown();
    }

    public function test_empty_strict_mysql_database_completes_the_real_browser_bootstrap(): void
    {
        $this->assertFalse(Schema::hasTable('migrations'));
        $this->assertFalse(Schema::hasTable('users'));

        $this->get('/')->assertRedirect(route('setup.index'));
        $this->get('/setup')->assertRedirect(route('setup.database.show', ['lang' => 'en']));
        $this->getJson('/api/setup/status')
            ->assertOk()
            ->assertJson(['status' => 'setup_required', 'phase' => 'database', 'setup_path' => '/setup']);

        $this->get('/setup/database')
            ->assertOk()
            ->assertSee('Initialize Database');

        $setupCode = trim((string) file_get_contents(FirstRunSetupCode::path()));
        $this->assertMatchesRegularExpression('/^[A-F0-9]{32}$/', $setupCode);

        $this->post('/setup/database', [
            'language' => 'en',
            'setup_code' => $setupCode,
        ])->assertRedirect(route('setup.admin.show', ['lang' => 'en']));

        $this->assertTrue(Schema::hasTable('migrations'));
        $this->assertTrue(Schema::hasTable(FirstRunSetupState::TABLE));
        $this->assertFileDoesNotExist(FirstRunSetupCode::path());
        $this->get('/setup/database')->assertNotFound();

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
        $this->get('/setup')->assertNotFound();
        $this->get('/setup/database')->assertNotFound();
        $this->get('/setup/admin')->assertNotFound();
        $this->getJson('/api/setup/status')->assertOk()->assertJson(['status' => 'ready']);
        $this->get('/login')->assertOk();
    }
}
