package com.masroufi.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.masroufi.R
import com.masroufi.core.Finance
import com.masroufi.core.Transaction
import java.time.YearMonth

@Composable
fun ReportsScreen(transactions: List<Transaction>) {
    val latest = remember(transactions) { YearMonth.from(transactions.first().dateTime) }
    val earliest = remember(transactions) { YearMonth.from(transactions.last().dateTime) }

    // Saved as strings/booleans so they survive rotation.
    var monthText by rememberSaveable(latest) { mutableStateOf(latest.toString()) }
    var yearly by rememberSaveable { mutableStateOf(false) }
    var openCategory by rememberSaveable { mutableStateOf<String?>(null) }
    val month = YearMonth.parse(monthText)

    val periodTx = remember(transactions, month, yearly) {
        if (yearly) Finance.inYear(transactions, month.year) else Finance.inMonth(transactions, month)
    }
    val totals = remember(periodTx) { Finance.totals(periodTx) }
    val categories = remember(periodTx, openCategory) { Finance.expensesByCategory(periodTx, openCategory) }
    val chart = remember(transactions, month) { Finance.monthlyTotals(transactions, month, 12) }

    val step = if (yearly) 12L else 1L
    val canGoBack = if (yearly) month.year > earliest.year else month > earliest
    val canGoForward = if (yearly) month.year < latest.year else month < latest
    val title = if (yearly) month.year.toString() else formatMonth(month)

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = !yearly,
                    onClick = { yearly = false; openCategory = null },
                    label = { Text(stringResource(R.string.month)) },
                )
                FilterChip(
                    selected = yearly,
                    onClick = { yearly = true; openCategory = null },
                    label = { Text(stringResource(R.string.year)) },
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { monthText = month.minusMonths(step).toString(); openCategory = null },
                    enabled = canGoBack,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.previous))
                }
                Text(
                    title,
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(
                    onClick = { monthText = month.plusMonths(step).toString(); openCategory = null },
                    enabled = canGoForward,
                ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next))
                }
            }
        }
        item { TotalsCard(title, totals) }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle(openCategory ?: stringResource(R.string.by_category), Modifier.weight(1f))
                if (openCategory != null) {
                    TextButton(onClick = { openCategory = null }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Text(stringResource(R.string.all_categories))
                    }
                }
            }
        }
        if (categories.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.no_expenses),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val categoryTotal = categories.sumOf { it.amount }
            items(categories) { item ->
                CategoryRow(
                    item,
                    max = categories.first().amount,
                    total = categoryTotal,
                    onClick = if (openCategory == null) ({ openCategory = item.name }) else null,
                )
            }
        }

        item { SectionTitle(stringResource(R.string.last_12_months)) }
        item { MonthlyChart(chart) }
    }
}
