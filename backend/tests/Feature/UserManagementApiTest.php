<?php

namespace Tests\Feature;

use Tests\TestCase;
use App\Models\User;
use App\Models\AuthSession;
use App\Models\DeviceBinding;
use App\Models\UserAccountShare;
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
        $payload = [
            'iss' => 'safa-backend',
            'sub' => $user->id,
            'user_id' => $user->id,
            'device_uuid' => 'TEST_DEVICE_' . $user->id,
            'session_token' => 'TEST_SESSION_' . $user->id,
            'iat' => time(),
            'exp' => time() + 3600,
        ];
        $token = AuthJWTController::generateJwt($payload);

        $apiKey = 'safa_key_7f8a9e0b1c2d3e4f5a6b7c8d9e0f1a2b';
        $apiSecret = 'safa_sec_9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c8d7e6f5a4b3c2d1e0f9a8b';
        $timestamp = (string) time();
        $nonce = 'nonce_' . str_pad((string) rand(10000, 99999), 10, '0', STR_PAD_LEFT);
        $sigPayload = strtoupper($method) . '/' . ltrim($path, '/') . $timestamp . $nonce . $body;
        $signature = hash_hmac('sha256', $sigPayload, $apiSecret);

        return [
            'Authorization' => 'Bearer ' . $token,
            'X-SAFA-ACCESS-TOKEN' => $token,
            'X-SAFA-API-KEY' => $apiKey,
            'X-SAFA-SIGNATURE' => $signature,
            'X-SAFA-TIMESTAMP' => $timestamp,
            'X-SAFA-NONCE' => $nonce,
            'Accept' => 'application/json',
        ];
    }

    public function test_superadmin_can_list_create_update_delete_operator()
    {
        $superAdmin = $this->createSuperAdmin();

        // 1. Create operator
        $createData = [
            'name' => 'New Operator',
            'mobile' => '01722222222',
            'email' => 'op@safa.local',
            'role' => 'staff',
            'pin' => '654321',
            'permissions' => ['can_view_customers' => true, 'can_delete_customers' => false]
        ];
        $resCreate = $this->withHeaders($this->getAuthHeaders($superAdmin))->postJson('/api/auth/operators', $createData);
        $resCreate->assertStatus(201);
        $resCreate->assertJsonPath('status', 'success');
        $opId = $resCreate->json('operator.id');

        $this->assertDatabaseHas('users', ['mobile' => '01722222222', 'role' => 'staff']);

        // 2. List operators
        $resList = $this->withHeaders($this->getAuthHeaders($superAdmin))->getJson('/api/auth/operators');
        $resList->assertStatus(200);
        $resList->assertJsonPath('status', 'success');

        // 3. Update operator
        $updateData = ['name' => 'Updated Operator', 'is_activated' => false];
        $resUpdate = $this->withHeaders($this->getAuthHeaders($superAdmin))->putJson("/api/auth/operators/{$opId}", $updateData);
        $resUpdate->assertStatus(200);
        $this->assertDatabaseHas('users', ['id' => $opId, 'name' => 'Updated Operator', 'is_activated' => 0]);

        // 4. Delete operator
        $resDel = $this->withHeaders($this->getAuthHeaders($superAdmin))->deleteJson("/api/auth/operators/{$opId}");
        $resDel->assertStatus(200);
        $this->assertDatabaseMissing('users', ['id' => $opId]);
    }

    public function test_unauthorized_staff_cannot_manage_operators()
    {
        $staff = $this->createStaff();

        $resGet = $this->withHeaders($this->getAuthHeaders($staff))->getJson('/api/auth/operators');
        $resGet->assertStatus(403);

        $resPost = $this->withHeaders($this->getAuthHeaders($staff))->postJson('/api/auth/operators', [
            'name' => 'Hacker', 'mobile' => '01799999999', 'role' => 'staff', 'pin' => '123456'
        ]);
        $resPost->assertStatus(403);
    }

    public function test_superadmin_cannot_delete_themselves()
    {
        $superAdmin = $this->createSuperAdmin();

        $resDel = $this->withHeaders($this->getAuthHeaders($superAdmin))->deleteJson("/api/auth/operators/{$superAdmin->id}");
        $resDel->assertStatus(400);
        $this->assertDatabaseHas('users', ['id' => $superAdmin->id]);
    }

    public function test_deleted_or_deactivated_user_cannot_access_apis()
    {
        $staff = $this->createStaff();

        // Access works when active
        $headers1 = $this->getAuthHeaders($staff, 'GET', 'api/customers');
        $resCust = $this->withHeaders($headers1)->get('/api/customers');
        $resCust->assertStatus(200);

        // Deactivate user
        $staff->is_activated = false;
        $staff->save();

        $headers2 = $this->getAuthHeaders($staff, 'GET', 'api/customers');
        $resCustBlocked = $this->withHeaders($headers2)->get('/api/customers');
        $resCustBlocked->assertStatus(401);

        // Delete user
        $staff->delete();

        $headers3 = $this->getAuthHeaders($staff, 'GET', 'api/customers');
        $resCustDeleted = $this->withHeaders($headers3)->get('/api/customers');
        $resCustDeleted->assertStatus(401);
    }
}
