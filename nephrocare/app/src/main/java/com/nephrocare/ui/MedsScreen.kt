package com.nephrocare.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nephrocare.AppContainer
import com.nephrocare.R
import com.nephrocare.core.ScheduleCalc
import com.nephrocare.data.MedIntake
import com.nephrocare.data.Medication
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime

data class Dose(val med: Medication, val time: LocalTime, val scheduledAt: Long, val status: String?)

/** Today's planned doses of active medicines with what was recorded for each. */
fun todayDoses(meds: List<Medication>, intakes: List<MedIntake>): List<Dose> {
    val today = LocalDate.now()
    return meds.filter { it.active }.flatMap { med ->
        ScheduleCalc.parseTimes(med.times).map { t ->
            val at = today.atTime(t).toMillis()
            val status = intakes.firstOrNull { it.medId == med.id && it.scheduledAt == at }?.status
            Dose(med, t, at, status)
        }
    }.sortedBy { it.time }
}

suspend fun markDose(app: AppContainer, dose: Dose, status: String) {
    if (app.db.intakes().countFor(dose.med.id, dose.scheduledAt) > 0) return
    app.db.intakes().insert(
        MedIntake(
            medId = dose.med.id, medName = dose.med.name, dose = dose.med.dose,
            scheduledAt = dose.scheduledAt, takenAt = System.currentTimeMillis(), status = status,
        ),
    )
}

