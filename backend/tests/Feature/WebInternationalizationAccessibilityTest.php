<?php

namespace Tests\Feature;

use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class WebInternationalizationAccessibilityTest extends TestCase
{
    use RefreshDatabase;

    public function test_english_and_bangla_catalogs_have_identical_keysets(): void
    {
        $en = require base_path('lang/en/web.php');
        $bn = require base_path('lang/bn/web.php');

        $this->assertSame($this->flattenKeys($en), $this->flattenKeys($bn));
        $this->assertNotEmpty($en['js']);
    }

    public function test_core_web_views_do_not_embed_language_ternaries(): void
    {
        foreach (['safa/app.blade.php', 'safa/login.blade.php'] as $view) {
            $source = file_get_contents(resource_path('views/' . $view));
            $this->assertIsString($source);
            $this->assertStringNotContainsString('$bn ?', $source, $view);
            $this->assertStringNotContainsString('$language === \'bn\' ?', $source, $view);
        }

        $product = file_get_contents(public_path('safa-web-product.js'));
        $this->assertIsString($product);
        $this->assertStringNotContainsString('const bn =', $product);
        $this->assertStringNotContainsString('const text =', $product);
        $this->assertStringContainsString('app.dataset.webCopy', $product);
    }

    public function test_login_renders_from_selected_locale_catalog(): void
    {
        $this->get('/login?lang=en')
            ->assertOk()
            ->assertSee('Language selection')
            ->assertSee('Mobile number or email')
            ->assertSee('Sign in');

        $this->get('/login?lang=bn')
            ->assertOk()
            ->assertSee('ভাষা নির্বাচন')
            ->assertSee('মোবাইল নম্বর অথবা ইমেইল')
            ->assertSee('লগইন করুন');
    }

    public function test_core_workspace_has_dialog_and_live_region_accessibility_contracts(): void
    {
        $source = file_get_contents(resource_path('views/safa/app.blade.php'));
        $this->assertIsString($source);
        $this->assertStringContainsString('role="dialog"', $source);
        $this->assertStringContainsString('aria-modal="true"', $source);
        $this->assertStringContainsString('aria-live="polite"', $source);
        $this->assertStringContainsString('aria-atomic="true"', $source);
        $this->assertStringContainsString('aria-label="{{ __(\'web.close\') }}"', $source);
    }

    private function flattenKeys(array $catalog, string $prefix = ''): array
    {
        $keys = [];
        foreach ($catalog as $key => $value) {
            $path = $prefix === '' ? (string) $key : $prefix . '.' . $key;
            if (is_array($value)) {
                $keys = array_merge($keys, $this->flattenKeys($value, $path));
            } else {
                $keys[] = $path;
            }
        }
        sort($keys);
        return $keys;
    }
}
