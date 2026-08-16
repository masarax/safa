<?php

namespace Tests\Feature;

use Tests\TestCase;

class WebDesktopPresentationContractTest extends TestCase
{
    public function test_web_uses_one_product_stylesheet_instead_of_legacy_cascade(): void
    {
        $entry = (string) file_get_contents(public_path('safa-web.css'));
        $product = (string) file_get_contents(public_path('safa-web-product.css'));

        $this->assertStringContainsString("@import url('/safa-web-product.css');", $entry);
        $this->assertStringNotContainsString("safa-web-base.css", $entry);
        $this->assertStringNotContainsString("safa-web-unified.css", $entry);
        $this->assertStringNotContainsString("safa-web-desktop.css", $entry);

        $this->assertStringContainsString('--sidebar-w:248px', $product);
        $this->assertStringContainsString('@media(min-width:1024px)', $product);
        $this->assertStringContainsString('.app-sidebar', $product);
        $this->assertStringContainsString('padding-left:var(--sidebar-w)', $product);
        $this->assertStringContainsString('.app-workspace', $product);
        $this->assertStringContainsString('.mobile-bottom-nav,.app-navigation', $product);
        $this->assertStringContainsString('flex-direction:column', $product);
        $this->assertStringContainsString('.subpage{left:var(--sidebar-w);top:68px}', $product);
        $this->assertStringContainsString('.entity-list{grid-template-columns:repeat(3,minmax(0,1fr))}', $product);
        $this->assertStringContainsString('.settings-grid{grid-template-columns:repeat(3,minmax(0,1fr))}', $product);
    }

    public function test_rebuilt_shell_preserves_android_parity_runtime_contracts(): void
    {
        $view = (string) file_get_contents(resource_path('views/safa/app.blade.php'));
        $runtime = (string) file_get_contents(public_path('safa-web.js'));

        foreach ([
            'data-customer-sale-url',
            'data-customer-adjustment-url',
            'data-supplier-funds-url',
            'data-wallet-withdraw-url',
            'data-profile-settings-url',
            'id="account-select"',
            'id="modal"',
            'id="subpage"',
            'data-nav="dashboard"',
            'data-screen="settings"',
        ] as $contract) {
            $this->assertStringContainsString($contract, $view);
        }

        $this->assertStringContainsString('customerSaleUrl', $runtime);
        $this->assertStringContainsString('supplierFundsUrl', $runtime);
        $this->assertStringContainsString('walletWithdrawUrl', $runtime);
        $this->assertStringContainsString("$$('.screen')", $runtime);
        $this->assertStringContainsString("$$('.bottom-nav-item')", $runtime);
    }
}
