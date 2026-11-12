package com.nephrocare.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nephrocare.R
import com.nephrocare.core.WeightCalc
import com.nephrocare.core.WeightKind
import com.nephrocare.core.WeightPoint
import com.nephrocare.core.fmt
import com.nephrocare.data.WeightEntry
import kotlinx.coroutines.launch

/** Period filter shared by the charts: number of days, 0 = everything. */
@Composable
fun PeriodChips(days: Int, onChange: (Int) -> Unit) {
    ChoiceChips(
        listOf(
            "7" to stringResource(R.string.period_week),
            "30" to stringResource(R.string.period_month),
            "90" to stringResource(R.string.period_3months),
            "0" to stringResource(R.string.period_all),
        ),
        days.toString(),
        { onChange(it.toInt()) },
    )
}

fun sinceMillis(days: Int): Long =
    if (days == 0) Long.MIN_VALUE else System.currentTimeMillis() - days * 24L * 3600 * 1000

@Composable
fun WeightScreen(back: () -> Unit) {
    val app = LocalApp.current
    val language = currentLanguage()
    val scope = rememberCoroutineScope()
    val entries by app.db.weights().observeAll().collectAsState(initial = emptyList())
    var kind by rememberSaveable { mutableStateOf("PRE") }
    var value by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var at by rememberSaveable { mutableStateOf(nowMinute()) }
    var period by rememberSaveable { mutableStateOf(30) }
    val kg = stringResource(R.string.unit_kg)

    val points = entries.map { WeightPoint(it.time, runCatching { WeightKind.valueOf(it.kind) }.getOrDefault(WeightKind.HOME), it.kg) }
    val dry = WeightCalc.dryWeight(points)
    val latest = WeightCalc.latestWeighing(points)
    val gains = WeightCalc.interdialyticGains(points)

    ScreenScaffold(stringResource(R.string.section_weight), back) { padding ->
        ScrollColumn(padding) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(stringResource(R.string.home_current_weight), latest?.let { "${fmt(it.kg, 1)} $kg" } ?: "—", Modifier.weight(1f),
                    subtitle = latest?.let { Fmt.dateTime(it.time, language) })
                StatCard(stringResource(R.string.home_dry_weight), dry?.let { "${fmt(it, 1)} $kg" } ?: "—", Modifier.weight(1f),
                    subtitle = stringResource(R.string.home_dry_by_team), tint = Green)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val diff = if (latest != null && dry != null) WeightCalc.aboveDry(latest.kg, dry) else null
                StatCard(stringResource(R.string.weight_above_dry), diff?.let { (if (it >= 0) "+" else "") + fmt(it, 1) + " $kg" } ?: "—", Modifier.weight(1f))
                val last = gains.lastOrNull()
                StatCard(
                    stringResource(R.string.weight_last_gain),
                    last?.let { "+${fmt(it.kg, 1)} $kg" } ?: "—",
                    Modifier.weight(1f),
                    subtitle = last?.percentOfDry?.let { stringResource(R.string.weight_percent_dry, fmt(it, 1)) },
                    tint = Warning,
                )
            }
            Banner(stringResource(R.string.weight_info))

            SectionHeader(stringResource(R.string.weight_add))
            ChoiceChips(
                listOf("PRE", "POST", "HOME", "DRY").map { it to stringResource(weightKindLabel(it)) },
                kind, { kind = it },
            )
            if (kind == "DRY") Banner(stringResource(R.string.weight_dry_note))
            NumberField(value, { value = it }, stringResource(R.string.weight_value), suffix = kg)
            DateTimeField(at, { at = it })
            TextInput(note, { note = it }, stringResource(R.string.note_optional))
            val parsed = parseDecimal(value)
            Button(
                enabled = parsed != null && parsed in 20.0..300.0,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val v = parsed ?: return@Button
                    scope.launch {
                        app.db.weights().insert(WeightEntry(time = at.toMillis(), kind = kind, kg = v, note = note.trim()))
                        value = ""; note = ""; at = nowMinute()
                    }
                },
            ) { Text(stringResource(R.string.save)) }

            SectionHeader(stringResource(R.string.chart_title))
            PeriodChips(period) { period = it }
            val since = sinceMillis(period)
            val visible = entries.filter { it.time >= since }
            val labels = listOf("PRE", "POST", "HOME").associateWith { stringResource(weightKindLabel(it)) }
            fun series(k: String, color: Color) = ChartSeries(
                labels.getValue(k), color,
                visible.filter { it.kind == k }.map { it.time to it.kg },
            )
            LineChart(
                listOf(series("PRE", Danger), series("POST", Green), series("HOME", Blue)).filter { it.points.isNotEmpty() },
                refLines = dry?.let { listOf(RefLine(it, Green, stringResource(R.string.home_dry_weight))) } ?: emptyList(),
            )
            val visibleGains = gains.filter { it.preTime >= since }
            if (visibleGains.size >= 2) {
                SectionHeader(stringResource(R.string.weight_gain_chart))
                LineChart(listOf(ChartSeries(stringResource(R.string.weight_gain_series), Warning, visibleGains.map { it.preTime to it.kg })))
            }

            SectionHeader(stringResource(R.string.history))
            if (entries.isEmpty()) EmptyState(stringResource(R.string.no_entries))
            entries.take(100).forEach { e ->
                HistoryRow(
                    "${fmt(e.kg, 1)} $kg · ${stringResource(weightKindLabel(e.kind))}",
                    Fmt.dateTime(e.time, language) + if (e.note.isNotBlank()) " · ${e.note}" else "",
                    accent = when (e.kind) { "DRY" -> Green; "PRE" -> Danger; "POST" -> Ok; else -> Blue },
                    onDelete = { scope.launch { app.db.weights().delete(e) } },
                )
            }
            Text(stringResource(R.string.disclaimer_short), style = MaterialTheme.typography.bodySmall)
        }
    }
}
