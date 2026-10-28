package com.masroufi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.masroufi.R
import com.masroufi.core.Finance
import com.masroufi.core.Transaction
import java.time.YearMonth

@Composable
fun OverviewScreen(transactions: List<Transaction>) {
    val balances = remember(transactions) { Finance.walletBalances(transactions) }
    // The most recent month that has data (transactions are sorted newest first).
    val month = remember(transactions) { YearMonth.from(transactions.first().dateTime) }
    val monthTx = remember(transactions, month) { Finance.inMonth(transactions, month) }
    val totals = remember(monthTx) { Finance.totals(monthTx) }
    val top = remember(monthTx) { Finance.expensesByCategory(monthTx).take(5) }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text(stringResource(R.string.total_balance), style = MaterialTheme.typography.titleSmall)
                    Text(
                        formatMoney(balances.sumOf { it.second }),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                }
            }
        }
        item { TotalsCard(formatMonth(month), totals) }

        if (top.isNotEmpty()) {
            item { SectionTitle(stringResource(R.string.top_categories)) }
            items(top) { CategoryRow(it, max = top.first().amount, total = totals.expense) }
        }

        item { SectionTitle(stringResource(R.string.wallets)) }
        items(balances) { (wallet, balance) ->
            Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(wallet, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatMoney(balance), color = amountColor(balance))
            }
        }
    }
}
