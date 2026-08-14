<?php

namespace Tests\Feature;

use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class Phase3BrandingAssetTest extends TestCase
{
    use RefreshDatabase;

    public function test_branding_assets_exist_and_are_non_empty(): void
    {
        foreach (['safa-logo.png', 'favicon.svg'] as $asset) {
            $path = public_path($asset); $this->assertFileExists($path); $this->assertGreaterThan(0, filesize($path));
        }
    }

    public function test_public_branding_assets_are_served(): void
    {
        $this->get('/safa-logo.png')->assertOk(); $this->get('/favicon.svg')->assertOk();
    }

    public function test_public_web_server_rules_do_not_deny_branding_assets(): void
    {
        $rules = (string) file_get_contents(public_path('.htaccess'));
        $this->assertStringNotContainsString('safa-logo', $rules);
        $this->assertStringNotContainsString('favicon', $rules);
    }

    public function test_generated_raster_logo_route_is_public_but_svg_is_not(): void
    {
        $directory = public_path('storage/logos');
        if (!is_dir($directory)) mkdir($directory, 0755, true);
        $path = $directory . '/logo_contract_test.png';
        file_put_contents($path, base64_decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=', true));

        try {
            $this->get('/storage/logos/logo_contract_test.png')
                ->assertOk()
                ->assertHeader('X-Content-Type-Options', 'nosniff');
            $this->get('/storage/logos/logo_contract_test.svg')->assertNotFound();
        } finally {
            @unlink($path);
        }
    }

    public function test_private_root_does_not_render_a_public_welcome_page(): void
    {
        $this->get('/')->assertNotFound();
    }

    public function test_public_installer_update_page_is_closed(): void
    {
        $this->get('/install/update')->assertNotFound();
    }

    public function test_logo_upload_requires_full_authenticated_api_context(): void
    {
        $this->postJson('/api/upload/logo', ['logo_base64' => 'data:image/png;base64,invalid'])
            ->assertUnauthorized();
    }
}
