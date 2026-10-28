package com.masroufi.core

import java.time.YearMonth

data class Totals(val income: Double, val expense: Double) {
    val net: Double get() = income - expense
}

data class CategoryTotal(val name: String, val amount: Double, val count: Int)

/** Calculations over a list of transactions. Expense amounts are returned as positive numbers. */
object Finance {

    /**
     * Running balance of every wallet, highest first.
     * Transfers move money from [Transaction.wallet] to [Transaction.toWallet].
     */
    fun walletBalances(transactions: List<Transaction>): List<Pair<String, Double>> {
        val balances = linkedMapOf<String, Double>()
        fun add(wallet: String, value: Double) {
            if (wallet.isBlank()) return
            balances[wallet.trim()] = (balances[wallet.trim()] ?: 0.0) + value
        }
        for (t in transactions) {
            if (t.toWallet.isNotBlank()) {
                val moved = kotlin.math.abs(t.amount)
                add(t.wallet, -moved)
                add(t.toWallet, moved)
            } else {
                add(t.wallet, t.amount)
            }
        }
        return balances.toList().sortedByDescending { it.second }
    }

    fun totals(transactions: List<Transaction>): Totals = Totals(
        income = transactions.filter { it.isIncome }.sumOf { it.amount },
        expense = -transactions.filter { it.isExpense }.sumOf { it.amount },
    )

    fun inMonth(transactions: List<Transaction>, month: YearMonth): List<Transaction> =
        transactions.filter { YearMonth.from(it.dateTime) == month }

    fun inYear(transactions: List<Transaction>, year: Int): List<Transaction> =
        transactions.filter { it.dateTime.year == year }

    /** Spending grouped by main category, or by sub-category when [mainCategory] is given. */
    fun expensesByCategory(
        transactions: List<Transaction>,
        mainCategory: String? = null,
    ): List<CategoryTotal> = transactions
        .filter { it.isExpense && (mainCategory == null || it.mainCategory == mainCategory) }
        .groupBy { if (mainCategory == null) it.mainCategory else it.category }
        .map { (name, list) -> CategoryTotal(name.ifBlank { "—" }, -list.sumOf { it.amount }, list.size) }
        .sortedByDescending { it.amount }

    /** Income and spending for each of the [count] months ending at [last], oldest first. */
    fun monthlyTotals(
        transactions: List<Transaction>,
        last: YearMonth,
        count: Int,
    ): List<Pair<YearMonth, Totals>> {
        val byMonth = transactions.groupBy { YearMonth.from(it.dateTime) }
        return (count - 1 downTo 0).map { back ->
            val month = last.minusMonths(back.toLong())
            month to totals(byMonth[month].orEmpty())
        }
    }
}
