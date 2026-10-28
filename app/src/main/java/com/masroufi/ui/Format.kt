package com.masroufi.ui

import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

// Only used from the main thread, so sharing the formatter is safe.
private val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
    minimumFractionDigits = 0
    maximumFractionDigits = 2
}

fun formatMoney(amount: Double, currency: String = "DZD"): String =
    "${numberFormat.format(amount)} $currency".trim()

fun formatSigned(amount: Double, currency: String = "DZD"): String =
    (if (amount > 0) "+" else "") + formatMoney(amount, currency)

val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)

fun formatMonth(month: YearMonth): String =
    month.format(DateTimeFormatter.ofPattern("LLLL yyyy", Locale.getDefault()))
