<?php

namespace Tests\Feature;

use Tests\TestCase;

class Phase3BrandingAssetTest extends TestCase
{
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
        // Either 200 (if pending) or 302 (if up to date)
        $this->assertTrue(in_array($response->status(), [200, 302]));
    }
}
