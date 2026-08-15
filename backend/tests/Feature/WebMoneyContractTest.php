<?php

namespace Tests\Feature;

use App\Support\DecimalMath;
use Tests\TestCase;

class WebMoneyContractTest extends TestCase
{
    public function test_web_financial_mutations_delegate_to_exact_server_decimal_math(): void
    {
        $controller = (string) file_get_contents(app_path('Http/Controllers/WebMobileFlowController.php'));
        $decimalMath = (string) file_get_contents(app_path('Support/DecimalMath.php'));
        $script = (string) file_get_contents(public_path('safa-web.js'));

        // The browser is a presentation/workflow client. Every coupled monetary
        // mutation is recalculated at the server boundary with canonical decimal
        // strings before database or wallet state is changed.
        $this->assertStringContainsString('DecimalMath::multiplyAmountRate', $controller);
        $this->assertStringContainsString('DecimalMath::subtractAmount', $controller);
        $this->assertStringContainsString('DecimalMath::addAmount', $controller);
        $this->assertStringContainsString('MoneyDecimal::unsigned', $controller);
        $this->assertStringContainsString('MoneyDecimal::signed', $controller);
        $this->assertStringContainsString('multiplyUnsignedIntegers', $decimalMath);

        // Coupled customer/supplier/wallet operations must use the mobile-flow
        // endpoints instead of directly mutating generic CRUD resources.
        $this->assertStringContainsString('urls.customerSale', $script);
        $this->assertStringContainsString('urls.customerAdjustment', $script);
        $this->assertStringContainsString('urls.supplierFunds', $script);
        $this->assertStringContainsString('urls.walletWithdraw', $script);

        // Fixed-point arithmetic itself is deterministic without float drift.
        $this->assertSame('321.00', DecimalMath::multiplyAmountRate('10.00', '32.1000'));
        $this->assertSame('0.30', DecimalMath::addAmount('0.10', '0.20'));
        $this->assertSame('679.00', DecimalMath::subtractAmount('1000.00', '321.00'));
    }
}
