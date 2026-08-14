<?php

namespace App\Support;

use InvalidArgumentException;

/** Canonical SAFA decimal contract matching database precision/scale. */
final class MoneyDecimal
{
    public const AMOUNT_SCALE = 2;
    public const RATE_SCALE = 4;

    private const FIELD_RULES = [
        'accounts' => ['balance' => [2, 13]],
        'rates' => ['rate' => [4, 6]],
        'transactions' => [
            'amount' => [2, 13], 'amount_sar' => [2, 13], 'customer_rate' => [4, 6],
            'supplier_rate' => [4, 6], 'amount_bdt' => [2, 13],
            // Collection adjustments can be negative when returning a customer's
            // advance balance; principal transaction amounts remain unsigned.
            'sar_collected' => [2, 13, true], 'bdt_disbursed' => [2, 13],
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
        foreach (self::FIELD_RULES[$entity] ?? [] as $field => $rule) {
            if (!array_key_exists($field, $payload) || $payload[$field] === null || $payload[$field] === '') continue;
            [$scale, $integerDigits, $signed] = array_pad($rule, 3, false);
            $payload[$field] = $signed
                ? self::signed($payload[$field], $scale, $integerDigits)
                : self::unsigned($payload[$field], $scale, $integerDigits);
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
        return self::normalize($value, $scale, $integerDigits, false);
    }

    public static function signed(mixed $value, int $scale, int $integerDigits): string
    {
        return self::normalize($value, $scale, $integerDigits, true);
    }

    /** Compare canonical decimals without requiring ext-bcmath or float casts. */
    public static function compare(mixed $left, mixed $right, int $scale, int $integerDigits, bool $signed = true): int
    {
        $a = self::normalize($left, $scale, $integerDigits, $signed);
        $b = self::normalize($right, $scale, $integerDigits, $signed);
        $aNegative = str_starts_with($a, '-');
        $bNegative = str_starts_with($b, '-');
        if ($aNegative !== $bNegative) return $aNegative ? -1 : 1;

        $aMagnitude = ltrim($a, '-');
        $bMagnitude = ltrim($b, '-');
        [$aw, $af] = array_pad(explode('.', $aMagnitude, 2), 2, '');
        [$bw, $bf] = array_pad(explode('.', $bMagnitude, 2), 2, '');
        $result = strlen($aw) <=> strlen($bw);
        if ($result === 0) $result = strcmp($aw, $bw) <=> 0;
        if ($result === 0) $result = strcmp($af, $bf) <=> 0;
        return $aNegative ? -$result : $result;
    }

    private static function normalize(mixed $value, int $scale, int $integerDigits, bool $allowNegative): string
    {
        if (is_array($value) || is_object($value) || is_bool($value) || $value === null) {
            throw new InvalidArgumentException('Invalid decimal value.');
        }

        $raw = trim((string) $value);
        $pattern = $allowNegative ? '/^-?(?:0|[0-9]+)(?:\.[0-9]+)?$/' : '/^(?:0|[0-9]+)(?:\.[0-9]+)?$/';
        if (!preg_match($pattern, $raw)) {
            throw new InvalidArgumentException('Invalid decimal value.');
        }

        $negative = str_starts_with($raw, '-');
        if ($negative) $raw = substr($raw, 1);

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

        $normalized = $scale > 0 ? $whole . '.' . $kept : $whole;
        $isZero = trim(str_replace(['0', '.'], '', $normalized)) === '';
        return $negative && !$isZero ? '-' . $normalized : $normalized;
    }
}
