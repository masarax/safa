<?php

namespace Tests\Feature;

use Tests\TestCase;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\Route;

class Phase3InstallerSecurityTest extends TestCase
{
    use RefreshDatabase;

    /**
     * Test /update-db unauthorized POST request returns HTTP 403.
     */
    public function test_update_db_unauthorized_request_returns_403()
    {
        config(['app.env' => 'production']);
        putenv('DB_UPDATE_SECRET=my_secure_prod_key');
        $_ENV['DB_UPDATE_SECRET'] = 'my_secure_prod_key';

        $response = $this->postJson('/update-db');
        $response->assertStatus(403);
        $response->assertJson([
            'status' => 'error'
        ]);
    }

    /**
     * Test /update-db with incorrect key returns HTTP 403.
     */
    public function test_update_db_with_wrong_key_returns_403()
    {
        putenv('DB_UPDATE_SECRET=my_secure_prod_key');
        $_ENV['DB_UPDATE_SECRET'] = 'my_secure_prod_key';

        $response = $this->postJson('/update-db', ['key' => 'invalid_secret_key_123']);
        $response->assertStatus(403);
        $response->assertJson([
            'status' => 'error'
        ]);
    }

    /**
     * Test /update-db GET request is rejected with 405 Method Not Allowed.
     */
    public function test_update_db_get_request_is_rejected()
    {
        $response = $this->get('/update-db');
        $response->assertStatus(405);
    }

    /**
     * Test /update-db fail closed when DB_UPDATE_SECRET environment variable is missing.
     */
    public function test_update_db_fails_closed_when_secret_not_configured()
    {
        putenv('DB_UPDATE_SECRET=');
        $_ENV['DB_UPDATE_SECRET'] = '';

        $response = $this->postJson('/update-db', ['key' => 'any_key']);
        $response->assertStatus(403);
        $response->assertJson([
            'status' => 'error'
        ]);
    }

    /**
     * Test /update-db authorized POST request executes migration successfully.
     */
    public function test_update_db_with_valid_key_returns_200()
    {
        $secretKey = 'test_secret_key_2026';
        putenv("DB_UPDATE_SECRET={$secretKey}");
        $_ENV['DB_UPDATE_SECRET'] = $secretKey;

        $response = $this->postJson('/update-db', ['key' => $secretKey]);
        $response->assertStatus(200);
        $response->assertJson([
            'status' => 'success'
        ]);
    }

    /**
     * Test /install/update-process rejects unauthorized POST with 403.
     */
    public function test_install_update_process_unauthorized_post_returns_403()
    {
        putenv('DB_UPDATE_SECRET=test_secret_key_2026');
        $_ENV['DB_UPDATE_SECRET'] = 'test_secret_key_2026';

        $response = $this->post('/install/update-process');
        $response->assertStatus(403);
    }

    /**
     * Test session spoofing: session user_id alone without valid token or admin fails with 403.
     */
    public function test_install_update_process_session_spoofing_rejected_with_403()
    {
        putenv('DB_UPDATE_SECRET=test_secret_key_2026');
        $_ENV['DB_UPDATE_SECRET'] = 'test_secret_key_2026';

        $response = $this->withSession(['user_id' => 9999])->post('/install/update-process');
        $response->assertStatus(403);
    }

    /**
     * Test valid single-use update token authorizes /install/update-process execution.
     */
    public function test_install_update_process_with_valid_update_token_succeeds()
    {
        $token = 'valid_session_update_token_12345';
        $response = $this->withSession(['safa_update_token' => $token])
            ->post('/install/update-process', ['update_token' => $token]);
            
        $this->assertTrue(in_array($response->status(), [200, 302]));
    }

    /**
     * Test single-use token replay is rejected on second attempt.
     */
    public function test_install_update_process_single_use_token_replay_rejected_with_403()
    {
        putenv('DB_UPDATE_SECRET=test_secret_key_2026');
        $_ENV['DB_UPDATE_SECRET'] = 'test_secret_key_2026';

        $token = 'single_use_token_998877';
        $firstResponse = $this->withSession(['safa_update_token' => $token])
            ->post('/install/update-process', ['update_token' => $token]);
        $this->assertTrue(in_array($firstResponse->status(), [200, 302]));

        // Second request reusing the same token without active session token returns 403
        $secondResponse = $this->post('/install/update-process', ['update_token' => $token]);
        $secondResponse->assertStatus(403);
    }

    /**
     * Test /install/update-process accepts valid secret key.
     */
    public function test_install_update_process_authorized_post_succeeds()
    {
        $secretKey = 'test_secret_key_2026';
        putenv("DB_UPDATE_SECRET={$secretKey}");
        $_ENV['DB_UPDATE_SECRET'] = $secretKey;

        $response = $this->post('/install/update-process', ['key' => $secretKey]);
        $this->assertTrue(in_array($response->status(), [200, 302]));
    }

    /**
     * Test syncUp clamps client timestamp spoofing (e.g. far-future timestamp).
     */
    public function test_sync_up_clamps_future_timestamp_spoofing()
    {
        $this->withoutMiddleware(\App\Http\Middleware\CheckApiSecurityKey::class);
        Artisan::call('migrate', ['--force' => true]);

        $account = \App\Models\Account::create(['name' => 'Test Account']);
        $apiKey = 'test_api_key_sync_123';
        $apiSecret = 'test_api_secret_sync_456';
        
        \App\Models\SafaApiKey::create([
            'account_id' => $account->id,
            'client_name' => 'Test Client',
            'api_key' => $apiKey,
            'api_secret' => $apiSecret,
            'is_active' => true,
        ]);

        $futureTimestamp = 4102444800; // Jan 1, 2100
        $data = [
            'customers' => [
                [
                    'local_id' => 9999,
                    'name' => 'Spoofed Timestamp Customer',
                    'timestamp' => $futureTimestamp,
                ]
            ]
        ];

        $response = $this->withHeaders(['X-SAFA-API-KEY' => $apiKey])->postJson('/api/sync/up', $data);
        $response->assertStatus(200);
        
        $customer = \App\Models\Customer::where('local_id', 9999)->first();
        $this->assertNotNull($customer);
        $this->assertLessThan($futureTimestamp, (int)$customer->timestamp, 'Future timestamp must be clamped to safe epoch range.');
    }
}
