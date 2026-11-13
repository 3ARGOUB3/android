package com.nephrocare.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.nephrocare.core.BpCalc
import com.nephrocare.data.BpEntry
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun BpScreen(back: () -> Unit, go: (String) -> Unit) {
    val app = LocalApp.current
    val language = currentLanguage()
    val scope = rememberCoroutineScope()
    val entries by app.db.bp().observeAll().collectAsState(initial = emptyList())
    var sys by rememberSaveable { mutableStateOf("") }
    var dia by rememberSaveable { mutableStateOf("") }
    var pulse by rememberSaveable { mutableStateOf("") }
    var ctx by rememberSaveable { mutableStateOf("HOME") }
    var at by rememberSaveable { mutableStateOf(nowMinute()) }
    var period by rememberSaveable { mutableStateOf(30) }

    ScreenScaffold(stringResource(R.string.section_bp), back) { padding ->
        ScrollColumn(padding) {
            val last = entries.firstOrNull()
            if (last != null) {
                StatCard(
                    stringResource(R.string.home_last_bp), "${last.systolic}/${last.diastolic} mmHg",
                    subtitle = listOfNotNull(
                        last.pulse?.let { stringResource(R.string.bp_pulse_value, it) },
                        stringResource(bpContextLabel(last.context)),
                        Fmt.dateTime(last.time, language),
                    ).joinToString(" · "),
                    tint = Color(0xFFD84343),
                )
                if (BpCalc.isVeryHigh(last.systolic, last.diastolic)) {
                    WarningBanner(stringResource(R.string.bp_very_high), onClick = { go(Routes.EMERGENCY) })
                } else if (BpCalc.isLow(last.systolic)) {
                    WarningBanner(stringResource(R.string.bp_low_warning), onClick = { go(Routes.article("bp_low")) })
                }
            }

            SectionHeader(stringResource(R.string.bp_add))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(sys, { sys = it }, stringResource(R.string.bp_systolic), Modifier.weight(1f), decimal = false)
                NumberField(dia, { dia = it }, stringResource(R.string.bp_diastolic), Modifier.weight(1f), decimal = false)
            }
            NumberField(pulse, { pulse = it }, stringResource(R.string.bp_pulse), suffix = stringResource(R.string.unit_bpm), decimal = false)
            ChoiceChips(listOf("PRE", "POST", "HOME", "OTHER").map { it to stringResource(bpContextLabel(it)) }, ctx, { ctx = it })
            DateTimeField(at, { at = it })
            val s = parseInt(sys)
            val d = parseInt(dia)
            val valid = s != null && d != null && s in 50..280 && d in 30..180 && s > d
            Button(
                enabled = valid,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    if (s == null || d == null) return@Button
                    scope.launch {
                        app.db.bp().insert(BpEntry(time = at.toMillis(), systolic = s, diastolic = d, pulse = parseInt(pulse), context = ctx))
                        sys = ""; dia = ""; pulse = ""; at = nowMinute()
                    }
                },
            ) { Text(stringResource(R.string.save)) }

            SectionHeader(stringResource(R.string.chart_title))
            PeriodChips(period) { period = it }
            val visible = entries.filter { it.time >= sinceMillis(period) }
            LineChart(
                listOf(
                    ChartSeries(stringResource(R.string.bp_systolic), Color(0xFFD84343), visible.map { it.time to it.systolic.toDouble() }),
                    ChartSeries(stringResource(R.string.bp_diastolic), Blue, visible.map { it.time to it.diastolic.toDouble() }),
                ),
                decimals = 0,
            )
            val pulses = visible.mapNotNull { e -> e.pulse?.let { e.time to it.toDouble() } }
            if (pulses.size >= 2) {
                LineChart(listOf(ChartSeries(stringResource(R.string.bp_pulse), Green, pulses)), height = 150.dp, decimals = 0)
            }

            // Averages before and after dialysis
            val pre = visible.filter { it.context == "PRE" }
            val post = visible.filter { it.context == "POST" }
            if (pre.isNotEmpty() || post.isNotEmpty()) {
                SectionHeader(stringResource(R.string.bp_pre_post))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(stringResource(R.string.bp_ctx_pre), average(pre), Modifier.weight(1f), subtitle = stringResource(R.string.bp_readings, pre.size))
                    StatCard(stringResource(R.string.bp_ctx_post), average(post), Modifier.weight(1f), subtitle = stringResource(R.string.bp_readings, post.size), tint = Green)
                }
            }

            SectionHeader(stringResource(R.string.bp_learn))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { go(Routes.article("bp_high")) }, Modifier.weight(1f)) { Text(stringResource(R.string.bp_high_title), maxLines = 1) }
                OutlinedButton(onClick = { go(Routes.article("bp_low")) }, Modifier.weight(1f)) { Text(stringResource(R.string.bp_low_title), maxLines = 1) }
            }
            OutlinedButton(onClick = { go(Routes.article("bp_measure")) }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.bp_measure_title)) }

            SectionHeader(stringResource(R.string.history))
            if (entries.isEmpty()) EmptyState(stringResource(R.string.no_entries))
            entries.take(100).forEach { e ->
                HistoryRow(
                    "${e.systolic}/${e.diastolic}" + (e.pulse?.let { " · ♥ $it" } ?: ""),
                    "${stringResource(bpContextLabel(e.context))} · ${Fmt.dateTime(e.time, language)}",
                    accent = if (BpCalc.isVeryHigh(e.systolic, e.diastolic) || BpCalc.isLow(e.systolic)) Danger else Ok,
                    onDelete = { scope.launch { app.db.bp().delete(e) } },
                )
            }
            Text(stringResource(R.string.bp_disclaimer), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun average(list: List<BpEntry>): String =
    if (list.isEmpty()) "—"
    else "${list.map { it.systolic }.average().roundToInt()}/${list.map { it.diastolic }.average().roundToInt()}"
