<?php

namespace Tests\Feature;

use App\Models\OperatorAccount;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class Phase32AuthIdentitySafetyTest extends TestCase
{
    use RefreshDatabase;

    public function test_duplicate_legacy_mobile_is_rejected_instead_of_guessing(): void
    {
        OperatorAccount::create([
            'name' => 'Legacy One',
            'email' => 'one@safa.local',
            'mobile' => '01900000001',
            'role' => 'staff',
            'pin_hash' => Hash::make('123456'),
            'is_activated' => true,
            'permissions' => [],
        ]);
        OperatorAccount::create([
            'name' => 'Legacy Two',
            'email' => 'two@safa.local',
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
        ])->assertStatus(409)
            ->assertJsonPath('message', 'Multiple accounts match this mobile number. Please contact an administrator.');
    }
}
