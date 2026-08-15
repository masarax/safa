<?php

namespace Tests\Feature;

use Tests\TestCase;

class WebMoneyContractTest extends TestCase
{
    public function test_browser_financial_summaries_use_fixed_scale_integer_math(): void
    {
        $script = (string) file_get_contents(public_path('safa-web.js'));

        $this->assertStringContainsString('const toMinorUnits', $script);
        $this->assertStringContainsString('BigInt(', $script);
        $this->assertStringContainsString('0n', $script);
        $this->assertStringContainsString('formatMinorUnits', $script);
        $this->assertStringNotContainsString('Number.parseFloat', $script);
        $this->assertStringNotContainsString('.toFixed(2)', $script);
    }
}
