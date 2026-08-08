<?php

namespace Tests\Feature;

use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;
use App\Models\Account;
use App\Models\SafaApiKey;
use App\Models\Customer;
use App\Models\Supplier;
use App\Models\Transaction;

class SyncRepairPhase1Test extends TestCase
{
    use RefreshDatabase;

    private string $apiKey = 'test-sync-key';
    private string $apiSecret = 'test-secret-value-for-hmac';

    protected function setUp(): void
    {
        parent::setUp();
        // Bypass HMAC middleware — sync contract logic is what we're testing here
        $this->withoutMiddleware(\App\Http\Middleware\CheckApiSecurityKey::class);
    }

    public function test_sync_up_returns_accepted_with_server_ids()
    {
        $account = Account::create(['name' => 'Test Account']);
        SafaApiKey::create([
            'account_id'  => $account->id,
            'api_key'     => $this->apiKey,
            'api_secret'  => $this->apiSecret,
            'client_name' => 'Test Client',
            'is_active'   => true,
        ]);

        $payload = [
            'customers' => [
                [
                    'local_id'  => 101,
                    'name'      => 'Rahim Uddin',
                    'phone'     => '01711000111',
                    'timestamp' => 1700000000,
                ]
            ],
            'suppliers' => [
                [
                    'local_id'  => 202,
                    'name'      => 'Dhaka Express',
                    'phone'     => '01811000222',
                    'timestamp' => 1700000000,
                ]
            ],
        ];

        $response = $this->withHeaders([
            'X-SAFA-API-KEY' => $this->apiKey,
        ])->postJson('/api/sync/up', $payload);

        $response->assertStatus(200)
                 ->assertJsonStructure([
                     'status',
                     'server_time',
                     'accepted' => [
                         'customers',
                         'suppliers',
                     ],
                     'rejected'
                 ]);

        $json = $response->json();
        $this->assertEquals('success', $json['status']);
        $this->assertCount(1, $json['accepted']['customers']);
        $this->assertEquals(101, $json['accepted']['customers'][0]['local_id']);
        $this->assertGreaterThan(0, $json['accepted']['customers'][0]['server_id']);
        $this->assertCount(1, $json['accepted']['suppliers']);
        $this->assertEquals(202, $json['accepted']['suppliers'][0]['local_id']);
        $this->assertGreaterThan(0, $json['accepted']['suppliers'][0]['server_id']);
    }

    public function test_sync_up_resolves_foreign_keys_for_transactions()
    {
        $account = Account::create(['name' => 'Test Account']);
        SafaApiKey::create([
            'account_id'  => $account->id,
            'api_key'     => $this->apiKey,
            'api_secret'  => $this->apiSecret,
            'client_name' => 'Test Client',
            'is_active'   => true,
        ]);

        $payload = [
            'customers' => [
                [
                    'local_id'  => 10,
                    'name'      => 'Customer A',
                    'phone'     => '01700000001',
                    'timestamp' => 1700000000,
                ]
            ],
            'suppliers' => [
                [
                    'local_id'  => 20,
                    'name'      => 'Supplier B',
                    'phone'     => '01800000002',
                    'timestamp' => 1700000000,
                ]
            ],
            'transactions' => [
                [
                    'local_id'              => 30,
                    'customer_id'           => 10,   // ← local Room ID
                    'supplier_id'           => 20,   // ← local Room ID
                    'amount_sar'            => 500.0,
                    'customer_rate'         => 32.5,
                    'supplier_rate'         => 32.0,
                    'amount_bdt'            => 16250.0,
                    'receiver_name'         => 'Karim',
                    'receiver_phone'        => '01911000333',
                    'receiver_account_type' => 'Bkash',
                    'receiver_account_no'   => '01911000333',
                    'timestamp'             => 1700000100,
                ]
            ]
        ];

        $response = $this->withHeaders([
            'X-SAFA-API-KEY' => $this->apiKey,
        ])->postJson('/api/sync/up', $payload);

        $response->assertStatus(200);

        $json = $response->json();

        $serverCustId = $json['accepted']['customers'][0]['server_id'];
        $serverSuppId = $json['accepted']['suppliers'][0]['server_id'];
        $serverTxId   = $json['accepted']['transactions'][0]['server_id'];

        $this->assertGreaterThan(0, $serverTxId);

        // Verify FK resolution: transaction's customer_id and supplier_id should be server PKs
        $dbTx = Transaction::find($serverTxId);
        $this->assertNotNull($dbTx);
        $this->assertEquals($serverCustId, $dbTx->customer_id, 'Transaction customer_id should be resolved to server PK');
        $this->assertEquals($serverSuppId, $dbTx->supplier_id, 'Transaction supplier_id should be resolved to server PK');
    }

    public function test_sync_up_rejects_records_with_missing_fields()
    {
        $account = Account::create(['name' => 'Test Account']);
        SafaApiKey::create([
            'account_id'  => $account->id,
            'api_key'     => $this->apiKey,
            'api_secret'  => $this->apiSecret,
            'client_name' => 'Test Client',
            'is_active'   => true,
        ]);

        $payload = [
            'customers' => [
                [
                    'local_id' => 999,
                    // Missing 'name' → should be rejected
                ]
            ],
        ];

        $response = $this->withHeaders([
            'X-SAFA-API-KEY' => $this->apiKey,
        ])->postJson('/api/sync/up', $payload);

        $response->assertStatus(200);
        $json = $response->json();

        $this->assertNotEmpty($json['rejected']);
        $this->assertEquals('customers', $json['rejected'][0]['entity']);
        $this->assertEquals(999, $json['rejected'][0]['local_id']);
    }
}
