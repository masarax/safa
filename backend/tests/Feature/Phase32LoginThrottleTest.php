<?php

namespace Tests\Feature;

use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class Phase32LoginThrottleTest extends TestCase
{
    use RefreshDatabase;

    public function test_login_throttle_returns_429_after_the_configured_limit(): void
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
        ])->assertStatus(429);
    }
}
