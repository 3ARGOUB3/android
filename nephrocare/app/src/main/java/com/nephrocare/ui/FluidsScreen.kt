package com.nephrocare.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
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
import com.nephrocare.data.FluidEntry
import kotlinx.coroutines.launch
import java.time.LocalDate

private val FluidBlue = Color(0xFF2B8AD9)

fun fluidKindLabel(kind: String): Int = when (kind) {
    "HOT" -> R.string.fluid_hot
    "SOUP" -> R.string.fluid_soup
    "MILK" -> R.string.fluid_milk
    "OTHER" -> R.string.fluid_other
    else -> R.string.fluid_water
}

@Composable
fun FluidsScreen(back: () -> Unit, go: (String) -> Unit) {
    val app = LocalApp.current
    val language = currentLanguage()
    val scope = rememberCoroutineScope()
    val entries by app.db.fluids().observeAll().collectAsState(initial = emptyList())
    val profile by app.settings.profile.collectAsState()
    var kind by rememberSaveable { mutableStateOf("WATER") }
    var custom by rememberSaveable { mutableStateOf("") }
    var target by rememberSaveable(profile.fluidTargetMl) { mutableStateOf(profile.fluidTargetMl?.toString() ?: "") }

    fun add(ml: Int) {
        scope.launch { app.db.fluids().insert(FluidEntry(time = System.currentTimeMillis(), ml = ml, kind = kind)) }
    }

    ScreenScaffold(stringResource(R.string.section_fluids), back) { padding ->
        ScrollColumn(padding) {
            val today = LocalDate.now()
            val todayEntries = entries.filter { it.time.toLocalDate() == today }
            val total = todayEntries.sumOf { it.ml }
            val goal = profile.fluidTargetMl
            StatCard(
                stringResource(R.string.home_fluids_today),
                if (goal != null) stringResource(R.string.fluids_of_target, total, goal) else stringResource(R.string.fluids_ml, total),
                tint = FluidBlue,
                subtitle = goal?.let {
                    if (total > it) stringResource(R.string.fluids_over, total - it) else stringResource(R.string.fluids_left, it - total)
                },
            )
            if (goal != null && goal > 0) {
                LinearProgressIndicator(
                    progress = { (total.toFloat() / goal).coerceIn(0f, 1f) },
                    color = if (total > goal) Danger else FluidBlue,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionHeader(stringResource(R.string.fluids_add))
            ChoiceChips(listOf("WATER", "HOT", "SOUP", "MILK", "OTHER").map { it to stringResource(fluidKindLabel(it)) }, kind, { kind = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(50, 100, 150, 200, 250).forEach { ml ->
                    FilledTonalButton(onClick = { add(ml) }, modifier = Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) {
                        Text("$ml")
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(custom, { custom = it }, stringResource(R.string.fluids_custom), Modifier.weight(1f), suffix = stringResource(R.string.unit_ml), decimal = false)
                val ml = parseInt(custom)
                Button(enabled = ml != null && ml in 1..3000, onClick = { ml?.let { add(it); custom = "" } }) { Text(stringResource(R.string.add)) }
            }
            Banner(stringResource(R.string.fluids_hidden_tip))

            SectionHeader(stringResource(R.string.fluids_week))
            val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
            BarChart(
                days.map { d -> Fmt.dayName(d.dayOfWeek, language, short = true) to entries.filter { it.time.toLocalDate() == d }.sumOf { it.ml }.toDouble() },
                FluidBlue,
                target = goal?.toDouble(),
            )
            val weekTotal = entries.filter { it.time.toLocalDate() >= today.minusDays(6) }.sumOf { it.ml }
            Text(stringResource(R.string.fluids_week_avg, weekTotal / 7), style = MaterialTheme.typography.bodyLarge)

            SectionHeader(stringResource(R.string.fluids_target))
            Text(stringResource(R.string.fluids_target_hint), style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(target, { target = it }, stringResource(R.string.fluids_target), Modifier.weight(1f), suffix = stringResource(R.string.unit_ml), decimal = false)
                OutlinedButton(onClick = {
                    val v = parseInt(target)?.takeIf { it in 100..5000 }
                    app.settings.updateProfile { it.copy(fluidTargetMl = v) }
                }) { Text(stringResource(R.string.save)) }
            }

            SectionHeader(stringResource(R.string.fluids_today_list))
            if (todayEntries.isEmpty()) EmptyState(stringResource(R.string.no_entries))
            todayEntries.forEach { e ->
                HistoryRow(
                    "${e.ml} ${stringResource(R.string.unit_ml)} · ${stringResource(fluidKindLabel(e.kind))}",
                    Fmt.time(e.time),
                    accent = FluidBlue,
                    onDelete = { scope.launch { app.db.fluids().delete(e) } },
                )
            }
            androidx.compose.material3.TextButton(onClick = { go(Routes.article("nut_fluids")) }) { Text(stringResource(R.string.fluids_learn)) }
        }
    }
}
