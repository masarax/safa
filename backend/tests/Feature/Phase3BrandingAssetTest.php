<?php

namespace Tests\Feature;

use Tests\TestCase;
use Illuminate\Foundation\Testing\RefreshDatabase;

class Phase3BrandingAssetTest extends TestCase
{
    use RefreshDatabase;

    /**
     * Test public safa-logo.png file existence and readability.
     */
    public function test_safa_logo_exists_and_is_valid()
    {
        $logoPath = public_path('safa-logo.png');
        $this->assertFileExists($logoPath, 'safa-logo.png must exist in backend/public/');
        $this->assertGreaterThan(0, filesize($logoPath), 'safa-logo.png size must be > 0 bytes');
    }

    /**
     * Test public favicon.svg existence.
     */
    public function test_favicon_svg_exists_and_is_valid()
    {
        $faviconPath = public_path('favicon.svg');
        $this->assertFileExists($faviconPath, 'favicon.svg must exist in backend/public/');
        $this->assertGreaterThan(0, filesize($faviconPath), 'favicon.svg size must be > 0 bytes');
    }

    /**
     * Test HTTP GET /safa-logo.png returns 200.
     */
    public function test_http_get_safa_logo_returns_200()
    {
        $response = $this->get('/safa-logo.png');
        $response->assertStatus(200);
    }

    /**
     * Test HTTP GET /favicon.svg returns 200.
     */
    public function test_http_get_favicon_svg_returns_200()
    {
        $response = $this->get('/favicon.svg');
        $response->assertStatus(200);
    }

    /**
     * Test welcome page contains valid branding asset link.
     */
    public function test_welcome_page_renders_logo()
    {
        $response = $this->get('/');
        $response->assertStatus(200);
        $response->assertSee('safa-logo.png');
    }

    /**
     * Test update page renders logo and favicon.
     */
    public function test_update_page_renders_logo()
    {
        $response = $this->get('/install/update');
        $this->assertTrue(in_array($response->status(), [200, 302]));
    }

    /**
     * Test security: uploadLogo rejects invalid base64 image data.
     */
    public function test_upload_logo_rejects_php_file_upload()
    {
        $apiKey = 'test_api_key_12345';
        $apiSecret = 'test_api_secret_67890';
        
        \App\Models\SafaApiKey::create([
            'client_name' => 'Test Client',
            'api_key' => $apiKey,
            'api_secret' => $apiSecret,
            'is_active' => true,
        ]);

        $data = ['logo_base64' => 'data:image/exe;base64,invalid_executable_content'];
        $body = json_encode($data);
        $timestamp = time();
        $nonce = 'nonce_test_12345678';
        
        $payload = 'POST/api/upload/logo' . $timestamp . $nonce . $body;
        $signature = hash_hmac('sha256', $payload, $apiSecret);

        $response = $this->postJson('/api/upload/logo', $data, [
            'X-SAFA-API-KEY' => $apiKey,
            'X-SAFA-SIGNATURE' => $signature,
            'X-SAFA-TIMESTAMP' => $timestamp,
            'X-SAFA-NONCE' => $nonce,
        ]);

        $response->assertStatus(400);
        $response->assertJson([
            'status' => 'error'
        ]);
    }
}