@Composable
fun MedsScreen(back: () -> Unit, go: (String) -> Unit) {
    val app = LocalApp.current
    val language = currentLanguage()
    val scope = rememberCoroutineScope()
    val meds by app.db.meds().observeAll().collectAsState(initial = emptyList())
    val intakes by app.db.intakes().observeAll().collectAsState(initial = emptyList())
    var tab by rememberSaveable { mutableIntStateOf(0) }

    ScreenScaffold(
        stringResource(R.string.section_meds), back,
        fab = {
            if (tab == 1) {
                ExtendedFloatingActionButton(
                    onClick = { go(Routes.med(0)) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text(stringResource(R.string.med_add)) },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            AppTabs(
                listOf(R.string.med_tab_today, R.string.med_tab_list, R.string.med_tab_history, R.string.med_tab_info).map { stringResource(it) },
                tab,
            ) { tab = it }
            when (tab) {
                0 -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Banner(stringResource(R.string.med_never_change)) }
                    val doses = todayDoses(meds, intakes)
                    if (doses.isEmpty()) item { EmptyState(stringResource(R.string.home_no_meds)) }
                    items(doses, key = { "${it.med.id}-${it.scheduledAt}" }) { dose ->
                        AppCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${Fmt.time(dose.time)} · ${dose.med.name}", style = MaterialTheme.typography.titleMedium)
                                    Text(dose.med.dose, style = MaterialTheme.typography.bodyMedium)
                                    if (dose.med.instructions.isNotBlank()) {
                                        Text(dose.med.instructions, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                when (dose.status) {
                                    "TAKEN" -> Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.med_taken), tint = Ok)
                                    "SKIPPED" -> Text(stringResource(R.string.med_skipped), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            if (dose.status == null) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { scope.launch { markDose(app, dose, "TAKEN") } }) {
                                        Icon(Icons.Filled.CheckCircle, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.action_taken))
                                    }
                                    OutlinedButton(onClick = { scope.launch { markDose(app, dose, "SKIPPED") } }) {
                                        Icon(Icons.Filled.Close, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text(stringResource(R.string.action_skip))
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (meds.isEmpty()) item { EmptyState(stringResource(R.string.med_empty)) }
                    items(meds, key = { it.id }) { med ->
                        AppCard(onClick = { go(Routes.med(med.id)) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(med.name, style = MaterialTheme.typography.titleMedium)
                                    Text(listOf(med.dose, med.form).filter { it.isNotBlank() }.joinToString(" · "))
                                    val times = ScheduleCalc.parseTimes(med.times)
                                    if (times.isNotEmpty()) Text(times.joinToString("  ") { Fmt.time(it) }, color = MaterialTheme.colorScheme.primary)
                                    if (!med.active) Text(stringResource(R.string.med_inactive), color = MaterialTheme.colorScheme.error)
                                }
                                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.edit))
                            }
                        }
                    }
                    item { Spacer(Modifier.padding(36.dp)) }
                }
                2 -> LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (intakes.isEmpty()) item { EmptyState(stringResource(R.string.med_history_empty)) }
                    items(intakes, key = { it.id }) { intake ->
                        HistoryRow(
                            "${intake.medName} · ${intake.dose}",
                            Fmt.dateTime(intake.takenAt, language) + " · " +
                                stringResource(if (intake.status == "TAKEN") R.string.med_taken else R.string.med_skipped),
                            accent = if (intake.status == "TAKEN") Ok else Warning,
                            onDelete = { scope.launch { app.db.intakes().delete(intake) } },
                        )
                    }
                }
                else -> ArticleLinks("meds", go, Modifier.fillMaxSize())
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MedEditScreen(id: Long, back: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var loaded by remember { mutableStateOf(id == 0L) }
    var existing by remember { mutableStateOf<Medication?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var dose by rememberSaveable { mutableStateOf("") }
    var form by rememberSaveable { mutableStateOf("") }
    var times by rememberSaveable { mutableStateOf("") }
    var instructions by rememberSaveable { mutableStateOf("") }
    var active by rememberSaveable { mutableStateOf(true) }
    var reminders by rememberSaveable { mutableStateOf(true) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != 0L && !loaded) {
            app.db.meds().byId(id)?.let { m ->
                existing = m
                name = m.name; dose = m.dose; form = m.form; times = m.times
                instructions = m.instructions; active = m.active; reminders = m.reminders
            }
            loaded = true
        }
    }

    ScreenScaffold(
        stringResource(if (id == 0L) R.string.med_add else R.string.med_edit), back,
        actions = {
            if (existing != null) {
                androidx.compose.material3.IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete))
                }
            }
        },
    ) { padding ->
        ScrollColumn(padding) {
            Banner(stringResource(R.string.med_dose_as_prescribed))
            TextInput(name, { name = it }, stringResource(R.string.med_name))
            TextInput(dose, { dose = it }, stringResource(R.string.med_dose))
            TextInput(form, { form = it }, stringResource(R.string.med_form))
            TextInput(instructions, { instructions = it }, stringResource(R.string.med_instructions), singleLine = false)

            SectionHeader(stringResource(R.string.med_times))
            val parsed = ScheduleCalc.parseTimes(times)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                parsed.forEach { t ->
                    InputChip(
                        selected = false,
                        onClick = { times = ScheduleCalc.formatTimes(parsed - t) },
                        label = { Text(Fmt.time(t)) },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.delete)) },
                    )
                }
            }
            TimeButton(LocalTime.of(8, 0)) { picked -> times = ScheduleCalc.formatTimes(parsed + picked) }
            Text(stringResource(R.string.med_add_time_hint), style = MaterialTheme.typography.bodySmall)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.med_reminders), modifier = Modifier.weight(1f))
                Switch(checked = reminders, onCheckedChange = { reminders = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.med_active), modifier = Modifier.weight(1f))
                Switch(checked = active, onCheckedChange = { active = it })
            }
            Button(
                enabled = name.isNotBlank() && dose.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        val med = Medication(
                            id = existing?.id ?: 0, name = name.trim(), dose = dose.trim(), form = form.trim(),
                            times = times, instructions = instructions.trim(), active = active, reminders = reminders,
                            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                        )
                        if (existing == null) app.db.meds().insert(med) else app.db.meds().update(med)
                        app.rescheduleReminders()
                        back()
                    }
                },
            ) { Text(stringResource(R.string.save)) }
        }
    }
    if (confirmDelete) {
        ConfirmDialog(
            stringResource(R.string.delete_title), stringResource(R.string.delete_text), stringResource(R.string.delete),
            onConfirm = {
                scope.launch {
                    existing?.let { app.db.meds().delete(it) }
                    app.rescheduleReminders()
                    back()
                }
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** Lists the articles of a category as cards. */
@Composable
fun ArticleLinks(category: String, go: (String) -> Unit, modifier: Modifier = Modifier) {
    val app = LocalApp.current
    val language = currentLanguage()
    val articles = remember(language, category) { app.content.articles(language, category) }
    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(articles, key = { it.id }) { a ->
            AppCard(onClick = { go(Routes.article(a.id)) }) {
                Text(a.title, style = MaterialTheme.typography.titleMedium)
                if (a.summary.isNotBlank()) Text(a.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item { DisclaimerBanner() }
    }
}
