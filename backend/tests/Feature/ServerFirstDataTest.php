<?php

namespace Tests\Feature;

use Tests\TestCase;
use App\Models\User;
use App\Models\Account;
use App\Models\Customer;
use App\Models\Supplier;
use App\Models\Transaction;
use App\Models\SafaApiKey;
use App\Models\AuthSession;
use App\Models\DeviceBinding;
use App\Http\Controllers\AuthJWTController;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Hash;

class ServerFirstDataTest extends TestCase
{
    use RefreshDatabase;

    private string $apiKey = 'safa_testing_key';
    private string $apiSecret = 'safa_testing_secret';

    private function createAuthenticatedContext(): array
    {
        $user = User::create([
            'name' => 'API Test User',
            'email' => 'api-test@safa.local',
            'mobile' => '01700000111',
            'role' => 'superadmin',
            'pin_hash' => Hash::make('123456'),
            'password' => Hash::make('123456'),
            'is_activated' => true,
            'permissions' => User::defaultPermissions(true),
        ]);

        $account = Account::create([
            'name' => 'API Test Account',
            'balance' => 1000,
            'owner_user_id' => $user->id,
        ]);

        SafaApiKey::updateOrCreate(
            ['client_name' => 'SAFA Mobile Client'],
            [
                'account_id' => $account->id,
                'api_key' => $this->apiKey,
                'api_secret' => $this->apiSecret,
                'is_active' => true,
            ]
        );

        $deviceUuid = 'TEST_DEVICE_' . $user->id;
        $fingerprint = 'TEST_FINGERPRINT_' . $user->id;
        $sessionToken = 'TEST_SESSION_' . $user->id;
        $refreshToken = 'TEST_REFRESH_' . $user->id;
        $jwt = AuthJWTController::generateJwt([
            'iss' => 'safa-backend',
            'sub' => $user->id,
            'user_id' => $user->id,
            'account_id' => $account->id,
            'owner_user_id' => $user->id,
            'device_uuid' => $deviceUuid,
            'session_token' => $sessionToken,
            'iat' => time(),
            'exp' => time() + 3600,
        ]);

        DeviceBinding::create([
            'user_id' => $user->id,
            'device_uuid' => $deviceUuid,
            'device_model' => 'Test Device',
            'fingerprint_hash' => $fingerprint,
            'is_active' => true,
            'bound_at' => now(),
        ]);

        AuthSession::create([
            'user_id' => $user->id,
            'device_uuid' => $deviceUuid,
            'access_token' => $jwt,
            'refresh_token' => $refreshToken,
            'session_token' => $sessionToken,
            'expires_at' => now()->addHour(),
            'is_revoked' => false,
        ]);

        return compact('user', 'account', 'jwt', 'refreshToken', 'deviceUuid', 'fingerprint', 'sessionToken');
    }

    private function getHeaders(array $context, string $method, string $path, string $body = ''): array
    {
        $timestamp = (string) time();
        $nonce = 'nonce_' . bin2hex(random_bytes(16));
        $payload = strtoupper($method) . '/' . ltrim($path, '/') . $timestamp . $nonce . $body;
        $signature = hash_hmac('sha256', $payload, $this->apiSecret);

        return [
            'Authorization' => 'Bearer ' . $context['jwt'],
            'X-SAFA-API-KEY' => $this->apiKey,
            'X-SAFA-SIGNATURE' => $signature,
            'X-SAFA-TIMESTAMP' => $timestamp,
            'X-SAFA-NONCE' => $nonce,
            'X-SAFA-REFRESH-TOKEN' => $context['refreshToken'],
            'X-SAFA-DEVICE-TOKEN' => $context['deviceUuid'],
            'X-SAFA-SESSION-TOKEN' => $context['sessionToken'],
            'X-SAFA-FINGERPRINT-TOKEN' => $context['fingerprint'],
            'X-SAFA-ACCOUNT-ID' => (string) $context['account']->id,
            'Accept' => 'application/json',
        ];
    }

    public function test_customer_crud_direct_rest_api()
    {
        $context = $this->createAuthenticatedContext();
        $createBody = json_encode(['name' => 'Karim Chowdhury', 'phone' => '01711223344', 'local_id' => 101]);
        $headers = $this->getHeaders($context, 'POST', 'api/customers', $createBody);
        $res = $this->withHeaders($headers)->postJson('/api/customers', json_decode($createBody, true));
        $res->assertStatus(201);
        $res->assertJsonPath('status', 'success');

        $this->assertDatabaseHas('customers', ['name' => 'Karim Chowdhury', 'phone' => '01711223344']);

        $headers = $this->getHeaders($context, 'GET', 'api/customers');
        $resGet = $this->withHeaders($headers)->get('/api/customers');
        $resGet->assertStatus(200);
        $resGet->assertJsonCount(1, 'customers');

        $cust = Customer::where('name', 'Karim Chowdhury')->firstOrFail();
        $headers = $this->getHeaders($context, 'DELETE', "api/customers/{$cust->id}");
        $resDel = $this->withHeaders($headers)->delete("/api/customers/{$cust->id}?confirmed=1");
        $resDel->assertStatus(200);
    }

    public function test_supplier_crud_direct_rest_api()
    {
        $context = $this->createAuthenticatedContext();
        $createBody = json_encode(['name' => 'Dhaka Exchange Ltd', 'phone' => '01899887766', 'local_id' => 202]);
        $headers = $this->getHeaders($context, 'POST', 'api/suppliers', $createBody);
        $res = $this->withHeaders($headers)->postJson('/api/suppliers', json_decode($createBody, true));
        $res->assertStatus(201);
        $this->assertDatabaseHas('suppliers', ['name' => 'Dhaka Exchange Ltd']);

        $supp = Supplier::where('name', 'Dhaka Exchange Ltd')->firstOrFail();
        $headers = $this->getHeaders($context, 'DELETE', "api/suppliers/{$supp->id}");
        $resDel = $this->withHeaders($headers)->delete("/api/suppliers/{$supp->id}?confirmed=1");
        $resDel->assertStatus(200);
    }

    public function test_transaction_crud_direct_rest_api()
    {
        $context = $this->createAuthenticatedContext();
        $createBody = json_encode([
            'type' => 'Deliver',
            'amount_sar' => 500.00,
            'customer_rate' => 32.50,
            'amount_bdt' => 16250.00,
            'receiver_name' => 'Rahim',
            'local_id' => 303,
        ]);
        $headers = $this->getHeaders($context, 'POST', 'api/transactions', $createBody);
        $res = $this->withHeaders($headers)->postJson('/api/transactions', json_decode($createBody, true));
        $res->assertStatus(201);
        $this->assertDatabaseHas('transactions', ['amount_sar' => 500.00, 'amount_bdt' => 16250.00]);
    }
}
