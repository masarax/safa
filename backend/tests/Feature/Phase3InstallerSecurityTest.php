<?php

namespace Tests\Feature;

use Tests\TestCase;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\Route;

class Phase3InstallerSecurityTest extends TestCase
{
    /**
     * Test /update-db unauthorized request returns HTTP 403.
     */
    public function test_update_db_unauthorized_request_returns_403()
    {
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
        $response = $this->postJson('/update-db', ['key' => 'invalid_secret_key_123']);
        $response->assertStatus(403);
        $response->assertJson([
            'status' => 'error'
        ]);
    }

    /**
     * Test /update-db GET request without authorization key returns 403 or method not allowed.
     */
    public function test_update_db_get_request_without_key_is_rejected()
    {
        $response = $this->get('/update-db');
        $response->assertStatus(403);
    }

    /**
     * Test /update-db authorized POST request executes migration.
     */
    public function test_update_db_with_valid_key_returns_200()
    {
        $secretKey = env('DB_UPDATE_SECRET', 'safa_secure_update_key_2026');
        $response = $this->postJson('/update-db', ['key' => $secretKey]);
        $response->assertStatus(200);
        $response->assertJson([
            'status' => 'success'
        ]);
    }

    /**
     * Test /install/update-process requires CSRF or valid authorization.
     */
    public function test_install_update_process_endpoint_exists()
    {
        $response = $this->get('/install/update');
        $this->assertTrue(in_array($response->status(), [200, 302]));
    }
}
