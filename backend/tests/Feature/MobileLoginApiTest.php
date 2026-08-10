<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\SafaApiKey;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class MobileLoginApiTest extends TestCase
{
    use RefreshDatabase;

    public function test_valid_mobile_and_pin_returns_tokens(): void
    {
        $user = User::create([
            'name' => 'Test Admin',
            'email' => 'test-admin@safa.local',
            'mobile' => '0536308965',
            'pin_hash' => Hash::make('123456'),
            'password' => Hash::make('123456'),
            'role' => 'superadmin',
            'is_activated' => true,
            'permissions' => User::defaultPermissions(true),
        ]);

        $account = Account::create(['name' => 'SAFA Account', 'owner_user_id' => $user->id, 'balance' => 0]);
        $apiKey = 'safa_testing_key';
        $apiSecret = 'safa_testing_secret';
        SafaApiKey::create([
            'account_id' => $account->id,
            'client_name' => 'SAFA Mobile Client',
            'api_key' => $apiKey,
            'api_secret' => $apiSecret,
            'is_active' => true,
        ]);

        $payload = ['mobile' => '0536308965', 'pin' => '123456'];
        $body = json_encode($payload, JSON_UNESCAPED_SLASHES);
        $timestamp = (string) time();
        $nonce = 'login_test_' . bin2hex(random_bytes(12));
        $signature = hash_hmac('sha256', 'POST' . '/api/auth/login' . $timestamp . $nonce . $body, $apiSecret);

        $response = $this->withHeaders([
            'X-SAFA-API-KEY' => $apiKey,
            'X-SAFA-SIGNATURE' => $signature,
            'X-SAFA-TIMESTAMP' => $timestamp,
            'X-SAFA-NONCE' => $nonce,
            'Accept' => 'application/json',
        ])->withBody($body, 'application/json')->post('/api/auth/login');

        $response->assertStatus(200)
            ->assertJsonPath('status', 'success')
            ->assertJsonPath('user.id', $user->id)
            ->assertJsonPath('user.mobile', '0536308965');

        $this->assertNotEmpty($response->json('access_token'));
        $this->assertDatabaseHas('auth_sessions', ['user_id' => $user->id, 'is_revoked' => 0]);
        $this->assertDatabaseHas('device_bindings', ['user_id' => $user->id, 'is_active' => 1]);
    }

    public function test_invalid_pin_is_rejected_without_creating_a_session(): void
    {
        $user = User::create([
            'name' => 'Test Admin',
            'email' => 'test-admin@safa.local',
            'mobile' => '0536308965',
            'pin_hash' => Hash::make('123456'),
            'password' => Hash::make('123456'),
            'role' => 'superadmin',
            'is_activated' => true,
            'permissions' => User::defaultPermissions(true),
        ]);

        $account = Account::create(['name' => 'SAFA Account', 'owner_user_id' => $user->id, 'balance' => 0]);
        $apiKey = 'safa_testing_key';
        $apiSecret = 'safa_testing_secret';
        SafaApiKey::create([
            'account_id' => $account->id,
            'client_name' => 'SAFA Mobile Client',
            'api_key' => $apiKey,
            'api_secret' => $apiSecret,
            'is_active' => true,
        ]);

        $payload = ['mobile' => '0536308965', 'pin' => '999999'];
        $body = json_encode($payload, JSON_UNESCAPED_SLASHES);
        $timestamp = (string) time();
        $nonce = 'login_test_' . bin2hex(random_bytes(12));
        $signature = hash_hmac('sha256', 'POST' . '/api/auth/login' . $timestamp . $nonce . $body, $apiSecret);

        $this->withHeaders([
            'X-SAFA-API-KEY' => $apiKey,
            'X-SAFA-SIGNATURE' => $signature,
            'X-SAFA-TIMESTAMP' => $timestamp,
            'X-SAFA-NONCE' => $nonce,
            'Accept' => 'application/json',
        ])->withBody($body, 'application/json')->post('/api/auth/login')->assertStatus(401);

        $this->assertDatabaseMissing('auth_sessions', ['user_id' => $user->id]);
    }
}
