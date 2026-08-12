<?php

namespace Tests\Feature;

use App\Models\Account;
use App\Models\DeviceBinding;
use App\Models\SafaApiKey;
use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;
use Tests\TestCase;

class MobileLoginRebindTest extends TestCase
{
    use RefreshDatabase;

    public function test_pin_login_rebinds_changed_fingerprint_on_same_device_and_reissues_session(): void
    {
        $user = User::create([
            'name' => 'Test User',
            'email' => 'login@safa.test',
            'mobile' => '0536308965',
            'pin_hash' => Hash::make('123456'),
            'password' => Hash::make('123456'),
            'role' => 'superadmin',
            'permissions' => User::defaultPermissions(true),
            'is_activated' => true,
        ]);

        $account = Account::create(['name' => 'SAFA Account', 'owner_user_id' => $user->id, 'balance' => 0]);
        SafaApiKey::create([
            'account_id' => $account->id,
            'client_name' => 'SAFA Mobile Client',
            'api_key' => 'safa_testing_key',
            'api_secret' => 'safa_testing_secret',
            'is_active' => true,
        ]);

        DeviceBinding::create([
            'user_id' => $user->id,
            'device_uuid' => 'DEVICE_TEST_1',
            'device_model' => 'Test Device',
            'fingerprint_hash' => 'OLD_FP',
            'is_active' => true,
            'bound_at' => now(),
        ]);

        $payload = ['mobile' => '0536308965', 'pin' => '123456'];
        $response = $this->withHeaders([
            'X-SAFA-API-KEY' => 'safa_testing_key',
            'X-SAFA-DEVICE-TOKEN' => 'DEVICE_TEST_1',
            'X-SAFA-FINGERPRINT-TOKEN' => 'NEW_FP',
            'X-SAFA-CLIENT' => 'android',
            'Accept' => 'application/json',
        ])->postJson('/api/auth/login', $payload);

        $response->assertOk()->assertJsonPath('status', 'success');
        $this->assertDatabaseHas('device_bindings', [
            'user_id' => $user->id,
            'device_uuid' => 'DEVICE_TEST_1',
            'fingerprint_hash' => 'NEW_FP',
            'is_active' => 1,
        ]);
    }
}
