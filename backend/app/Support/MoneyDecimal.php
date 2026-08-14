<?php

namespace App\Support;

use InvalidArgumentException;

/** Canonical SAFA decimal contract matching database precision/scale. */
final class MoneyDecimal
{
    public const AMOUNT_SCALE = 2;
    public const RATE_SCALE = 4;

    private const FIELD_RULES = [
        'transactions' => [
            'amount' => [2, 13], 'amount_sar' => [2, 13], 'customer_rate' => [4, 6],
            'supplier_rate' => [4, 6], 'amount_bdt' => [2, 13],
        ],
        'supplier_deposits' => [
            'amount_sar' => [2, 13], 'rate' => [4, 6], 'amount_bdt' => [2, 13], 'paid_bdt' => [2, 13],
        ],
        'wallet_batches' => [
            'rate' => [4, 6], 'initial_bdt' => [2, 13], 'remaining_bdt' => [2, 13],
        ],
        'expenses_incomes' => ['amount' => [2, 13]],
    ];

    public static function canonicalizeEntityPayload(string $entity, array $payload): array
    {
        foreach (self::FIELD_RULES[$entity] ?? [] as $field => [$scale, $integerDigits]) {
            if (!array_key_exists($field, $payload) || $payload[$field] === null || $payload[$field] === '') continue;
            $payload[$field] = self::unsigned($payload[$field], $scale, $integerDigits);
        }
        return $payload;
    }

    /**
     * Normalize a non-negative decimal string with HALF_UP rounding without
     * converting through PHP float. This accepts compatibility JSON numbers by
     * stringifying them first, then establishes the database scale exactly.
     */
    public static function unsigned(mixed $value, int $scale, int $integerDigits): string
    {
        if (is_array($value) || is_object($value) || is_bool($value) || $value === null) {
            throw new InvalidArgumentException('Invalid decimal value.');
        }

        $raw = trim((string) $value);
        if (!preg_match('/^(?:0|[0-9]+)(?:\.[0-9]+)?$/', $raw)) {
            throw new InvalidArgumentException('Invalid decimal value.');
        }

        [$whole, $fraction] = array_pad(explode('.', $raw, 2), 2, '');
        $whole = ltrim($whole, '0') ?: '0';
        if (strlen($whole) > $integerDigits) throw new InvalidArgumentException('Decimal value is outside the supported range.');

        $kept = substr($fraction, 0, $scale);
        $kept = str_pad($kept, $scale, '0');
        $roundDigit = strlen($fraction) > $scale ? (int) $fraction[$scale] : 0;

        if ($roundDigit >= 5) {
            $digits = $whole . $kept;
            $carry = 1;
            for ($i = strlen($digits) - 1; $i >= 0 && $carry; $i--) {
                $n = ((int) $digits[$i]) + $carry;
                $digits[$i] = (string) ($n % 10);
                $carry = intdiv($n, 10);
            }
            if ($carry) $digits = '1' . $digits;
            if (strlen($digits) > $integerDigits + $scale) throw new InvalidArgumentException('Decimal value is outside the supported range.');
            $whole = $scale > 0 ? substr($digits, 0, -$scale) : $digits;
            $kept = $scale > 0 ? substr($digits, -$scale) : '';
            $whole = ltrim($whole, '0') ?: '0';
        }

        return $scale > 0 ? $whole . '.' . $kept : $whole;
    }
}
