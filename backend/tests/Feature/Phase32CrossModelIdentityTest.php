<?php

namespace Tests\Feature;

use App\Models\OperatorAccount;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class Phase32CrossModelIdentityTest extends TestCase
{
    use RefreshDatabase;

    public function test_current_user_and_unlinked_legacy_operator_with_same_mobile_are_rejected(): void
    {
        User::create([
            'name' => 'Canonical User',
            'email' => 'canonical@safa.local',
            'mobile' => '01912345678',
            'pin_hash' => Hash::make('123456'),
            'password' => Hash::make('123456'),
            'role' => 'staff',
            'is_activated' => true,
            'permissions' => User::defaultPermissions(false),
        ]);

        OperatorAccount::create([
            'name' => 'Legacy Shadow',
            'email' => 'legacy-shadow@safa.local',
            'mobile' => '01912345678',
            'role' => 'staff',
            'pin_hash' => Hash::make('123456'),
            'is_activated' => true,
            'permissions' => [],
        ]);

        $this->postJson('/api/auth/login', [
            'mobile' => '01912345678',
            'pin' => '123456',
            'device_uuid' => 'cross-model-device',
            'fingerprint_hash' => 'cross-model-fingerprint',
        ])->assertStatus(409)
            ->assertJsonPath('message', 'Multiple accounts match this mobile number. Please contact an administrator.');
    }

    public function test_linked_legacy_operator_is_one_identity_and_can_authenticate(): void
    {
        $user = User::create([
            'name' => 'Linked User',
            'email' => 'linked@safa.local',
            'mobile' => '01987654321',
            'pin_hash' => null,
            'password' => null,
            'role' => 'staff',
            'is_activated' => true,
            'permissions' => User::defaultPermissions(false),
        ]);

        OperatorAccount::create([
            'user_id' => $user->id,
            'name' => 'Linked Legacy',
            'email' => 'linked@safa.local',
            'mobile' => '01987654321',
            'role' => 'staff',
            'pin_hash' => Hash::make('123456'),
            'is_activated' => true,
            'permissions' => [],
        ]);

        $this->postJson('/api/auth/login', [
            'mobile' => '01987654321',
            'pin' => '123456',
            'device_uuid' => 'linked-device',
            'fingerprint_hash' => 'linked-fingerprint',
        ])->assertOk()
            ->assertJsonPath('user.id', $user->id);
    }
}
