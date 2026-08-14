<?php

namespace Tests\Feature;

use App\Http\Controllers\AuthJWTController;
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
    private function context(): array { $user = User::create(['name' => 'Auth Test User', 'email' => 'auth-test@safa.local', 'mobile' => '01700000999', 'role' => 'staff', 'pin_hash' => Hash::make('123456'), 'password' => Hash::make('123456'), 'is_activated' => true, 'permissions' => User::defaultPermissions(false)]); SafaApiKey::create(['client_name' => 'Auth Test Client', 'api_key' => $this->apiKey, 'api_secret' => 'test-secret', 'is_active' => true]); $device = 'AUTH_TEST_DEVICE'; $fingerprint = 'AUTH_TEST_FINGERPRINT'; $refresh = 'AUTH_TEST_REFRESH'; $sessionToken = 'AUTH_TEST_SESSION'; $access = AuthJWTController::generateJwt(['sub' => $user->id, 'user_id' => $user->id, 'device_uuid' => $device, 'iat' => time(), 'exp' => time() + 3600]); DeviceBinding::create(['user_id' => $user->id, 'device_uuid' => $device, 'device_model' => 'Test Device', 'fingerprint_hash' => $fingerprint, 'is_active' => true, 'bound_at' => now()]); AuthSession::create(['user_id' => $user->id, 'device_uuid' => $device, 'access_token' => $access, 'refresh_token' => $refresh, 'session_token' => $sessionToken, 'expires_at' => now()->addDays(30), 'is_revoked' => false]); return compact('user', 'device', 'fingerprint', 'refresh', 'sessionToken', 'access'); }
    private function headers(array $ctx): array { return ['Accept' => 'application/json', 'X-SAFA-API-KEY' => $this->apiKey, 'X-SAFA-CLIENT' => 'android', 'X-SAFA-DEVICE-TOKEN' => $ctx['device'], 'X-SAFA-FINGERPRINT-TOKEN' => $ctx['fingerprint'], 'X-SAFA-REFRESH-TOKEN' => $ctx['refresh'], 'X-SAFA-SESSION-TOKEN' => $ctx['sessionToken'], 'Authorization' => 'Bearer ' . $ctx['access']]; }
    public function test_refresh_rotates_refresh_token_atomically(): void { $ctx = $this->context(); $response = $this->withHeaders($this->headers($ctx))->postJson('/api/auth/refresh', ['refresh_token' => $ctx['refresh'], 'device_token' => $ctx['device'], 'fingerprint_token' => $ctx['fingerprint']]); $response->assertOk()->assertJsonPath('status', 'success'); $newRefresh = $response->json('tokens.refresh_token'); $this->assertNotSame($ctx['refresh'], $newRefresh); $this->assertDatabaseMissing('auth_sessions', ['refresh_token_hash' => hash('sha256', $ctx['refresh'])]); $this->assertDatabaseHas('auth_sessions', ['refresh_token_hash' => hash('sha256', $newRefresh), 'is_revoked' => 0]); $this->withHeaders($this->headers($ctx))->postJson('/api/auth/refresh', ['refresh_token' => $ctx['refresh'], 'device_token' => $ctx['device'], 'fingerprint_token' => $ctx['fingerprint']])->assertStatus(401); }
    public function test_refresh_rejects_wrong_fingerprint(): void { $ctx = $this->context(); $this->withHeaders($this->headers($ctx))->postJson('/api/auth/refresh', ['refresh_token' => $ctx['refresh'], 'device_token' => $ctx['device'], 'fingerprint_token' => 'WRONG_FINGERPRINT'])->assertStatus(401); $this->assertDatabaseHas('auth_sessions', ['refresh_token_hash' => hash('sha256', $ctx['refresh']), 'is_revoked' => 0]); }
    public function test_logout_revokes_current_session(): void { $ctx = $this->context(); $this->withHeaders($this->headers($ctx))->postJson('/api/auth/logout')->assertOk()->assertJsonPath('status', 'success'); $this->assertDatabaseHas('auth_sessions', ['access_token_hash' => hash('sha256', $ctx['access']), 'refresh_token_hash' => hash('sha256', $ctx['refresh']), 'is_revoked' => 1]); }
    public function test_logout_is_idempotent_for_an_already_revoked_session(): void { $ctx = $this->context(); $headers = $this->headers($ctx); $this->withHeaders($headers)->postJson('/api/auth/logout')->assertOk()->assertJsonPath('status', 'success'); $this->assertDatabaseHas('auth_sessions', ['access_token_hash' => hash('sha256', $ctx['access']), 'is_revoked' => 1]); $this->withHeaders($headers)->getJson('/api/auth/operators')->assertStatus(401); }

    public function test_authenticated_pin_change_updates_credentials_and_revokes_other_sessions(): void
    {
        $ctx = $this->context();
        $other = AuthSession::create([
            'user_id' => $ctx['user']->id,
            'device_uuid' => 'OTHER_DEVICE',
            'access_token' => 'OTHER_ACCESS',
            'refresh_token' => 'OTHER_REFRESH',
            'session_token' => 'OTHER_SESSION',
            'expires_at' => now()->addDays(30),
            'is_revoked' => false,
        ]);

        $this->withHeaders($this->headers($ctx))
            ->postJson('/api/auth/change-pin', ['current_pin' => '123456', 'new_pin' => '654321'])
            ->assertOk()
            ->assertJsonPath('status', 'success');

        $user = $ctx['user']->fresh();
        $this->assertTrue(Hash::check('654321', $user->pin_hash));
        $this->assertTrue(Hash::check('654321', $user->password));
        $this->assertFalse(Hash::check('123456', $user->pin_hash));
        $this->assertDatabaseHas('auth_sessions', [
            'access_token_hash' => hash('sha256', $ctx['access']),
            'is_revoked' => 0,
        ]);
        $this->assertTrue((bool) $other->fresh()->is_revoked);
    }

    public function test_pin_change_rejects_wrong_current_pin_and_reused_or_malformed_pin(): void
    {
        $ctx = $this->context();
        $headers = $this->headers($ctx);

        $this->withHeaders($headers)
            ->postJson('/api/auth/change-pin', ['current_pin' => '000000', 'new_pin' => '654321'])
            ->assertStatus(401);
        $this->withHeaders($headers)
            ->postJson('/api/auth/change-pin', ['current_pin' => '123456', 'new_pin' => '123456'])
            ->assertStatus(422);
        $this->withHeaders($headers)
            ->postJson('/api/auth/change-pin', ['current_pin' => '123456', 'new_pin' => '12345x'])
            ->assertStatus(422);

        $this->assertTrue(Hash::check('123456', $ctx['user']->fresh()->pin_hash));
    }
}
