<?php

namespace Tests\Feature;

use App\Models\OperatorAccount;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class Phase32AuthIdentitySafetyTest extends TestCase
{
    use RefreshDatabase;

    public function test_duplicate_legacy_mobile_is_rejected_without_revealing_ambiguity(): void
    {
        User::create([
            'name' => 'Canonical User',
            'email' => 'canonical@safa.local',
            'mobile' => '01900000001',
            'pin_hash' => Hash::make('123456'),
            'password' => Hash::make('123456'),
            'role' => 'staff',
            'is_activated' => true,
            'permissions' => User::defaultPermissions(false),
        ]);

        OperatorAccount::create([
            'name' => 'Legacy Operator',
            'email' => 'legacy@safa.local',
            'mobile' => '01900000001',
            'role' => 'staff',
            'pin_hash' => Hash::make('123456'),
            'is_activated' => true,
            'permissions' => [],
        ]);

        $this->postJson('/api/auth/login', [
            'mobile' => '01900000001',
            'pin' => '123456',
            'device_uuid' => 'ambiguous-device',
            'fingerprint_hash' => 'ambiguous-fingerprint',
        ])->assertStatus(401)
            ->assertJsonPath('error.code', 'INVALID_CREDENTIALS')
            ->assertJsonPath('message', 'Mobile number or PIN is incorrect.');
    }
}
