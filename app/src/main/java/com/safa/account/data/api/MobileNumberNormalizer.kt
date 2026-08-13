package com.safa.account.data.api

/**
 * Canonical mobile input normalization matching the Laravel MobileNumber contract.
 * Returns ASCII digits in local leading-zero form.
 */
object MobileNumberNormalizer {
    fun normalize(value: String): String {
        val digits = value.trim()
            .mapNotNull { char ->
                when (char) {
                    in '0'..'9' -> char
                    '٠' -> '0'; '١' -> '1'; '٢' -> '2'; '٣' -> '3'; '٤' -> '4'
                    '٥' -> '5'; '٦' -> '6'; '٧' -> '7'; '٨' -> '8'; '٩' -> '9'
                    '۰' -> '0'; '۱' -> '1'; '۲' -> '2'; '۳' -> '3'; '۴' -> '4'
                    '۵' -> '5'; '۶' -> '6'; '۷' -> '7'; '۸' -> '8'; '۹' -> '9'
                    '০' -> '0'; '১' -> '1'; '২' -> '2'; '৩' -> '3'; '৪' -> '4'
                    '৫' -> '5'; '৬' -> '6'; '৭' -> '7'; '৮' -> '8'; '৯' -> '9'
                    else -> null
                }
            }
            .joinToString("")

        return when {
            digits.length == 13 && digits.startsWith("880") -> "0${digits.drop(3)}"
            digits.length == 12 && digits.startsWith("966") -> "0${digits.drop(3)}"
            else -> digits
        }
    }

    fun normalizePin(value: String): String = normalize(value)
}
