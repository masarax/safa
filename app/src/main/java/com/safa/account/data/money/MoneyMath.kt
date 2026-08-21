package com.safa.account.data.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Canonical SAFA monetary contract.
 *
 * Database amounts are DECIMAL(15,2) and exchange rates are DECIMAL(10,4).
 * All business calculations and persistence/wire formatting must pass through
 * this object. Legacy JSON libraries may expose a wire number as Float/Double;
 * that ingestion boundary is converted immediately with BigDecimal.valueOf.
 * Domain models and business/UI state never use binary floating point for money.
 */
object MoneyMath {
    const val AMOUNT_SCALE = 2
    const val RATE_SCALE = 4
    const val AMOUNT_INTEGER_DIGITS = 13
    const val RATE_INTEGER_DIGITS = 6
    val ROUNDING: RoundingMode = RoundingMode.HALF_UP
    val ZERO_AMOUNT: BigDecimal = BigDecimal.ZERO.setScale(AMOUNT_SCALE)
    val ZERO_RATE: BigDecimal = BigDecimal.ZERO.setScale(RATE_SCALE)

    fun amount(value: Any?): BigDecimal = scaled(value, AMOUNT_SCALE, AMOUNT_INTEGER_DIGITS)
    fun rate(value: Any?): BigDecimal = scaled(value, RATE_SCALE, RATE_INTEGER_DIGITS)

    fun nonNegativeAmount(value: Any?): BigDecimal = amount(value).also {
        require(it.signum() >= 0) { "Amount must not be negative." }
    }

    fun nonNegativeRate(value: Any?): BigDecimal = rate(value).also {
        require(it.signum() >= 0) { "Rate must not be negative." }
    }

    fun amountString(value: Any?): String = amount(value).toPlainString()
    fun rateString(value: Any?): String = rate(value).toPlainString()
    fun rateDisplayString(value: Any?): String = rate(value).setScale(2, ROUNDING).toPlainString()

    fun multiply(rawAmount: Any?, rawRate: Any?): BigDecimal =
        amount(amount(rawAmount).multiply(rate(rawRate)))

    fun add(left: Any?, right: Any?): BigDecimal =
        amount(amount(left).add(amount(right)))

    fun subtract(left: Any?, right: Any?): BigDecimal =
        amount(amount(left).subtract(amount(right)))

    fun clampNonNegativeAmount(value: Any?): BigDecimal = amount(value).max(ZERO_AMOUNT)

    fun divideAmountByRate(rawAmount: Any?, rawRate: Any?): BigDecimal {
        val divisor = rate(rawRate)
        if (divisor.compareTo(BigDecimal.ZERO) == 0) return ZERO_AMOUNT
        return amount(amount(rawAmount).divide(divisor, AMOUNT_SCALE, ROUNDING))
    }

    fun sumAmounts(values: Iterable<Any?>): BigDecimal = values.fold(ZERO_AMOUNT) { total, value ->
        add(total, value)
    }

    fun weightedRate(positions: Iterable<Pair<Any?, Any?>>): BigDecimal {
        var totalAmount = ZERO_AMOUNT
        var totalBaseUnits = BigDecimal.ZERO
        positions.forEach { (rawAmount, rawRate) ->
            val amount = amount(rawAmount)
            val rate = rate(rawRate)
            if (amount.signum() > 0 && rate.signum() > 0) {
                totalAmount = add(totalAmount, amount)
                totalBaseUnits = totalBaseUnits.add(
                    amount.divide(rate, AMOUNT_SCALE + RATE_SCALE + 4, ROUNDING)
                )
            }
        }
        if (totalBaseUnits.signum() == 0) return ZERO_RATE
        return rate(totalAmount.divide(totalBaseUnits, RATE_SCALE, ROUNDING))
    }

    fun profitBdt(amountSar: Any?, customerRate: Any?, supplierRate: Any?): BigDecimal =
        amount(rate(customerRate)
            .subtract(rate(supplierRate))
            .multiply(amount(amountSar)))

    fun profitSar(amountSar: Any?, amountBdt: Any?, customerRate: Any?): BigDecimal {
        val rate = rate(customerRate)
        if (rate.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO.setScale(AMOUNT_SCALE)
        val costSar = amount(amountBdt).divide(rate, AMOUNT_SCALE + RATE_SCALE, ROUNDING)
        return amount(amount(amountSar).subtract(costSar))
    }

    fun sameAmount(left: Any?, right: Any?): Boolean = amount(left).compareTo(amount(right)) == 0
    fun sameRate(left: Any?, right: Any?): Boolean = rate(left).compareTo(rate(right)) == 0
    fun isZeroAmount(value: Any?): Boolean {
        if (value is CharSequence && value.toString().trim().isEmpty()) return false
        return amount(value).signum() == 0
    }

    fun isPositiveAmount(value: Any?): Boolean = amount(value).signum() > 0
    fun isPositiveRate(value: Any?): Boolean = rate(value).signum() > 0

    private fun scaled(value: Any?, scale: Int, integerDigits: Int): BigDecimal {
        val normalized = decimal(value).setScale(scale, ROUNDING)
        val wholeDigits = (normalized.precision() - normalized.scale()).coerceAtLeast(1)
        require(wholeDigits <= integerDigits) { "Decimal value is outside the supported range." }
        return normalized
    }

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
