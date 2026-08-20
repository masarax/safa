<?php

namespace Tests\Feature;

use App\Models\OperatorAccount;
use App\Models\User;
use Illuminate\Database\UniqueConstraintViolationException;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class LegacyOperatorMigrationTest extends TestCase
{
    use RefreshDatabase;

    public function test_legacy_operator_is_migrated_and_can_authenticate(): void
    {
        $hash = Hash::make('123456');
        $operator = OperatorAccount::create([
            'name' => 'Legacy Operator', 'email' => 'legacy@safa.local', 'mobile' => '01912345678',
            'role' => 'staff', 'pin_hash' => $hash, 'is_activated' => true, 'permissions' => ['users.view' => true],
        ]);

        $this->artisan('safa:migrate-legacy-operators')->assertExitCode(0);

        $user = User::where('mobile', '01912345678')->firstOrFail();
        $this->assertSame($user->id, $operator->fresh()->user_id);
        $this->assertSame(User::ROLE_USER, $user->role);
        $this->assertTrue($user->is_activated);
        $this->assertTrue(Hash::check('123456', $user->pin_hash));
        // MySQL JSON objects do not guarantee key order; permissions are a map.
        $this->assertEquals(User::permissionsForRole(User::ROLE_USER), $user->permissions);

        $this->postJson('/api/auth/login', [
            'mobile' => '01912345678', 'pin' => '123456',
            'device_uuid' => 'migration-device', 'fingerprint_hash' => 'migration-fingerprint',
        ])->assertOk()->assertJsonPath('user.id', $user->id);
    }

    public function test_migration_is_idempotent(): void
    {
        $operator = OperatorAccount::create([
            'name' => 'Repeatable', 'email' => 'repeatable@safa.local', 'mobile' => '01987654321',
            'role' => 'manager', 'pin_hash' => Hash::make('123456'), 'is_activated' => true, 'permissions' => [],
        ]);

        $this->artisan('safa:migrate-legacy-operators')->assertExitCode(0);
        $userId = $operator->fresh()->user_id;
        $this->artisan('safa:migrate-legacy-operators')->assertExitCode(0);

        $this->assertSame($userId, $operator->fresh()->user_id);
        $this->assertSame(1, User::where('mobile', '01987654321')->count());
        $this->assertSame(User::ROLE_BUSINESS_USER, User::where('mobile', '01987654321')->value('role'));
    }

    public function test_canonical_mobile_uniqueness_prevents_ambiguous_live_identity(): void
    {
        User::create(['name'=>'A','email'=>'a@safa.local','mobile'=>'01911111111','pin_hash'=>Hash::make('123456'),'password'=>Hash::make('123456'),'role'=>'staff','is_activated'=>true,'permissions'=>[]]);

        $this->expectException(UniqueConstraintViolationException::class);
        User::create(['name'=>'B','email'=>'b@safa.local','mobile'=>'01911111111','pin_hash'=>Hash::make('123456'),'password'=>Hash::make('123456'),'role'=>'staff','is_activated'=>true,'permissions'=>[]]);
    }

    public function test_deactivated_canonical_user_is_not_exposed_by_login(): void
    {
        $user = User::create(['name'=>'Inactive','email'=>'inactive@safa.local','mobile'=>'01922222222','pin_hash'=>Hash::make('123456'),'password'=>Hash::make('123456'),'role'=>'staff','is_activated'=>false,'permissions'=>[]]);
        $this->postJson('/api/auth/login', ['mobile'=>'01922222222','pin'=>'123456','device_uuid'=>'inactive-device','fingerprint_hash'=>'inactive-fingerprint'])
            ->assertStatus(401)
            ->assertJsonPath('error.code', 'INVALID_CREDENTIALS')
            ->assertJsonPath('message', 'Mobile number or PIN is incorrect.');
        $this->assertNotNull($user->id);
    }

    public function test_legacy_only_account_is_not_a_second_live_credential_source(): void
    {
        OperatorAccount::create(['name'=>'Legacy Only','email'=>'only@safa.local','mobile'=>'01933333333','role'=>'staff','pin_hash'=>Hash::make('123456'),'is_activated'=>true,'permissions'=>[]]);
        $this->postJson('/api/auth/login', ['mobile'=>'01933333333','pin'=>'123456','device_uuid'=>'legacy-only-device','fingerprint_hash'=>'legacy-only-fingerprint'])
            ->assertStatus(401)->assertJsonPath('error.code', 'INVALID_CREDENTIALS');
    }
}
