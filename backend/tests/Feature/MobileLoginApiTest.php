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

    private function authenticateHeaders(string $apiKey, string $apiSecret, array $payload, string $method = 'POST', string $path = '/api/auth/login'): array
    {
        $body = json_encode($payload, JSON_UNESCAPED_SLASHES); $timestamp = (string) time(); $nonce = 'login_test_' . bin2hex(random_bytes(12));
        return ['X-SAFA-API-KEY' => $apiKey, 'X-SAFA-SIGNATURE' => hash_hmac('sha256', $method . $path . $timestamp . $nonce . $body, $apiSecret), 'X-SAFA-TIMESTAMP' => $timestamp, 'X-SAFA-NONCE' => $nonce, 'Accept' => 'application/json'];
    }

    private function seedUser(): array
    {
        $user = User::create(['name' => 'Test Admin', 'email' => 'test-admin@safa.local', 'mobile' => '0536308965', 'pin_hash' => Hash::make('123456'), 'password' => Hash::make('123456'), 'role' => 'superadmin', 'is_activated' => true, 'permissions' => User::defaultPermissions(true)]);
        $account = Account::create(['name' => 'SAFA Account', 'owner_user_id' => $user->id, 'balance' => 0]); $apiKey = 'safa_testing_key'; $apiSecret = 'safa_testing_secret';
        SafaApiKey::create(['account_id' => $account->id, 'client_name' => 'SAFA Mobile Client', 'api_key' => $apiKey, 'api_secret' => $apiSecret, 'is_active' => true]);
        return [$user, $apiKey, $apiSecret];
    }

    private function login(string $apiKey, string $apiSecret, array $payload)
    {
        return $this->withHeaders($this->authenticateHeaders($apiKey, $apiSecret, $payload))->postJson('/api/auth/login', $payload);
    }

    public function test_valid_mobile_and_pin_returns_tokens(): void
    {
        [$user, $apiKey, $apiSecret] = $this->seedUser(); $payload = ['mobile' => '0536308965', 'pin' => '123456', 'device_uuid' => 'device-a', 'fingerprint_hash' => 'fingerprint-a'];
        $response = $this->login($apiKey, $apiSecret, $payload);
        $response->assertStatus(200)->assertJsonPath('status', 'success')->assertJsonPath('user.id', $user->id)->assertJsonPath('user.mobile', '0536308965');
        $this->assertNotEmpty($response->json('access_token')); $this->assertDatabaseHas('auth_sessions', ['user_id' => $user->id, 'is_revoked' => 0]); $this->assertDatabaseHas('device_bindings', ['user_id' => $user->id, 'device_uuid' => 'device-a', 'is_active' => 1]);
    }

    public function test_authenticated_session_endpoint_accepts_the_current_access_token(): void
    {
        [$user, $apiKey, $apiSecret] = $this->seedUser(); $payload = ['mobile' => '0536308965', 'pin' => '123456', 'device_uuid' => 'device-a', 'fingerprint_hash' => 'fingerprint-a'];
        $login = $this->login($apiKey, $apiSecret, $payload)->assertStatus(200); $accessToken = $login->json('access_token');
        $headers = $this->authenticateHeaders($apiKey, $apiSecret, [], 'GET', '/api/auth/session'); $headers['Authorization'] = 'Bearer ' . $accessToken; $headers['X-SAFA-DEVICE-TOKEN'] = 'device-a';
        $this->withHeaders($headers)->get('/api/auth/session')->assertStatus(200)->assertJsonPath('status', 'success')->assertJsonPath('user.id', $user->id)->assertJsonPath('user.mobile', '0536308965');
    }

    public function test_invalid_pin_is_rejected_without_creating_a_session(): void
    {
        [$user, $apiKey, $apiSecret] = $this->seedUser(); $payload = ['mobile' => '0536308965', 'pin' => '999999'];
        $this->login($apiKey, $apiSecret, $payload)->assertStatus(401)->assertJsonPath('message', 'Mobile number or PIN is incorrect.'); $this->assertDatabaseMissing('auth_sessions', ['user_id' => $user->id]);
    }

    public function test_repeated_login_on_same_device_revokes_the_previous_session(): void
    {
        [$user, $apiKey, $apiSecret] = $this->seedUser(); $payload = ['mobile' => '0536308965', 'pin' => '123456', 'device_uuid' => 'device-a', 'fingerprint_hash' => 'fingerprint-a'];
        $first = $this->login($apiKey, $apiSecret, $payload)->assertStatus(200); $firstAccessToken = $first->json('access_token'); $second = $this->login($apiKey, $apiSecret, $payload)->assertStatus(200);
        $this->assertDatabaseHas('auth_sessions', ['user_id' => $user->id, 'access_token_hash' => hash('sha256', $firstAccessToken), 'is_revoked' => 1]); $this->assertDatabaseHas('auth_sessions', ['user_id' => $user->id, 'access_token_hash' => hash('sha256', $second->json('access_token')), 'is_revoked' => 0]);
    }

    public function test_known_device_rebinds_fingerprint_after_explicit_pin_login(): void
    {
        [$user, $apiKey, $apiSecret] = $this->seedUser(); $first = ['mobile' => '0536308965', 'pin' => '123456', 'device_uuid' => 'device-a', 'fingerprint_hash' => 'fingerprint-a'];
        $this->login($apiKey, $apiSecret, $first)->assertStatus(200); $second = array_merge($first, ['fingerprint_hash' => 'fingerprint-b']); $this->login($apiKey, $apiSecret, $second)->assertStatus(200);
        $this->assertDatabaseHas('device_bindings', ['user_id' => $user->id, 'device_uuid' => 'device-a', 'fingerprint_hash' => 'fingerprint-b']);
    }
}
