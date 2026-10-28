package com.masroufi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.masroufi.R
import com.masroufi.core.CategoryTotal
import com.masroufi.core.Totals
import java.time.YearMonth

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier.padding(top = 8.dp),
    )
}

@Composable
fun TotalsCard(title: String, totals: Totals, modifier: Modifier = Modifier) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            AmountLine(stringResource(R.string.income), totals.income, incomeColor)
            AmountLine(stringResource(R.string.expenses), totals.expense, expenseColor)
            AmountLine(stringResource(R.string.net), totals.net, amountColor(totals.net), bold = true)
        }
    }
}

@Composable
fun AmountLine(label: String, amount: Double, color: Color, bold: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(
            formatMoney(amount),
            color = color,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
fun CategoryRow(
    item: CategoryTotal,
    max: Double,
    total: Double,
    onClick: (() -> Unit)? = null,
) {
    val share = if (total > 0) (item.amount / total * 100).toInt() else 0
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.name,
                Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "$share%  ${formatMoney(item.amount)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        LinearProgressIndicator(
            progress = { if (max > 0) (item.amount / max).toFloat() else 0f },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            color = expenseColor,
        )
    }
}

/** Side-by-side income and spending bars for each month. */
@Composable
fun MonthlyChart(data: List<Pair<YearMonth, Totals>>, modifier: Modifier = Modifier) {
    if (data.isEmpty()) return
    val income = incomeColor
    val expense = expenseColor
    val max = data.maxOf { maxOf(it.second.income, it.second.expense) }.coerceAtLeast(1.0)

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(160.dp)
        ) {
            val slot = size.width / data.size
            val bar = slot * 0.35f
            data.forEachIndexed { i, (_, totals) ->
                // Keep bars under their labels when the layout is right-to-left.
                val index = if (layoutDirection == LayoutDirection.Rtl) data.size - 1 - i else i
                val x = slot * index + slot * 0.15f
                val hi = (totals.income / max * size.height).toFloat()
                val he = (totals.expense / max * size.height).toFloat()
                drawRect(income, Offset(x, size.height - hi), Size(bar, hi))
                drawRect(expense, Offset(x + bar, size.height - he), Size(bar, he))
            }
        }
        Row(Modifier.fillMaxWidth()) {
            data.forEach { (month, _) ->
                Text(
                    month.monthValue.toString(),
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        Row(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Legend(stringResource(R.string.income), income)
            Legend(stringResource(R.string.expenses), expense)
        }
    }
}

@Composable
private fun Legend(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(
            Modifier
                .size(10.dp)
                .background(color, CircleShape)
        )
        Text(label, Modifier.padding(start = 6.dp), style = MaterialTheme.typography.labelMedium)
    }
}
