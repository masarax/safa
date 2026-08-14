package com.safa.account.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorDialogTest {
    @Test fun decimalExpressionAvoidsBinaryFloatDrift() {
        assertEquals("0.3", evaluateExpression("0.1 + 0.2").toPlainString())
        assertEquals("9.63702", evaluateExpression("0.3 * 32.1234").stripTrailingZeros().toPlainString())
    }

    @Test fun divisionUsesDocumentedHalfUpIntermediatePrecision() {
        assertEquals("0.3333333333333333", tryEvaluate("1 ÷ 3")?.toPlainString())
    }

    @Test fun invalidExpressionsNeverProduceAFinancialValue() {
        assertNull(tryEvaluate("1 / 0"))
        assertNull(tryEvaluate("1 + )"))
    }
}
