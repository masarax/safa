<?php

namespace Tests\Unit;

use App\Support\MoneyDecimal;
use InvalidArgumentException;
use PHPUnit\Framework\TestCase;

class MoneyDecimalTest extends TestCase
{
    public function test_amount_and_rate_round_half_up_without_float_math(): void
    {
        $this->assertSame('0.30', MoneyDecimal::unsigned('0.30000000000000004', 2, 13));
        $this->assertSame('12.35', MoneyDecimal::unsigned('12.345', 2, 13));
        $this->assertSame('32.1235', MoneyDecimal::unsigned('32.12345', 4, 6));
    }

    public function test_entity_payload_uses_database_scales(): void
    {
        $payload = MoneyDecimal::canonicalizeEntityPayload('transactions', [
            'amount_sar' => '10.005',
            'customer_rate' => '32.12345',
            'supplier_rate' => '32',
            'amount_bdt' => '321.234',
        ]);

        $this->assertSame('10.01', $payload['amount_sar']);
        $this->assertSame('32.1235', $payload['customer_rate']);
        $this->assertSame('32.0000', $payload['supplier_rate']);
        $this->assertSame('321.23', $payload['amount_bdt']);
    }

    public function test_negative_value_is_rejected(): void
    {
        $this->expectException(InvalidArgumentException::class);
        MoneyDecimal::unsigned('-0.01', 2, 13);
    }

    public function test_precision_overflow_is_rejected(): void
    {
        $this->expectException(InvalidArgumentException::class);
        MoneyDecimal::unsigned('10000000000000.00', 2, 13);
    }
}
