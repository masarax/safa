<?php

namespace Tests\Unit;

use App\Support\MobileNumber;
use PHPUnit\Framework\TestCase;

class MobileNumberTest extends TestCase
{
    public function test_local_formatted_and_localized_digits_share_one_canonical_value(): void
    {
        $this->assertSame('0536308965', MobileNumber::normalize('0536-308-965'));
        $this->assertSame('0536308965', MobileNumber::normalize('০৫৩৬ ৩০৮ ৯৬৫'));
        $this->assertSame('0536308965', MobileNumber::normalize('٠٥٣٦ ٣٠٨ ٩٦٥'));
    }

    public function test_bangladesh_country_code_is_normalized_to_local_form(): void
    {
        $this->assertSame('01712345678', MobileNumber::normalize('+880 1712-345-678'));
        $this->assertSame('01712345678', MobileNumber::normalize('8801712345678'));
    }

    public function test_supported_mobile_lengths_are_validated_without_accepting_garbage(): void
    {
        $this->assertTrue(MobileNumber::isValid('0536308965'));
        $this->assertTrue(MobileNumber::isValid('01712345678'));
        $this->assertFalse(MobileNumber::isValid('053630896'));
        $this->assertFalse(MobileNumber::isValid('not-a-mobile'));
    }
}
