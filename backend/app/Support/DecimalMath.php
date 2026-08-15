<?php

namespace App\Support;

/**
 * Small arbitrary-precision decimal helpers for SAFA domain operations.
 *
 * This intentionally does not require ext-bcmath so the same exact-decimal
 * business rules work on constrained cPanel hosts. Inputs are canonicalized by
 * MoneyDecimal before arithmetic and results are returned at database scale.
 */
final class DecimalMath
{
    public static function addAmount(mixed $left, mixed $right): string
    {
        return self::add($left, $right, MoneyDecimal::AMOUNT_SCALE, 13);
    }

    public static function subtractAmount(mixed $left, mixed $right): string
    {
        return self::add($left, self::negate($right, MoneyDecimal::AMOUNT_SCALE, 13), MoneyDecimal::AMOUNT_SCALE, 13);
    }

    public static function multiplyAmountRate(mixed $amount, mixed $rate): string
    {
        $amountValue = MoneyDecimal::unsigned($amount, MoneyDecimal::AMOUNT_SCALE, 13);
        $rateValue = MoneyDecimal::unsigned($rate, MoneyDecimal::RATE_SCALE, 6);
        $amountDigits = str_replace('.', '', $amountValue);
        $rateDigits = str_replace('.', '', $rateValue);
        $product = self::multiplyUnsignedIntegers($amountDigits, $rateDigits);
        $scale = MoneyDecimal::AMOUNT_SCALE + MoneyDecimal::RATE_SCALE;
        $product = str_pad($product, $scale + 1, '0', STR_PAD_LEFT);
        $decimal = substr($product, 0, -$scale) . '.' . substr($product, -$scale);

        return MoneyDecimal::unsigned($decimal, MoneyDecimal::AMOUNT_SCALE, 13);
    }

    public static function compareAmount(mixed $left, mixed $right): int
    {
        return MoneyDecimal::compare($left, $right, MoneyDecimal::AMOUNT_SCALE, 13, true);
    }

    public static function minAmount(mixed $left, mixed $right): string
    {
        $a = MoneyDecimal::unsigned($left, MoneyDecimal::AMOUNT_SCALE, 13);
        $b = MoneyDecimal::unsigned($right, MoneyDecimal::AMOUNT_SCALE, 13);
        return MoneyDecimal::compare($a, $b, MoneyDecimal::AMOUNT_SCALE, 13, false) <= 0 ? $a : $b;
    }

    private static function add(mixed $left, mixed $right, int $scale, int $integerDigits): string
    {
        $a = MoneyDecimal::signed($left, $scale, $integerDigits);
        $b = MoneyDecimal::signed($right, $scale, $integerDigits);
        [$aNegative, $aDigits] = self::scaledInteger($a, $scale);
        [$bNegative, $bDigits] = self::scaledInteger($b, $scale);

        if ($aNegative === $bNegative) {
            $digits = self::addUnsignedIntegers($aDigits, $bDigits);
            $negative = $aNegative;
        } else {
            $comparison = self::compareUnsignedIntegers($aDigits, $bDigits);
            if ($comparison === 0) {
                $digits = '0';
                $negative = false;
            } elseif ($comparison > 0) {
                $digits = self::subtractUnsignedIntegers($aDigits, $bDigits);
                $negative = $aNegative;
            } else {
                $digits = self::subtractUnsignedIntegers($bDigits, $aDigits);
                $negative = $bNegative;
            }
        }

        $digits = str_pad($digits, $scale + 1, '0', STR_PAD_LEFT);
        $decimal = $scale > 0
            ? substr($digits, 0, -$scale) . '.' . substr($digits, -$scale)
            : $digits;
        if ($negative && trim(str_replace(['0', '.'], '', $decimal)) !== '') $decimal = '-' . $decimal;

        return MoneyDecimal::signed($decimal, $scale, $integerDigits);
    }

    private static function negate(mixed $value, int $scale, int $integerDigits): string
    {
        $normalized = MoneyDecimal::signed($value, $scale, $integerDigits);
        if (MoneyDecimal::compare($normalized, 0, $scale, $integerDigits, true) === 0) return $normalized;
        return str_starts_with($normalized, '-') ? substr($normalized, 1) : '-' . $normalized;
    }

    private static function scaledInteger(string $decimal, int $scale): array
    {
        $negative = str_starts_with($decimal, '-');
        $raw = ltrim($decimal, '-');
        [$whole, $fraction] = array_pad(explode('.', $raw, 2), 2, '');
        $digits = ltrim($whole . str_pad(substr($fraction, 0, $scale), $scale, '0'), '0') ?: '0';
        return [$negative && $digits !== '0', $digits];
    }

    private static function compareUnsignedIntegers(string $a, string $b): int
    {
        $a = ltrim($a, '0') ?: '0';
        $b = ltrim($b, '0') ?: '0';
        if (strlen($a) !== strlen($b)) return strlen($a) <=> strlen($b);
        return strcmp($a, $b) <=> 0;
    }

    private static function addUnsignedIntegers(string $a, string $b): string
    {
        $a = strrev($a); $b = strrev($b); $length = max(strlen($a), strlen($b));
        $carry = 0; $result = '';
        for ($i = 0; $i < $length; $i++) {
            $sum = ($i < strlen($a) ? (int) $a[$i] : 0) + ($i < strlen($b) ? (int) $b[$i] : 0) + $carry;
            $result .= (string) ($sum % 10);
            $carry = intdiv($sum, 10);
        }
        if ($carry) $result .= (string) $carry;
        return strrev($result);
    }

    /** $a must be >= $b. */
    private static function subtractUnsignedIntegers(string $a, string $b): string
    {
        $a = strrev($a); $b = strrev($b); $borrow = 0; $result = '';
        for ($i = 0; $i < strlen($a); $i++) {
            $digit = (int) $a[$i] - $borrow - ($i < strlen($b) ? (int) $b[$i] : 0);
            if ($digit < 0) { $digit += 10; $borrow = 1; } else { $borrow = 0; }
            $result .= (string) $digit;
        }
        return ltrim(strrev($result), '0') ?: '0';
    }

    private static function multiplyUnsignedIntegers(string $a, string $b): string
    {
        $a = ltrim($a, '0') ?: '0'; $b = ltrim($b, '0') ?: '0';
        if ($a === '0' || $b === '0') return '0';
        $result = array_fill(0, strlen($a) + strlen($b), 0);
        for ($i = strlen($a) - 1; $i >= 0; $i--) {
            for ($j = strlen($b) - 1; $j >= 0; $j--) {
                $position = $i + $j + 1;
                $sum = $result[$position] + ((int) $a[$i] * (int) $b[$j]);
                $result[$position] = $sum % 10;
                $result[$position - 1] += intdiv($sum, 10);
            }
        }
        return ltrim(implode('', $result), '0') ?: '0';
    }
}
