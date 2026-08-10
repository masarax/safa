<?php

namespace Tests\Feature;

use Tests\TestCase;
use App\Models\User;
use App\Models\AuthSession;
use App\Models\DeviceBinding;
use App\Models\SafaApiKey;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use App\Http\Controllers\AuthJWTController;

class UserManagementApiTest extends TestCase
{
    use RefreshDatabase;

    private function createSuperAdmin(): User
    {
        return User::create([
            'name' => 'Super Admin',
            'email' => 'superadmin@safa.local',
            'mobile' => '01700000000',
            'role' => 'superadmin',
            'pin_hash' => Hash::make('123456'),
            'password' => Hash::make('123456'),
            'is_activated' => true,
            'permissions' => User::defaultPermissions(true),
        ]);
    }

    private function createStaff(): User
    {
        return User::create([
            'name' => 'Staff User',
            'email' => 'staff@safa.local',
            'mobile' => '01711111111',
            'role' => 'staff',
            'pin_hash' => Hash::make('123456'),
            'password' => Hash::make('123456'),
            'is_activated' => true,
            'permissions' => User::defaultPermissions(false),
        ]);
    }

    private function getAuthHeaders(User $user, string $method = 'GET', string $path = 'api/customers', string $body = ''): array
    {
        $deviceUuid = 'TEST_DEVICE_' . $user->id;
        $fingerprint = 'TEST_FINGERPRINT_' . $user->id;
        $sessionToken = 'TEST_SESSION_' . bin2hex(random_bytes(8));
        $refreshToken = 'TEST_REFRESH_' . bin2hex(random_bytes(8));
        $payload = [
            'iss' => 'safa-backend',
            'sub' => $user->id,
            'user_id' => $user->id,
            'device_uuid' => $deviceUuid,
            'session_token' => $sessionToken,
            'iat' => time(),
            'exp' => time() + 3600,
        ];
        $token = AuthJWTController::generateJwt($payload);

        DeviceBinding::updateOrCreate(
            ['user_id' => $user->id, 'device_uuid' => $deviceUuid],
            [
                'device_model' => 'Test Device',
                'fingerprint_hash' => $fingerprint,
                'is_active' => true,
                'bound_at' => now(),
            ]
        );
        AuthSession::create([
            'user_id' => $user->id,
            'device_uuid' => $deviceUuid,
            'access_token' => $token,
            'refresh_token' => $refreshToken,
            'session_token' => $sessionToken,
            'expires_at' => now()->addHour(),
            'is_revoked' => false,
        ]);

        $apiKey = 'safa_testing_key';
        $apiSecret = 'safa_testing_secret';
        $apiAccount = \App\Models\Account::firstOrCreate(['name' => 'SAFA Account']);
        SafaApiKey::updateOrCreate(
            ['client_name' => 'SAFA Mobile Client'],
            ['account_id' => $apiAccount->id, 'api_key' => $apiKey, 'api_secret' => $apiSecret, 'is_active' => true]
        );

        $timestamp = (string) time();
        $nonce = 'nonce_' . bin2hex(random_bytes(16));
        $sigPayload = strtoupper($method) . '/' . ltrim($path, '/') . $timestamp . $nonce . $body;
        $signature = hash_hmac('sha256', $sigPayload, $apiSecret);

        return [
            'Authorization' => 'Bearer ' . $token,
            'X-SAFA-API-KEY' => $apiKey,
            'X-SAFA-SIGNATURE' => $signature,
            'X-SAFA-TIMESTAMP' => $timestamp,
            'X-SAFA-NONCE' => $nonce,
            'X-SAFA-REFRESH-TOKEN' => $refreshToken,
            'X-SAFA-DEVICE-TOKEN' => $deviceUuid,
            'X-SAFA-SESSION-TOKEN' => $sessionToken,
            'X-SAFA-FINGERPRINT-TOKEN' => $fingerprint,
            'Accept' => 'application/json',
        ];
    }

    public function test_superadmin_can_list_create_update_delete_operator()
    {
        $superAdmin = $this->createSuperAdmin();
        $createData = [
            'name' => 'New Operator',
            'mobile' => '01722222222',
            'email' => 'op@safa.local',
            'role' => 'staff',
            'pin' => '654321',
            'permissions' => ['can_view_customers' => true, 'can_delete_customers' => false],
        ];
        $body = json_encode($createData);
        $resCreate = $this->withHeaders($this->getAuthHeaders($superAdmin, 'POST', 'api/auth/operators', $body))->postJson('/api/auth/operators', $createData);
        $resCreate->assertStatus(201);
        $opId = $resCreate->json('operator.id');
        $this->assertDatabaseHas('users', ['mobile' => '01722222222', 'role' => 'staff']);

        $resList = $this->withHeaders($this->getAuthHeaders($superAdmin))->getJson('/api/auth/operators');
        $resList->assertStatus(200);

        $updateData = ['name' => 'Updated Operator', 'is_activated' => false];
        $body = json_encode($updateData);
        $resUpdate = $this->withHeaders($this->getAuthHeaders($superAdmin, 'PUT', "api/auth/operators/{$opId}", $body))->putJson("/api/auth/operators/{$opId}", $updateData);
        $resUpdate->assertStatus(200);
        $this->assertDatabaseHas('users', ['id' => $opId, 'name' => 'Updated Operator', 'is_activated' => 0]);

        $resDel = $this->withHeaders($this->getAuthHeaders($superAdmin, 'DELETE', "api/auth/operators/{$opId}"))->deleteJson("/api/auth/operators/{$opId}");
        $resDel->assertStatus(200);
        $this->assertDatabaseMissing('users', ['id' => $opId]);
    }

    public function test_unauthorized_staff_cannot_manage_operators()
    {
        $staff = $this->createStaff();
        $resGet = $this->withHeaders($this->getAuthHeaders($staff))->getJson('/api/auth/operators');
        $resGet->assertStatus(403);

        $data = ['name' => 'Hacker', 'mobile' => '01799999999', 'role' => 'staff', 'pin' => '123456'];
        $resPost = $this->withHeaders($this->getAuthHeaders($staff, 'POST', 'api/auth/operators', json_encode($data)))->postJson('/api/auth/operators', $data);
        $resPost->assertStatus(403);
    }

    public function test_superadmin_cannot_delete_themselves()
    {
        $superAdmin = $this->createSuperAdmin();
        $resDel = $this->withHeaders($this->getAuthHeaders($superAdmin, 'DELETE', "api/auth/operators/{$superAdmin->id}"))->deleteJson("/api/auth/operators/{$superAdmin->id}");
        $resDel->assertStatus(400);
        $this->assertDatabaseHas('users', ['id' => $superAdmin->id]);
    }

    public function test_deleted_or_deactivated_user_cannot_access_apis()
    {
        $staff = $this->createStaff();
        $headers1 = $this->getAuthHeaders($staff, 'GET', 'api/customers');
        $resCust = $this->withHeaders($headers1)->get('/api/customers');
        $resCust->assertStatus(200);

        $staff->is_activated = false;
        $staff->save();
        $headers2 = $this->getAuthHeaders($staff, 'GET', 'api/customers');
        $resCustBlocked = $this->withHeaders($headers2)->get('/api/customers');
        $resCustBlocked->assertStatus(401);

        $staff->delete();
        $headers3 = $this->getAuthHeaders($staff, 'GET', 'api/customers');
        $resCustDeleted = $this->withHeaders($headers3)->get('/api/customers');
        $resCustDeleted->assertStatus(401);
    }
}
