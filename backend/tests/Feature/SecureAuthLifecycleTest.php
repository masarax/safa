<?php

namespace Tests\Feature;

use App\Models\AuthSession;
use App\Models\DeviceBinding;
use App\Models\SafaApiKey;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class SecureAuthLifecycleTest extends TestCase
{
    use RefreshDatabase;

    private string $apiKey = 'safa_auth_test_key';

    private function context(): array
    {
        $user = User::create([
            'name' => 'Auth Test User',
            'email' => 'auth-test@safa.local',
            'mobile' => '01700000999',
            'role' => 'staff',
            'pin_hash' => Hash::make('123456'),
            'password' => Hash::make('123456'),
            'is_activated' => true,
            'permissions' => User::defaultPermissions(false),
        ]);

        SafaApiKey::create([
            'client_name' => 'Auth Test Client',
            'api_key' => $this->apiKey,
            'api_secret' => 'test-secret',
            'is_active' => true,
        ]);

        $device = 'AUTH_TEST_DEVICE';
        $fingerprint = 'AUTH_TEST_FINGERPRINT';
        $refresh = 'AUTH_TEST_REFRESH';
        $sessionToken = 'AUTH_TEST_SESSION';
        $access = 'AUTH_TEST_ACCESS';

        DeviceBinding::create([
            'user_id' => $user->id,
            'device_uuid' => $device,
            'device_model' => 'Test Device',
            'fingerprint_hash' => $fingerprint,
            'is_active' => true,
            'bound_at' => now(),
        ]);

        AuthSession::create([
            'user_id' => $user->id,
            'device_uuid' => $device,
            'access_token' => $access,
            'refresh_token' => $refresh,
            'session_token' => $sessionToken,
            'expires_at' => now()->addDays(30),
            'is_revoked' => false,
        ]);

        return compact('user', 'device', 'fingerprint', 'refresh', 'sessionToken', 'access');
    }

    private function headers(array $ctx): array
    {
        return [
            'Accept' => 'application/json',
            'X-SAFA-API-KEY' => $this->apiKey,
            'X-SAFA-CLIENT' => 'android',
            'X-SAFA-DEVICE-TOKEN' => $ctx['device'],
            'X-SAFA-FINGERPRINT-TOKEN' => $ctx['fingerprint'],
            'X-SAFA-REFRESH-TOKEN' => $ctx['refresh'],
        ];
    }

    public function test_refresh_rotates_refresh_token_atomically(): void
    {
        $ctx = $this->context();

        $response = $this->withHeaders($this->headers($ctx))->postJson('/api/auth/refresh', [
            'refresh_token' => $ctx['refresh'],
            'device_token' => $ctx['device'],
            'fingerprint_token' => $ctx['fingerprint'],
        ]);

        $response->assertOk()->assertJsonPath('status', 'success');
        $newRefresh = $response->json('tokens.refresh_token');
        $this->assertNotSame($ctx['refresh'], $newRefresh);
        $this->assertDatabaseMissing('auth_sessions', ['refresh_token' => $ctx['refresh']]);
        $this->assertDatabaseHas('auth_sessions', ['refresh_token' => $newRefresh, 'is_revoked' => 0]);

        $oldToken = $this->withHeaders($this->headers($ctx))->postJson('/api/auth/refresh', [
            'refresh_token' => $ctx['refresh'],
            'device_token' => $ctx['device'],
            'fingerprint_token' => $ctx['fingerprint'],
        ]);
        $oldToken->assertStatus(401);
    }

    public function test_refresh_rejects_wrong_fingerprint(): void
    {
        $ctx = $this->context();
        $response = $this->withHeaders($this->headers($ctx))->postJson('/api/auth/refresh', [
            'refresh_token' => $ctx['refresh'],
            'device_token' => $ctx['device'],
            'fingerprint_token' => 'WRONG_FINGERPRINT',
        ]);

        $response->assertStatus(401);
        $this->assertDatabaseHas('auth_sessions', ['refresh_token' => $ctx['refresh'], 'is_revoked' => 0]);
    }

    public function test_logout_revokes_current_session(): void
    {
        $ctx = $this->context();
        $response = $this->withHeaders([
            ...$this->headers($ctx),
            'Authorization' => 'Bearer ' . $ctx['access'],
        ])->postJson('/api/auth/logout');

        $response->assertOk()->assertJsonPath('status', 'success');
        $this->assertDatabaseHas('auth_sessions', [
            'access_token' => $ctx['access'],
            'refresh_token' => $ctx['refresh'],
            'is_revoked' => 1,
        ]);
    }
}
