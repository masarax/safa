<?php

namespace Tests\Feature;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class Phase32LoginThrottleTest extends TestCase
{
    use RefreshDatabase;

    public function test_login_throttle_returns_429_after_the_configured_failure_limit(): void
    {
        for ($attempt = 1; $attempt <= 5; $attempt++) {
            $this->postJson('/api/auth/login', [
                'mobile' => '0536308965',
                'pin' => '000000',
                'device_uuid' => 'throttle-device',
                'fingerprint_hash' => 'throttle-fingerprint',
            ])->assertStatus(401);
        }

        $this->postJson('/api/auth/login', [
            'mobile' => '0536308965',
            'pin' => '000000',
            'device_uuid' => 'throttle-device',
            'fingerprint_hash' => 'throttle-fingerprint',
        ])->assertStatus(429)->assertJsonPath('error.code', 'LOGIN_THROTTLED');
    }

    public function test_successful_login_clears_prior_failures_and_does_not_consume_the_failure_bucket(): void
    {
        User::create([
            'name' => 'Throttle Reset User',
            'email' => 'throttle-reset@safa.local',
            'mobile' => '0536308966',
            'pin_hash' => Hash::make('123456'),
            'password' => Hash::make('123456'),
            'role' => 'staff',
            'is_activated' => true,
            'permissions' => User::defaultPermissions(false),
        ]);

        for ($attempt = 1; $attempt <= 4; $attempt++) {
            $this->postJson('/api/auth/login', [
                'mobile' => '0536308966',
                'pin' => '654321',
                'device_uuid' => 'reset-device',
                'fingerprint_hash' => 'reset-fingerprint',
            ])->assertStatus(401);
        }

        $validPayload = [
            'mobile' => '0536308966',
            'pin' => '123456',
            'device_uuid' => 'reset-device',
            'fingerprint_hash' => 'reset-fingerprint',
        ];

        $this->postJson('/api/auth/login', $validPayload)
            ->assertOk()
            ->assertJsonPath('status', 'success');

        // A fresh valid sign-in on the same device models the user's immediate
        // logout -> login cycle. Successful authentication must not accumulate
        // against the failed-PIN limiter.
        $this->postJson('/api/auth/login', $validPayload)
            ->assertOk()
            ->assertJsonPath('status', 'success');
    }

    public function test_canonical_and_versioned_login_aliases_share_the_same_failure_bucket(): void
    {
        $payload = [
            'mobile' => '0536308970',
            'pin' => '000000',
            'device_uuid' => 'alias-throttle-device',
            'fingerprint_hash' => 'alias-throttle-fingerprint',
        ];

        for ($attempt = 1; $attempt <= 3; $attempt++) {
            $this->postJson('/api/auth/login', $payload)->assertStatus(401);
        }
        for ($attempt = 1; $attempt <= 2; $attempt++) {
            $this->postJson('/api/v1/auth/login', $payload)->assertStatus(401);
        }

        $this->postJson('/api/auth/login', $payload)
            ->assertStatus(429)
            ->assertJsonPath('error.code', 'LOGIN_THROTTLED');
    }
}
