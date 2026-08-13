<?php

namespace App\Support;

/**
 * Canonical mobile-number normalization shared by authentication and lookup.
 *
 * Supported input:
 * - ASCII, Bengali, Arabic-Indic and Persian digits
 * - spaces, hyphens, parentheses and common punctuation
 * - local Saudi (05xxxxxxxx), Bangladesh (01xxxxxxxxx) forms
 * - Saudi +966/966 and Bangladesh +880/880 country-code forms
 *
 * Canonical storage/lookup representation contains ASCII digits only and uses
 * the local leading-zero form.
 */
final class MobileNumber
{
    public static function normalize(string $value): string
    {
        $value = trim($value);
        if ($value === '') {
            return '';
        }

        $value = self::normalizeDigits($value);
        $digits = preg_replace('/\D+/', '', $value) ?? '';
        if ($digits === '') {
            return '';
        }

        // Bangladesh international format -> local canonical form.
        if (str_starts_with($digits, '880') && strlen($digits) === 13) {
            return '0' . substr($digits, 3);
        }

        // Saudi international format -> local canonical form.
        if (str_starts_with($digits, '966') && strlen($digits) === 12) {
            return '0' . substr($digits, 3);
        }

        return $digits;
    }

    public static function isValid(string $value): bool
    {
        $normalized = self::normalize($value);

        // Canonical local mobile numbers are 10 or 11 digits and start with 0.
        return (bool) preg_match('/^0\d{9,10}$/', $normalized);
    }

    public static function normalizeStored(string $value): string
    {
        return self::normalize($value);
    }

    private static function normalizeDigits(string $value): string
    {
        return strtr($value, [
            // Arabic-Indic digits.
            '٠' => '0', '١' => '1', '٢' => '2', '٣' => '3', '٤' => '4',
            '٥' => '5', '٦' => '6', '٧' => '7', '٨' => '8', '٩' => '9',
            // Eastern Arabic/Persian digits.
            '۰' => '0', '۱' => '1', '۲' => '2', '۳' => '3', '۴' => '4',
            '۵' => '5', '۶' => '6', '۷' => '7', '۸' => '8', '۹' => '9',
            // Bengali digits.
            '০' => '0', '১' => '1', '২' => '2', '৩' => '3', '৪' => '4',
            '৫' => '5', '৬' => '6', '৭' => '7', '৮' => '8', '৯' => '9',
        ]);
    }
}
