<?php

namespace Tests\Feature;

use Tests\TestCase;

class WebDesktopPresentationContractTest extends TestCase
{
    public function test_web_styles_preserve_mobile_base_and_add_desktop_workspace_layer(): void
    {
        $entry = (string) file_get_contents(public_path('safa-web.css'));
        $desktop = (string) file_get_contents(public_path('safa-web-desktop.css'));

        $this->assertStringContainsString("@import url('/safa-web-base.css');", $entry);
        $this->assertStringContainsString("@import url('/safa-web-desktop.css');", $entry);
        $this->assertFileExists(public_path('safa-web-base.css'));

        $this->assertStringContainsString('@media (min-width: 1024px)', $desktop);
        $this->assertStringContainsString('--desktop-nav-w: 252px', $desktop);
        $this->assertStringContainsString('.mobile-bottom-nav', $desktop);
        $this->assertStringContainsString('flex-direction: column', $desktop);
        $this->assertStringContainsString('.mobile-app-shell', $desktop);
        $this->assertStringContainsString('padding-left: var(--desktop-nav-w)', $desktop);
        $this->assertStringContainsString('.subpage', $desktop);
        $this->assertStringContainsString('left: var(--desktop-nav-w)', $desktop);
        $this->assertStringContainsString('#customers-list', $desktop);
        $this->assertStringContainsString('#suppliers-list', $desktop);
        $this->assertStringContainsString('.settings-grid', $desktop);
    }

    public function test_desktop_presentation_does_not_replace_android_parity_runtime(): void
    {
        $view = (string) file_get_contents(resource_path('views/safa/app.blade.php'));
        $runtime = (string) file_get_contents(public_path('safa-web.js'));

        $this->assertStringContainsString('data-customer-sale-url', $view);
        $this->assertStringContainsString('data-customer-adjustment-url', $view);
        $this->assertStringContainsString('data-supplier-funds-url', $view);
        $this->assertStringContainsString('data-wallet-withdraw-url', $view);
        $this->assertStringContainsString('data-profile-settings-url', $view);

        $this->assertStringContainsString('customerSaleUrl', $runtime);
        $this->assertStringContainsString('supplierFundsUrl', $runtime);
        $this->assertStringContainsString('walletWithdrawUrl', $runtime);
    }
}
