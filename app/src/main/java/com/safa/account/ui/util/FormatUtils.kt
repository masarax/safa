package com.safa.account.ui.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormatUtils {
    private val integerFormat = DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US))
    private val decimalFormat = DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US))
    private val dateFormatStandard = SimpleDateFormat("dd MMM, yyyy", Locale.US)
    private val dateTimeFormatStandard = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.US)
    private val dateIsoFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    @Synchronized
    fun formatInteger(value: Number): String {
        return integerFormat.format(value)
    }

    @Synchronized
    fun formatDecimal(value: Number): String {
        return decimalFormat.format(value)
    }

    @Synchronized
    fun formatDate(timestamp: Long): String {
        return dateFormatStandard.format(Date(timestamp))
    }

    @Synchronized
    fun formatDateTime(timestamp: Long): String {
        return dateTimeFormatStandard.format(Date(timestamp))
    }

    @Synchronized
    fun formatDateIso(timestamp: Long): String {
        return dateIsoFormat.format(Date(timestamp))
    }
}
