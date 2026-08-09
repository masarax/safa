<?php

namespace Tests\Feature;

use Tests\TestCase;
use App\Models\User;
use App\Models\Account;
use App\Models\Customer;
use App\Models\Supplier;
use App\Models\Transaction;
use App\Models\UserAccountShare;
use Illuminate\Foundation\Testing\RefreshDatabase;

class ServerFirstDataTest extends TestCase
{
    use RefreshDatabase;

    private string $apiKey = 'safa_key_7f8a9e0b1c2d3e4f5a6b7c8d9e0f1a2b';
    private string $apiSecret = 'safa_sec_9a8b7c6d5e4f3a2b1c0d9e8f7a6b5c4d3e2f1a0b9c8d7e6f5a4b3c2d1e0f9a8b';

    private function getHeaders(string $method, string $path, string $body = ''): array
    {
        $timestamp = (string) time();
        $nonce = 'nonce_' . str_pad((string) rand(10000, 99999), 10, '0', STR_PAD_LEFT);
        $payload = strtoupper($method) . '/' . ltrim($path, '/') . $timestamp . $nonce . $body;
        $signature = hash_hmac('sha256', $payload, $this->apiSecret);

        return [
            'X-SAFA-API-KEY' => $this->apiKey,
            'X-SAFA-SIGNATURE' => $signature,
            'X-SAFA-TIMESTAMP' => $timestamp,
            'X-SAFA-NONCE' => $nonce,
            'Accept' => 'application/json',
        ];
    }

    public function test_customer_crud_direct_rest_api()
    {
        $account = Account::create(['name' => 'Test Account 1', 'balance' => 1000]);

        // 1. Create Customer via POST
        $createBody = json_encode(['name' => 'Karim Chowdhury', 'phone' => '01711223344', 'local_id' => 101]);
        $headers = $this->getHeaders('POST', 'api/customers', $createBody);
        $res = $this->postJson('/api/customers', json_decode($createBody, true), $headers);
        $res->assertStatus(201);
        $res->assertJsonPath('status', 'success');

        $this->assertDatabaseHas('customers', [
            'name' => 'Karim Chowdhury',
            'phone' => '01711223344'
        ]);

        // 2. Fetch Customers via GET
        $headers = $this->getHeaders('GET', 'api/customers');
        $resGet = $this->withHeaders($headers)->get('/api/customers');
        $resGet->assertStatus(200);
        $resGet->assertJsonCount(1, 'customers');

        // 3. Delete Customer via DELETE
        $cust = Customer::where('name', 'Karim Chowdhury')->first();
        $headers = $this->getHeaders('DELETE', "api/customers/{$cust->id}");
        $resDel = $this->withHeaders($headers)->delete("/api/customers/{$cust->id}");
        $resDel->assertStatus(200);

        $this->assertDatabaseMissing('customers', [
            'id' => $cust->id,
            'deleted_at' => null
        ]);
    }

    public function test_supplier_crud_direct_rest_api()
    {
        $account = Account::create(['name' => 'Test Account 2', 'balance' => 2000]);

        // 1. Create Supplier via POST
        $createBody = json_encode(['name' => 'Dhaka Exchange Ltd', 'phone' => '01899887766', 'local_id' => 202]);
        $headers = $this->getHeaders('POST', 'api/suppliers', $createBody);
        $res = $this->postJson('/api/suppliers', json_decode($createBody, true), $headers);
        $res->assertStatus(201);

        $this->assertDatabaseHas('suppliers', [
            'name' => 'Dhaka Exchange Ltd'
        ]);

        // 2. Delete Supplier via DELETE
        $supp = Supplier::where('name', 'Dhaka Exchange Ltd')->first();
        $headers = $this->getHeaders('DELETE', "api/suppliers/{$supp->id}");
        $resDel = $this->withHeaders($headers)->delete("/api/suppliers/{$supp->id}");
        $resDel->assertStatus(200);
    }

    public function test_transaction_crud_direct_rest_api()
    {
        $account = Account::create(['name' => 'Test Account 3', 'balance' => 3000]);

        // 1. Create Transaction via POST
        $createBody = json_encode([
            'type' => 'Deliver',
            'amount_sar' => 500.00,
            'customer_rate' => 32.50,
            'amount_bdt' => 16250.00,
            'receiver_name' => 'Rahim',
            'local_id' => 303
        ]);
        $headers = $this->getHeaders('POST', 'api/transactions', $createBody);
        $res = $this->postJson('/api/transactions', json_decode($createBody, true), $headers);
        $res->assertStatus(201);

        $this->assertDatabaseHas('transactions', [
            'amount_sar' => 500.00,
            'amount_bdt' => 16250.00
        ]);
    }
}
