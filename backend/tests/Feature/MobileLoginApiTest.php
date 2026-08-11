<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\AuthSession;
use App\Models\DeviceBinding;
use App\Models\SafaApiKey;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class MobileLoginApiTest extends TestCase
{
    use RefreshDatabase;

    private function authenticateHeaders(string $apiKey, string $apiSecret, array $payload): array
    {
        $body = json_encode($payload, JSON_UNESCAPED_SLASHES);
        $timestamp = (string) time();
        $nonce = 'login_test_' . bin2hex(random_bytes(12));
        $signature = hash_hmac('sha256', 'POST' . '/api/auth/login' . $timestamp . $nonce . $body, $apiSecret);

        return [
            'X-SAFA-API-KEY' => $apiKey,
            'X-SAFA-SIGNATURE' => $signature,
            'X-SAFA-TIMESTAMP' => $timestamp,
            'X-SAFA-NONCE' => $nonce,
            'Accept' => 'application/json',
        ];
    }

    private function seedUser(): array
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

        return [$user, $apiKey, $apiSecret];
    }

    public function test_valid_mobile_and_pin_returns_tokens(): void
    {
        [$user, $apiKey, $apiSecret] = $this->seedUser();
        $payload = ['mobile' => '0536308965', 'pin' => '123456', 'device_uuid' => 'device-a', 'fingerprint_hash' => 'fingerprint-a'];
        $body = json_encode($payload, JSON_UNESCAPED_SLASHES);

        $response = $this->withHeaders($this->authenticateHeaders($apiKey, $apiSecret, $payload))
            ->withBody($body, 'application/json')
            ->post('/api/auth/login');

        $response->assertStatus(200)
            ->assertJsonPath('status', 'success')
            ->assertJsonPath('user.id', $user->id)
            ->assertJsonPath('user.mobile', '0536308965');

        $this->assertNotEmpty($response->json('access_token'));
        $this->assertDatabaseHas('auth_sessions', ['user_id' => $user->id, 'is_revoked' => 0]);
        $this->assertDatabaseHas('device_bindings', ['user_id' => $user->id, 'device_uuid' => 'device-a', 'is_active' => 1]);
    }

    public function test_invalid_pin_is_rejected_without_creating_a_session(): void
    {
        [$user, $apiKey, $apiSecret] = $this->seedUser();
        $payload = ['mobile' => '0536308965', 'pin' => '999999'];

        $this->withHeaders($this->authenticateHeaders($apiKey, $apiSecret, $payload))
            ->withBody(json_encode($payload, JSON_UNESCAPED_SLASHES), 'application/json')
            ->post('/api/auth/login')
            ->assertStatus(401)
            ->assertJsonPath('message', 'Mobile number or PIN is incorrect.');

        $this->assertDatabaseMissing('auth_sessions', ['user_id' => $user->id]);
    }

    public function test_repeated_login_on_same_device_revokes_the_previous_session(): void
    {
        [$user, $apiKey, $apiSecret] = $this->seedUser();
        $payload = ['mobile' => '0536308965', 'pin' => '123456', 'device_uuid' => 'device-a', 'fingerprint_hash' => 'fingerprint-a'];

        $first = $this->withHeaders($this->authenticateHeaders($apiKey, $apiSecret, $payload))
            ->withBody(json_encode($payload, JSON_UNESCAPED_SLASHES), 'application/json')
            ->post('/api/auth/login')
            ->assertStatus(200);

        $firstAccessToken = $first->json('access_token');

        $second = $this->withHeaders($this->authenticateHeaders($apiKey, $apiSecret, $payload))
            ->withBody(json_encode($payload, JSON_UNESCAPED_SLASHES), 'application/json')
            ->post('/api/auth/login')
            ->assertStatus(200);

        $this->assertDatabaseHas('auth_sessions', [
            'user_id' => $user->id,
            'access_token' => $firstAccessToken,
            'is_revoked' => 1,
        ]);
        $this->assertDatabaseHas('auth_sessions', [
            'user_id' => $user->id,
            'access_token' => $second->json('access_token'),
            'is_revoked' => 0,
        ]);
    }

    public function test_known_device_cannot_silently_replace_its_fingerprint(): void
    {
        [$user, $apiKey, $apiSecret] = $this->seedUser();
        $firstPayload = ['mobile' => '0536308965', 'pin' => '123456', 'device_uuid' => 'device-a', 'fingerprint_hash' => 'fingerprint-a'];

        $this->withHeaders($this->authenticateHeaders($apiKey, $apiSecret, $firstPayload))
            ->withBody(json_encode($firstPayload, JSON_UNESCAPED_SLASHES), 'application/json')
            ->post('/api/auth/login')
            ->assertStatus(200);

        $secondPayload = $firstPayload + ['fingerprint_hash' => 'fingerprint-b'];
        $this->withHeaders($this->authenticateHeaders($apiKey, $apiSecret, $secondPayload))
            ->withBody(json_encode($secondPayload, JSON_UNESCAPED_SLASHES), 'application/json')
            ->post('/api/auth/login')
            ->assertStatus(403)
            ->assertJsonPath('message', 'Device security identity changed. Re-bind this device before continuing.');

        $this->assertDatabaseHas('device_bindings', [
            'user_id' => $user->id,
            'device_uuid' => 'device-a',
            'fingerprint_hash' => 'fingerprint-a',
        ]);
    }
}
