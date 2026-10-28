package com.masroufi.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.masroufi.R
import com.masroufi.core.Transaction
import java.time.LocalDate

private sealed interface Entry {
    data class Day(val date: LocalDate, val net: Double) : Entry
    data class Item(val tx: Transaction) : Entry
}

@Composable
fun TransactionsScreen(transactions: List<Transaction>) {
    var query by rememberSaveable { mutableStateOf("") }

    val filtered = remember(transactions, query) {
        val q = query.trim()
        if (q.isEmpty()) {
            transactions
        } else {
            transactions.filter { t ->
                listOf(t.note, t.category, t.mainCategory, t.wallet, t.toWallet, t.contact, t.group)
                    .any { it.contains(q, ignoreCase = true) }
            }
        }
    }
    val entries = remember(filtered) {
        filtered.groupBy { it.dateTime.toLocalDate() }.flatMap { (day, list) ->
            val net = list.filter { !it.isTransfer }.sumOf { it.amount }
            listOf<Entry>(Entry.Day(day, net)) + list.map { Entry.Item(it) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text(stringResource(R.string.search)) },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.clear))
                    }
                }
            },
            singleLine = true,
        )
        Text(
            stringResource(R.string.results, filtered.size),
            modifier = Modifier.padding(horizontal = 16.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(Modifier.fillMaxSize()) {
            items(entries, contentType = { it::class }) { entry ->
                when (entry) {
                    is Entry.Day -> DayHeader(entry)
                    is Entry.Item -> TransactionRow(entry.tx)
                }
            }
        }
    }
}

@Composable
private fun DayHeader(day: Entry.Day) {
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                day.date.format(dayFormatter),
                Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
            )
            if (day.net != 0.0) {
                Text(
                    formatSigned(day.net),
                    style = MaterialTheme.typography.labelLarge,
                    color = amountColor(day.net),
                )
            }
        }
    }
}

@Composable
private fun TransactionRow(t: Transaction) {
    val title = t.category.ifBlank { t.mainCategory }.ifBlank { "—" }
    val wallets = if (t.toWallet.isNotBlank()) "${t.wallet} → ${t.toWallet}" else t.wallet
    val details = listOf(
        t.mainCategory.takeIf { it != title },
        t.note,
        t.contact,
        wallets,
    ).filter { !it.isNullOrBlank() }.joinToString(" · ")

    ListItem(
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(details, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (t.isTransfer) formatMoney(t.amount, t.currency) else formatSigned(t.amount, t.currency),
                    color = if (t.isTransfer) MaterialTheme.colorScheme.onSurfaceVariant else amountColor(t.amount),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    if (t.isTransfer) stringResource(R.string.transfer) else t.dateTime.format(timeFormatter),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
    )
    HorizontalDivider()
}
