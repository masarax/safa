<?php

namespace Tests\Feature;

use Tests\TestCase;

class WebUnifiedDesignSystemContractTest extends TestCase
{
    public function test_web_product_system_matches_actual_android_visual_language(): void
    {
        $css = (string) file_get_contents(public_path('safa-web-product.css'));
        $entry = (string) file_get_contents(public_path('safa-web.css'));

        foreach ([
            '--brand-green:#064e3b',
            '--brand-orange:#f97316',
            '--chrome-gold:#d7a84b',
            '--chrome-ink:#3e2700',
            '--nav-active:#a82222',
            '--nav-active-soft:#ffebee',
            '--bg:#f7faf8',
            '--line:#e5e7eb',
            '--shortcut-red:#e53935',
            '--shortcut-orange:#fb8c00',
            '--shortcut-green:#43a047',
            '--shortcut-pink:#e91e63',
            '--shortcut-teal:#00796b',
            '--shortcut-indigo:#3f51b5',
            '--shortcut-brown:#8d6e63',
            '--touch:48px',
            '--button-h:48px',
        ] as $token) {
            $this->assertStringContainsString($token, $css);
        }

        $this->assertStringContainsString('--shortcut-yellow:#fbc02d', $entry);
        $this->assertStringContainsString('.shortcut-grid', $css);
        $this->assertStringContainsString('grid-template-columns:repeat(4,minmax(0,1fr))', $css);
        $this->assertStringContainsString('.bottom-nav-item.active', $css);
        $this->assertStringContainsString('background:var(--nav-active-soft)', $css);
        $this->assertStringContainsString('.icon-home', $css);
        $this->assertStringContainsString('mask:var(--icon)', $css);
        $this->assertStringContainsString('prefers-reduced-motion:reduce', $css);
    }

    public function test_login_matches_android_auth_composition_and_shared_controls(): void
    {
        $login = (string) file_get_contents(resource_path('views/safa/login.blade.php'));
        $css = (string) file_get_contents(public_path('safa-web-product.css'));

        $this->assertStringContainsString('class="auth-shell"', $login);
        $this->assertStringContainsString('class="auth-logo-wrap"', $login);
        $this->assertStringContainsString('class="language-switch"', $login);
        $this->assertStringContainsString('field-icon icon icon-phone', $login);
        $this->assertStringContainsString('field-icon icon icon-lock', $login);
        $this->assertStringContainsString('class="primary-button wide"', $login);
        $this->assertStringContainsString('name="identity"', $login);
        $this->assertStringContainsString('name="credential"', $login);
        $this->assertStringContainsString('@csrf', $login);

        $this->assertStringContainsString('.auth-logo-wrap{width:88px;height:88px', $css);
        $this->assertStringContainsString('.auth-card', $css);
        $this->assertStringContainsString('.field-control', $css);
    }

    public function test_dashboard_recreates_android_shortcuts_and_recent_ledger_presentation(): void
    {
        $view = (string) file_get_contents(resource_path('views/safa/app.blade.php'));
        $presentation = (string) file_get_contents(public_path('safa-web-product.js'));

        $this->assertStringContainsString('class="dashboard-shortcuts"', $view);
        $this->assertStringContainsString('class="shortcut-grid"', $view);
        $this->assertStringContainsString('shortcut-action red', $view);
        $this->assertStringContainsString('shortcut-action orange', $view);
        $this->assertStringContainsString('shortcut-action green', $view);
        $this->assertStringContainsString('shortcut-action pink', $view);
        $this->assertStringContainsString('shortcut-action yellow', $view);
        $this->assertStringContainsString('shortcut-action teal', $view);
        $this->assertStringContainsString('shortcut-action indigo', $view);
        $this->assertStringContainsString('shortcut-action brown', $view);
        $this->assertStringContainsString("url('/safa-web-product.js')", $view);

        $this->assertStringContainsString('Recent Transaction History', $presentation);
        $this->assertStringContainsString('Ledger Reserves Details', $presentation);
        $this->assertStringContainsString("credentials:'same-origin'", $presentation);
        $this->assertStringNotContainsString("method:'POST'", $presentation);
        $this->assertStringNotContainsString("method:'PATCH'", $presentation);
        $this->assertStringNotContainsString("method:'DELETE'", $presentation);
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
