package com.safa.account.data.money

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyMathTest {
    @Test fun decimalAdditionAvoidsBinaryFloatDrift() {
        assertEquals("0.30", MoneyMath.add("0.10", "0.20").toPlainString())
    }

    @Test fun sarToBdtUsesAmountAndRateScale() {
        assertEquals("321.01", MoneyMath.multiply("10.00", "32.1010").toPlainString())
    }

    @Test fun profitCalculationIsExactAndRoundedOnce() {
        assertEquals("1.01", MoneyMath.profitBdt("10.00", "32.1010", "32.0000").toPlainString())
    }

    @Test fun repeatedWalletDeductionsDoNotAccumulateBinaryDrift() {
        var remaining = MoneyMath.amount("100.00")
        repeat(3) { remaining = MoneyMath.subtract(remaining, "0.10") }
        repeat(2) { remaining = MoneyMath.subtract(remaining, "0.20") }
        assertEquals("99.30", remaining.toPlainString())
    }

    @Test fun canonicalWireStringsMatchDatabaseScale() {
        assertEquals("12.35", MoneyMath.amountString("12.345"))
        assertEquals("32.1235", MoneyMath.rateString("32.12345"))
    }

    @Test fun compatibilityDoubleUsesDecimalStringSemantics() {
        val value = 0.1 + 0.2
        assertTrue(MoneyMath.sameAmount(value, BigDecimal("0.30")))
    }

    @Test fun negativeAndZeroRemainDeterministic() {
        assertEquals("0.00", MoneyMath.amountString(0))
        assertEquals("-1.25", MoneyMath.amountString("-1.25"))
    }
}
