package com.nephrocare.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nephrocare.R
import com.nephrocare.core.LabStatus
import com.nephrocare.core.LabTest
import com.nephrocare.core.Labs
import com.nephrocare.core.fmt
import com.nephrocare.data.LabResult
import kotlinx.coroutines.launch

fun statusColor(status: LabStatus): Color = when (status) {
    LabStatus.LOW -> Warning
    LabStatus.HIGH -> Danger
    LabStatus.IN_RANGE -> Ok
    LabStatus.NOT_FLAGGED -> Blue
}

@Composable
fun statusLabel(status: LabStatus): String = stringResource(
    when (status) {
        LabStatus.LOW -> R.string.lab_status_low
        LabStatus.HIGH -> R.string.lab_status_high
        LabStatus.IN_RANGE -> R.string.lab_status_in
        LabStatus.NOT_FLAGGED -> R.string.lab_status_none
    },
)

@Composable
fun LabsScreen(back: () -> Unit, go: (String) -> Unit) {
    val app = LocalApp.current
    val language = currentLanguage()
    val results by app.db.labs().observeAll().collectAsState(initial = emptyList())
    val texts = app.content.pack(language).labs.associateBy { it.code }

    ScreenScaffold(stringResource(R.string.section_labs), back) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Banner(stringResource(R.string.lab_ranges_vary)) }
            items(Labs.all, key = { it.code }) { test ->
                val latest = results.firstOrNull { it.code == test.code }
                AppCard(onClick = { go(Routes.lab(test.code)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(test.name, style = MaterialTheme.typography.titleMedium)
                            texts[test.code]?.let {
                                Text(it.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (latest != null) {
                                val main = test.toMain(latest.value, latest.unit)
                                val status = Labs.status(test, main)
                                Text(
                                    "${fmt(latest.value)} ${latest.unit} · ${Fmt.date(latest.date, language)}",
                                    color = statusColor(status), fontWeight = FontWeight.SemiBold,
                                )
                            } else {
                                Text(stringResource(R.string.lab_no_result), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }
            }
            item { DisclaimerBanner() }
        }
    }
}

@Composable
fun LabDetailScreen(code: String, back: () -> Unit) {
    val app = LocalApp.current
    val language = currentLanguage()
    val scope = rememberCoroutineScope()
    val test: LabTest = Labs.byCode(code) ?: Labs.all.first()
    val text = app.content.pack(language).labs.firstOrNull { it.code == test.code }
    val all by app.db.labs().observeAll().collectAsState(initial = emptyList())
    val results = all.filter { it.code == test.code }
    var value by rememberSaveable { mutableStateOf("") }
    var unit by rememberSaveable(code) { mutableStateOf(test.mainUnit) }
    var at by rememberSaveable { mutableStateOf(nowMinute()) }
    var note by rememberSaveable { mutableStateOf("") }

    ScreenScaffold(test.name, back) { padding ->
        ScrollColumn(padding) {
            if (text != null) {
                Text(text.title, style = MaterialTheme.typography.titleLarge)
                Text(text.what, style = MaterialTheme.typography.bodyLarge)
            }
            AppCard {
                Text(stringResource(R.string.lab_reference), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.lab_general_range, test.general.label(), test.mainUnit))
                test.dialysis?.let { Text(stringResource(R.string.lab_dialysis_range, it.label(), test.mainUnit), color = MaterialTheme.colorScheme.primary) }
                Text(stringResource(R.string.lab_ranges_vary), style = MaterialTheme.typography.bodySmall)
            }

            SectionHeader(stringResource(R.string.lab_add))
            NumberField(value, { value = it }, stringResource(R.string.lab_value))
            ChoiceChips(test.units.map { it.symbol to it.symbol }, unit, { unit = it })
            DateTimeField(at, { at = it }, showTime = false)
            TextInput(note, { note = it }, stringResource(R.string.note_optional))
            val parsed = parseDecimal(value)
            Button(
                enabled = parsed != null && parsed >= 0,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val v = parsed ?: return@Button
                    scope.launch {
                        app.db.labs().insert(LabResult(code = test.code, value = v, unit = unit, date = at.toMillis(), note = note.trim()))
                        value = ""; note = ""
                    }
                },
            ) { Text(stringResource(R.string.save)) }

            SectionHeader(stringResource(R.string.chart_title))
            val refs = (test.dialysis ?: test.general).let { r ->
                listOfNotNull(
                    r.low?.let { RefLine(it, Ok) },
                    r.high?.let { RefLine(it, Danger) },
                )
            }
            LineChart(
                listOf(ChartSeries("${test.name} (${test.mainUnit})", Teal, results.map { it.date to test.toMain(it.value, it.unit) })),
                refLines = if (test.flagged) refs else emptyList(),
            )

            SectionHeader(stringResource(R.string.history))
            if (results.isEmpty()) EmptyState(stringResource(R.string.no_entries))
            results.forEach { r ->
                val status = Labs.status(test, test.toMain(r.value, r.unit))
                HistoryRow(
                    "${fmt(r.value)} ${r.unit}" + if (status != LabStatus.NOT_FLAGGED) " · ${statusLabel(status)}" else "",
                    Fmt.date(r.date, language) + if (r.note.isNotBlank()) " · ${r.note}" else "",
                    accent = statusColor(status),
                    onDelete = { scope.launch { app.db.labs().delete(r) } },
                )
            }

            if (text != null) {
                SectionHeader(stringResource(R.string.lab_if_high))
                Text(text.high, style = MaterialTheme.typography.bodyLarge)
                SectionHeader(stringResource(R.string.lab_if_low))
                Text(text.low, style = MaterialTheme.typography.bodyLarge)
                if (text.tips.isNotBlank()) {
                    SectionHeader(stringResource(R.string.lab_tips))
                    Text(text.tips, style = MaterialTheme.typography.bodyLarge)
                }
            }
            DisclaimerBanner()
        }
    }
}
