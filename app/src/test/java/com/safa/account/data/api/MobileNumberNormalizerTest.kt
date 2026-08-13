package com.safa.account.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

class MobileNumberNormalizerTest {
    @Test
    fun localized_and_formatted_numbers_share_one_canonical_value() {
        assertEquals("0536308965", MobileNumberNormalizer.normalize("0536-308-965"))
        assertEquals("0536308965", MobileNumberNormalizer.normalize("০৫৩৬ ৩০৮ ৯৬৫"))
        assertEquals("0536308965", MobileNumberNormalizer.normalize("٠٥٣٦ ٣٠٨ ٩٦٥"))
    }

    @Test
    fun country_code_forms_use_local_canonical_value() {
        assertEquals("0501234567", MobileNumberNormalizer.normalize("+966 50 123 4567"))
        assertEquals("01712345678", MobileNumberNormalizer.normalize("+880 1712-345-678"))
    }

    @Test
    fun localized_pin_digits_are_normalized_to_ascii() {
        assertEquals("123456", MobileNumberNormalizer.normalizePin("١٢৩৪٥৬"))
    }
}
