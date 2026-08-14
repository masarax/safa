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

    public function test_signed_adjustments_and_exact_comparison_never_use_float_math(): void
    {
        $this->assertSame('-0.01', MoneyDecimal::signed('-0.005', 2, 13));
        $this->assertSame('0.00', MoneyDecimal::signed('-0.004', 2, 13));
        $this->assertSame(1, MoneyDecimal::compare('9999999999999.99', '9999999999999.98', 2, 13));
        $this->assertSame(-1, MoneyDecimal::compare('-0.02', '-0.01', 2, 13));
    }

    public function test_only_the_sar_collection_adjustment_is_signed(): void
    {
        $payload = MoneyDecimal::canonicalizeEntityPayload('transactions', [
            'amount_sar' => '10',
            'sar_collected' => '-0.105',
            'bdt_disbursed' => '321.005',
        ]);

        $this->assertSame('10.00', $payload['amount_sar']);
        $this->assertSame('-0.11', $payload['sar_collected']);
        $this->assertSame('321.01', $payload['bdt_disbursed']);
    }

    public function test_negative_bdt_disbursement_is_rejected(): void
    {
        $this->expectException(InvalidArgumentException::class);
        MoneyDecimal::canonicalizeEntityPayload('transactions', ['bdt_disbursed' => '-0.01']);
    }

    public function test_account_balance_and_rate_table_use_the_same_contract(): void
    {
        $this->assertSame('12.35', MoneyDecimal::canonicalizeEntityPayload('accounts', ['balance' => '12.345'])['balance']);
        $this->assertSame('32.1235', MoneyDecimal::canonicalizeEntityPayload('rates', ['rate' => '32.12345'])['rate']);
    }
}
