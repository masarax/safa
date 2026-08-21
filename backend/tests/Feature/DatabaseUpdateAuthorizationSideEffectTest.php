<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Config;
use Illuminate\Support\Facades\DB;
use Tests\TestCase;

class DatabaseUpdateAuthorizationSideEffectTest extends TestCase
{
    use RefreshDatabase;

    private const LEGACY_MIGRATION = '0001_01_01_000000_create_users_table';

    protected function setUp(): void
    {
        parent::setUp();
        Config::set('safa.enforce_update_checks_in_tests', true);
        Config::set('safa.enforce_release_update_in_tests', true);
    }

    public function test_public_release_update_view_does_not_repair_migration_metadata(): void
    {
        DB::table('migrations')->where('migration', self::LEGACY_MIGRATION)->delete();
        $this->assertDatabaseMissing('migrations', ['migration' => self::LEGACY_MIGRATION]);

        $this->get('/update')
            ->assertOk()
            ->assertSee('System Update Ready')
            ->assertSee('Run Update');

        $this->assertDatabaseMissing('migrations', ['migration' => self::LEGACY_MIGRATION]);
    }

    public function test_authenticated_release_update_view_is_also_read_only(): void
    {
        $admin = User::factory()->create([
            'role' => User::ROLE_ADMIN,
            'is_activated' => true,
        ]);
        DB::table('migrations')->where('migration', self::LEGACY_MIGRATION)->delete();
        $this->assertDatabaseMissing('migrations', ['migration' => self::LEGACY_MIGRATION]);

        $this->actingAs($admin)->get('/update')->assertOk();

        $this->assertDatabaseMissing('migrations', ['migration' => self::LEGACY_MIGRATION]);
    }
}
