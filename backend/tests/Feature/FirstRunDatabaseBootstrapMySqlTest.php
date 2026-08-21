<?php

namespace Tests\Feature;

use App\Models\User;
use App\Support\CredentialVerifier;
use App\Support\ReleaseUpdateState;
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
            $this->markTestSkipped('Dedicated empty-MySQL release update smoke only.');
        }

        $this->assertSame('mysql', DB::connection()->getDriverName());
        Config::set('safa.enforce_update_checks_in_tests', true);
        Config::set('safa.enforce_release_update_in_tests', true);
    }

    public function test_empty_strict_mysql_database_runs_clean_release_update_and_creates_required_superadmin(): void
    {
        $this->assertFalse(Schema::hasTable('migrations'));
        $this->assertFalse(Schema::hasTable('users'));

        $this->get('/')->assertRedirect(route('system.update.show'));
        $this->get('/update')
            ->assertOk()
            ->assertSee('System Update Ready')
            ->assertSee('Run Update')
            ->assertDontSee('Pending migrations')
            ->assertDontSee('setup code')
            ->assertDontSee('safa-first-run-setup-code.txt');

        $this->post('/update/run', ['language' => 'en'])
            ->assertRedirect(route('safa.login', ['lang' => 'en']));

        $this->assertTrue(Schema::hasTable('migrations'));
        $this->assertTrue(Schema::hasTable('users'));
        $this->assertTrue(Schema::hasTable(ReleaseUpdateState::TABLE));
        $this->assertFalse(ReleaseUpdateState::required());
        $this->assertNotNull(DB::table(ReleaseUpdateState::TABLE)->where('id', 1)->value('applied_at'));

        $required = User::query()->where('email', 'sakib.masarax@gmail.com')->firstOrFail();
        $this->assertSame('NAZMUS SAKIB', $required->name);
        $this->assertTrue($required->isSuperAdmin());
        $this->assertTrue((bool) $required->is_activated);
        $this->assertTrue(CredentialVerifier::verify('123456', [
            $required->pin_hash,
            $required->password,
        ]));
        $this->assertSame(1, User::query()->where('email', 'sakib.masarax@gmail.com')->count());
        $this->assertDatabaseHas('accounts', [
            'owner_user_id' => $required->id,
            'name' => 'SAFA Account',
        ]);

        $this->get('/update')->assertRedirect(route('safa.login'));
        $this->post('/update/run')->assertRedirect(route('safa.login'));
        $this->get('/data-migration')->assertNotFound();
        $this->get('/setup')->assertNotFound();
        $this->getJson('/api/setup/status')->assertNotFound();

        $this->post('/login', [
            'identity' => 'sakib.masarax@gmail.com',
            'credential' => '123456',
            'language' => 'en',
        ])->assertRedirect(route('safa.app'));
    }
}
