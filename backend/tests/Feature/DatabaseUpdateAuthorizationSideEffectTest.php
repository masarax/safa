<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Tests\TestCase;

class DatabaseUpdateAuthorizationSideEffectTest extends TestCase
{
    use RefreshDatabase;

    private const LEGACY_MIGRATION = '0001_01_01_000000_create_users_table';

    public function test_forbidden_update_view_does_not_repair_migration_metadata(): void
    {
        $admin = User::factory()->create([
            'role' => User::ROLE_ADMIN,
            'is_activated' => true,
        ]);
        DB::table('migrations')->where('migration', self::LEGACY_MIGRATION)->delete();
        $this->assertDatabaseMissing('migrations', ['migration' => self::LEGACY_MIGRATION]);

        $this->actingAs($admin)->get('/update')->assertForbidden();

        $this->assertDatabaseMissing('migrations', ['migration' => self::LEGACY_MIGRATION]);
    }

    public function test_superadmin_update_view_is_read_only(): void
    {
        $superAdmin = User::factory()->create([
            'role' => User::ROLE_SUPERADMIN,
            'is_activated' => true,
        ]);
        DB::table('migrations')->where('migration', self::LEGACY_MIGRATION)->delete();
        $this->assertDatabaseMissing('migrations', ['migration' => self::LEGACY_MIGRATION]);

        $this->actingAs($superAdmin)->get('/update')->assertOk();

        $this->assertDatabaseMissing('migrations', ['migration' => self::LEGACY_MIGRATION]);
    }
}
