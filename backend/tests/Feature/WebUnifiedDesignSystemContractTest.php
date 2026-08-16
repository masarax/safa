<?php

namespace Tests\Feature;

use Tests\TestCase;

class WebUnifiedDesignSystemContractTest extends TestCase
{
    public function test_web_uses_canonical_android_brand_and_component_metrics(): void
    {
        $css = (string) file_get_contents(public_path('safa-web-unified.css'));

        foreach ([
            '--brand-green:#064e3b',
            '--brand-green-dark:#043a2c',
            '--brand-green-light:#0b6b52',
            '--brand-green-container:#ddf5ed',
            '--brand-orange:#f97316',
            '--brand-orange-dark:#ea580c',
            '--brand-orange-light:#ff8a3d',
            '--brand-orange-container:#ffe9d8',
            '--bg:#f7faf8',
            '--surface-2:#eef4f1',
            '--text:#10231c',
            '--muted:#5f7069',
            '--line:#dce7e2',
            '--line-strong:#b9ccc4',
            '--primary:#f97316',
            '--secondary:#064e3b',
            '--field-h:52px',
            '--button-h:48px',
            '--radius-sm:10px',
            '--radius:14px',
            '--radius-lg:18px',
            '--radius-xl:24px',
        ] as $token) {
            $this->assertStringContainsString($token, $css);
        }

        $this->assertStringContainsString('.primary-button,.button.primary', $css);
        $this->assertStringContainsString('background:var(--primary)', $css);
        $this->assertStringContainsString('.surface-card,.entity-card,.metric-card', $css);
        $this->assertStringContainsString('.bottom-nav-item.active', $css);
        $this->assertStringContainsString('box-shadow:inset 0 -2px 0 var(--primary)', $css);
        $this->assertStringContainsString('prefers-reduced-motion:reduce', $css);
    }

    public function test_login_uses_android_aligned_shared_auth_components(): void
    {
        $login = (string) file_get_contents(resource_path('views/safa/login.blade.php'));
        $css = (string) file_get_contents(public_path('safa-web-unified.css'));

        $this->assertStringContainsString('class="auth-shell"', $login);
        $this->assertStringContainsString('class="auth-logo-wrap"', $login);
        $this->assertStringContainsString('class="language-switch"', $login);
        $this->assertStringContainsString('class="button primary wide"', $login);
        $this->assertStringContainsString('name="identity"', $login);
        $this->assertStringContainsString('name="credential"', $login);
        $this->assertStringContainsString('@csrf', $login);

        $this->assertStringContainsString('.auth-layout', $css);
        $this->assertStringContainsString('.auth-logo-wrap{width:88px;height:88px', $css);
        $this->assertStringContainsString('.auth-card', $css);
        $this->assertStringContainsString('.language-switch', $css);
    }

    public function test_system_update_surface_uses_the_same_product_stylesheet(): void
    {
        $view = (string) file_get_contents(resource_path('views/system_update.blade.php'));

        $this->assertStringContainsString("url('/safa-web.css')", $view);
        $this->assertStringContainsString('class="system-page"', $view);
        $this->assertStringContainsString('class="system-card"', $view);
        $this->assertStringContainsString('class="primary-button wide"', $view);
        $this->assertStringNotContainsString('<style>', $view);
    }
}
