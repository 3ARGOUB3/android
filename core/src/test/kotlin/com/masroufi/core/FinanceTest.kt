package com.masroufi.core

import kotlin.test.Test
import kotlin.test.assertEquals
import java.time.LocalDateTime
import java.time.YearMonth

class FinanceTest {

    private fun tx(
        amount: Double,
        wallet: String = "cash",
        toWallet: String = "",
        main: String = "food",
        sub: String = main,
        date: LocalDateTime = LocalDateTime.of(2026, 10, 10, 12, 0),
    ) = Transaction("", "", "", toWallet, wallet, "DZD", amount, sub, main, Transaction.timestampOf(date))

    private val data = listOf(
        tx(100_000.0, wallet = "bank", main = "salary"),
        tx(30_000.0, wallet = "bank", toWallet = "cash", main = Transaction.TRANSFER_CATEGORY),
        tx(-500.0, main = "food", sub = "pizza"),
        tx(-1_500.0, main = "food", sub = "groceries"),
        tx(-2_000.0, main = "transport"),
        tx(-700.0, main = "food", date = LocalDateTime.of(2026, 9, 3, 9, 0)),
    )

    @Test
    fun transfersMoveMoneyWithoutCountingAsIncome() {
        val balances = Finance.walletBalances(data).toMap()
        assertEquals(70_000.0, balances["bank"])
        assertEquals(30_000.0 - 500 - 1_500 - 2_000 - 700, balances["cash"])

        val totals = Finance.totals(data)
        assertEquals(100_000.0, totals.income)
        assertEquals(4_700.0, totals.expense)
    }

    @Test
    fun groupsByCategoryAndMonth() {
        val october = Finance.inMonth(data, YearMonth.of(2026, 10))
        assertEquals(5, october.size)
        assertEquals(
            listOf(CategoryTotal("food", 2_000.0, 2), CategoryTotal("transport", 2_000.0, 1)),
            Finance.expensesByCategory(october),
        )
        assertEquals("groceries", Finance.expensesByCategory(october, "food").first().name)

        val months = Finance.monthlyTotals(data, YearMonth.of(2026, 10), 3)
        assertEquals(listOf(YearMonth.of(2026, 8), YearMonth.of(2026, 9), YearMonth.of(2026, 10)), months.map { it.first })
        assertEquals(700.0, months[1].second.expense)
    }

    @Test
    fun storeRoundTrips() {
        val file = kotlin.io.path.createTempFile(suffix = ".json").toFile()
        TransactionStore(file).save(data)
        assertEquals(data, TransactionStore(file).load())
        file.delete()
    }
}
