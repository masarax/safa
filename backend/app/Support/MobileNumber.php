<?php

namespace App\Support;

/**
 * Canonical mobile-number normalization shared by login and inactive-account checks.
 *
 * Supported input includes ASCII/Bengali/Arabic-Indic digits, common separators,
 * local numbers beginning with 0, and the Bangladesh +880/880 country-code form.
 * The canonical value contains digits only.
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

        // Normalize Bangladesh international format to its local canonical form.
        if (str_starts_with($digits, '880') && strlen($digits) === 13) {
            return '0' . substr($digits, 3);
        }

        return $digits;
    }

    public static function isValid(string $value): bool
    {
        $normalized = self::normalize($value);

        // SAFA currently stores local mobile numbers as 10 or 11 digits,
        // beginning with 0 (e.g. Saudi 05xxxxxxxx or Bangladesh 01xxxxxxxxx).
        return (bool) preg_match('/^0\d{9,10}$/', $normalized);
    }

    public static function normalizeStored(string $value): string
    {
        return self::normalize($value);
    }

    private static function normalizeDigits(string $value): string
    {
        return strtr($value, [
            // Arabic-Indic digits: U+0660..U+0669.
            '٠' => '0', '١' => '1', '٢' => '2', '٣' => '3', '٤' => '4',
            '٥' => '5', '٦' => '6', '٧' => '7', '٨' => '8', '٩' => '9',
            // Eastern Arabic/Persian digits: U+06F0..U+06F9.
            '۰' => '0', '۱' => '1', '۲' => '2', '۳' => '3', '۴' => '4',
            '۵' => '5', '۶' => '6', '۷' => '7', '۸' => '8', '۹' => '9',
            // Bengali digits: U+09E6..U+09EF.
            '০' => '0', '১' => '1', '২' => '2', '৩' => '3', '৪' => '4',
            '৫' => '5', '৬' => '6', '৭' => '7', '৮' => '8', '৯' => '9',
        ]);
    }
}
