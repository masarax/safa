package com.safa.account.data.money

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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

    @Test fun displayRateUsesExactlyTwoDecimalsWithoutChangingWirePrecision() {
        assertEquals("32.50", MoneyMath.rateDisplayString("32.5000"))
        assertEquals("32.57", MoneyMath.rateDisplayString("32.5678"))
        assertEquals("32.5000", MoneyMath.rateString("32.50"))
    }

    @Test fun blankCreateAmountIsNotTreatedAsExplicitZeroDueOnlyTransaction() {
        assertFalse(MoneyMath.isZeroAmount(""))
        assertFalse(MoneyMath.isZeroAmount("   "))
        assertTrue(MoneyMath.isZeroAmount("0"))
        assertTrue(MoneyMath.isZeroAmount("0.00"))
    }

    @Test fun legacyJsonNumberIsCanonicalizedAtTheIngestionBoundary() {
        val value = 0.1 + 0.2
        assertTrue(MoneyMath.sameAmount(value, BigDecimal("0.30")))
    }

    @Test fun negativeAndZeroRemainDeterministic() {
        assertEquals("0.00", MoneyMath.amountString(0))
        assertEquals("-1.25", MoneyMath.amountString("-1.25"))
        assertTrue(MoneyMath.isZeroAmount("0.00"))
        assertTrue(MoneyMath.isZeroAmount("00.004"))
        assertTrue(!MoneyMath.isZeroAmount("0.005"))
    }

    @Test fun weightedWalletRateUsesExactBaseUnits() {
        val rate = MoneyMath.weightedRate(
            listOf(
                MoneyMath.amount("3200") to MoneyMath.rate("32"),
                MoneyMath.amount("3300") to MoneyMath.rate("33")
            )
        )
        assertEquals("32.5000", rate.toPlainString())
    }

    @Test fun maximumValuesAreAcceptedAndRoundingOverflowIsRejected() {
        assertEquals("9999999999999.99", MoneyMath.amountString("9999999999999.99"))
        assertEquals("999999.9999", MoneyMath.rateString("999999.9999"))
        assertThrows(IllegalArgumentException::class.java) {
            MoneyMath.amountString("9999999999999.995")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MoneyMath.add("9999999999999.99", "0.01")
        }
        assertThrows(IllegalArgumentException::class.java) {
            MoneyMath.multiply("9999999999999.99", "2")
        }
    }

    @Test fun nonNegativeBusinessBoundariesRejectNegativeValues() {
        assertThrows(IllegalArgumentException::class.java) { MoneyMath.nonNegativeAmount("-0.01") }
        assertThrows(IllegalArgumentException::class.java) { MoneyMath.nonNegativeRate("-0.0001") }
    }
}
