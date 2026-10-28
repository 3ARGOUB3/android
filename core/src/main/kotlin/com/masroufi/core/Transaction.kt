package com.masroufi.core

import kotlinx.serialization.Serializable
import java.time.LocalDateTime
import java.time.ZoneOffset

/**
 * One row of the expense-tracker export.
 *
 * [amount] is signed: negative for spending, positive for income.
 * For a transfer, [wallet] is the source, [toWallet] the destination and
 * [amount] the (positive) sum moved.
 */
@Serializable
data class Transaction(
    val note: String,
    val group: String,
    val contact: String,
    val toWallet: String,
    val wallet: String,
    val currency: String,
    val amount: Double,
    val category: String,
    val mainCategory: String,
    /** Local date-time stored as epoch seconds with a UTC offset (no time-zone shift). */
    val timestamp: Long,
) {
    val dateTime: LocalDateTime
        get() = LocalDateTime.ofEpochSecond(timestamp, 0, ZoneOffset.UTC)

    /** Money moved between the user's own wallets: not income or spending. */
    val isTransfer: Boolean
        get() = toWallet.isNotBlank() || mainCategory.trim() == TRANSFER_CATEGORY

    val isIncome: Boolean get() = !isTransfer && amount > 0
    val isExpense: Boolean get() = !isTransfer && amount < 0

    companion object {
        const val TRANSFER_CATEGORY = "تحويل رصيد"

        fun timestampOf(dateTime: LocalDateTime): Long = dateTime.toEpochSecond(ZoneOffset.UTC)
    }
}
