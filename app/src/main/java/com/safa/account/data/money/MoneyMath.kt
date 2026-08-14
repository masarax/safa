package com.safa.account.data.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Canonical SAFA monetary contract.
 *
 * Database amounts are DECIMAL(15,2) and exchange rates are DECIMAL(10,4).
 * All business calculations and persistence/wire formatting must pass through
 * this object. Double is accepted only as a compatibility input from existing
 * Compose view state and is converted using BigDecimal.valueOf, never the
 * binary-floating BigDecimal(Double) constructor.
 */
object MoneyMath {
    const val AMOUNT_SCALE = 2
    const val RATE_SCALE = 4
    val ROUNDING: RoundingMode = RoundingMode.HALF_UP

    fun amount(value: Any?): BigDecimal = decimal(value).setScale(AMOUNT_SCALE, ROUNDING)
    fun rate(value: Any?): BigDecimal = decimal(value).setScale(RATE_SCALE, ROUNDING)

    fun amountString(value: Any?): String = amount(value).toPlainString()
    fun rateString(value: Any?): String = rate(value).toPlainString()

    fun multiply(amount: Any?, rate: Any?): BigDecimal =
        amount(amount).multiply(rate(rate)).setScale(AMOUNT_SCALE, ROUNDING)

    fun add(left: Any?, right: Any?): BigDecimal =
        amount(left).add(amount(right)).setScale(AMOUNT_SCALE, ROUNDING)

    fun subtract(left: Any?, right: Any?): BigDecimal =
        amount(left).subtract(amount(right)).setScale(AMOUNT_SCALE, ROUNDING)

    fun profitBdt(amountSar: Any?, customerRate: Any?, supplierRate: Any?): BigDecimal =
        rate(customerRate)
            .subtract(rate(supplierRate))
            .multiply(amount(amountSar))
            .setScale(AMOUNT_SCALE, ROUNDING)

    fun profitSar(amountSar: Any?, amountBdt: Any?, customerRate: Any?): BigDecimal {
        val rate = rate(customerRate)
        if (rate.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO.setScale(AMOUNT_SCALE)
        val costSar = amount(amountBdt).divide(rate, AMOUNT_SCALE + RATE_SCALE, ROUNDING)
        return amount(amountSar).subtract(costSar).setScale(AMOUNT_SCALE, ROUNDING)
    }

    fun sameAmount(left: Any?, right: Any?): Boolean = amount(left).compareTo(amount(right)) == 0
    fun sameRate(left: Any?, right: Any?): Boolean = rate(left).compareTo(rate(right)) == 0

    private fun decimal(value: Any?): BigDecimal = when (value) {
        null -> BigDecimal.ZERO
        is BigDecimal -> value
        is Byte, is Short, is Int, is Long -> BigDecimal(value.toString())
        is Float -> BigDecimal.valueOf(value.toDouble())
        is Double -> BigDecimal.valueOf(value)
        is Number -> BigDecimal(value.toString())
        else -> value.toString().trim().takeIf { it.isNotEmpty() }?.let(::BigDecimal) ?: BigDecimal.ZERO
    }
}
