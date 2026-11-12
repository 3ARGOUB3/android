package com.nephrocare.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nephrocare.R
import com.nephrocare.core.ScheduleCalc
import com.nephrocare.core.fmt
import com.nephrocare.data.DialysisSession
import com.nephrocare.data.WeightEntry
import androidx.room.withTransaction
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

@Composable
fun ScheduleScreen(back: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    ScreenScaffold(stringResource(R.string.section_schedule), back) { padding ->
        Column(Modifier.fillMaxWidth().padding(padding)) {
            AppTabs(
                listOf(R.string.sched_tab_days, R.string.sched_tab_sessions, R.string.sched_tab_prep).map { stringResource(it) },
                tab,
            ) { tab = it }
            when (tab) {
                0 -> ScheduleSettings()
                1 -> SessionsLog()
                else -> PrepChecklist()
            }
        }
    }
}

@Composable
private fun ScheduleSettings() {
    val app = LocalApp.current
    val language = currentLanguage()
    val s by app.settings.schedule.collectAsState()
    fun update(f: (com.nephrocare.data.DialysisSchedule) -> com.nephrocare.data.DialysisSchedule) {
        app.settings.updateSchedule(f)
        app.rescheduleReminders()
    }
    ScrollColumn(androidx.compose.foundation.layout.PaddingValues()) {
        val days = s.days.mapNotNull { runCatching { DayOfWeek.of(it) }.getOrNull() }.toSet()
        val next = ScheduleCalc.nextSession(days, LocalTime.of(s.hour, s.minute), LocalDateTime.now())
        StatCard(
            stringResource(R.string.home_next_session),
            next?.let { Fmt.fullDateTime(it, language) } ?: "—",
            subtitle = if (next == null) stringResource(R.string.home_set_schedule) else null,
            tint = BlueDark,
        )
        SectionHeader(stringResource(R.string.sched_days))
        MultiChoiceChips(
            DayOfWeek.entries.map { it.value.toString() to Fmt.dayName(it, language) },
            s.days.map { it.toString() }.toSet(),
            { key ->
                val d = key.toInt()
                update { it.copy(days = if (d in it.days) it.days - d else (it.days + d).sorted()) }
            },
        )
        SectionHeader(stringResource(R.string.sched_time))
        TimeButton(LocalTime.of(s.hour, s.minute), Modifier.fillMaxWidth()) { t -> update { it.copy(hour = t.hour, minute = t.minute) } }
        ChoiceChips(
            listOf("180", "210", "240", "270", "300").map { it to stringResource(R.string.sched_hours, fmt(it.toInt() / 60.0, 1)) },
            s.durationMin.toString(),
            { v -> update { it.copy(durationMin = v.toInt()) } },
        )

        SectionHeader(stringResource(R.string.sched_reminders))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.sched_reminder_before), Modifier.weight(1f))
            Switch(s.reminderEnabled, { v -> update { it.copy(reminderEnabled = v) } })
        }
        if (s.reminderEnabled) {
            ChoiceChips(
                listOf("30", "60", "90", "120").map { it to stringResource(R.string.sched_minutes, it.toInt()) },
                s.reminderMinutesBefore.toString(),
                { v -> update { it.copy(reminderMinutesBefore = v.toInt()) } },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.sched_prep_reminder), Modifier.weight(1f))
            Switch(s.prepReminderEnabled, { v -> update { it.copy(prepReminderEnabled = v) } })
        }
        if (s.prepReminderEnabled) {
            TimeButton(LocalTime.of(s.prepHour, s.prepMinute), Modifier.fillMaxWidth()) { t -> update { it.copy(prepHour = t.hour, prepMinute = t.minute) } }
        }
        Banner(stringResource(R.string.sched_info))
    }
}

@Composable
private fun SessionsLog() {
    val app = LocalApp.current
    val language = currentLanguage()
    val scope = rememberCoroutineScope()
    val sessions by app.db.sessions().observeAll().collectAsState(initial = emptyList())
    var at by rememberSaveable { mutableStateOf(nowMinute()) }
    var pre by rememberSaveable { mutableStateOf("") }
    var post by rememberSaveable { mutableStateOf("") }
    var preSys by rememberSaveable { mutableStateOf("") }
    var preDia by rememberSaveable { mutableStateOf("") }
    var postSys by rememberSaveable { mutableStateOf("") }
    var postDia by rememberSaveable { mutableStateOf("") }
    var duration by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    val kg = stringResource(R.string.unit_kg)

    ScrollColumn(androidx.compose.foundation.layout.PaddingValues()) {
        SectionHeader(stringResource(R.string.session_add))
        DateTimeField(at, { at = it })
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(pre, { pre = it }, stringResource(R.string.weight_kind_pre), Modifier.weight(1f), suffix = kg)
            NumberField(post, { post = it }, stringResource(R.string.weight_kind_post), Modifier.weight(1f), suffix = kg)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(preSys, { preSys = it }, stringResource(R.string.session_pre_sys), Modifier.weight(1f), decimal = false)
            NumberField(preDia, { preDia = it }, stringResource(R.string.session_pre_dia), Modifier.weight(1f), decimal = false)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField(postSys, { postSys = it }, stringResource(R.string.session_post_sys), Modifier.weight(1f), decimal = false)
            NumberField(postDia, { postDia = it }, stringResource(R.string.session_post_dia), Modifier.weight(1f), decimal = false)
        }
        NumberField(duration, { duration = it }, stringResource(R.string.session_duration), suffix = stringResource(R.string.unit_min), decimal = false)
        TextInput(note, { note = it }, stringResource(R.string.note_optional), singleLine = false)
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    val preKg = parseDecimal(pre)?.takeIf { it in 20.0..300.0 }
                    val postKg = parseDecimal(post)?.takeIf { it in 20.0..300.0 }
                    val start = at.toMillis()
                    val dur = parseInt(duration)
                    val session = DialysisSession(
                        date = start, durationMin = dur, preWeight = preKg, postWeight = postKg,
                        preSystolic = parseInt(preSys), preDiastolic = parseInt(preDia),
                        postSystolic = parseInt(postSys), postDiastolic = parseInt(postDia), note = note.trim(),
                    )
                    app.db.withTransaction {
                        val id = app.db.sessions().insert(session)
                        // The weights also feed the weight charts and the interdialytic gain.
                        preKg?.let { app.db.weights().insert(WeightEntry(time = start, kind = "PRE", kg = it, sessionId = id)) }
                        postKg?.let {
                            val end = start + (dur ?: 240) * 60_000L
                            app.db.weights().insert(WeightEntry(time = end, kind = "POST", kg = it, sessionId = id))
                        }
                        val s = session.preSystolic; val d = session.preDiastolic
                        if (s != null && d != null) app.db.bp().insert(com.nephrocare.data.BpEntry(time = start, systolic = s, diastolic = d, context = "PRE"))
                        val s2 = session.postSystolic; val d2 = session.postDiastolic
                        if (s2 != null && d2 != null) app.db.bp().insert(
                            com.nephrocare.data.BpEntry(time = start + (dur ?: 240) * 60_000L, systolic = s2, diastolic = d2, context = "POST"),
                        )
                    }
                    pre = ""; post = ""; preSys = ""; preDia = ""; postSys = ""; postDia = ""; duration = ""; note = ""
                    at = nowMinute()
                }
            },
        ) { Text(stringResource(R.string.save)) }

        SectionHeader(stringResource(R.string.history))
        if (sessions.isEmpty()) EmptyState(stringResource(R.string.no_entries))
        sessions.forEach { s ->
            val removed = if (s.preWeight != null && s.postWeight != null) " · −${fmt(s.preWeight - s.postWeight, 1)} $kg" else ""
            HistoryRow(
                Fmt.dateTime(s.date, language) + removed,
                listOfNotNull(
                    s.preWeight?.let { "${stringResource(R.string.weight_kind_pre)} ${fmt(it, 1)}" },
                    s.postWeight?.let { "${stringResource(R.string.weight_kind_post)} ${fmt(it, 1)}" },
                    if (s.preSystolic != null && s.preDiastolic != null) "${stringResource(R.string.bp_ctx_pre)} ${s.preSystolic}/${s.preDiastolic}" else null,
                    if (s.postSystolic != null && s.postDiastolic != null) "${stringResource(R.string.bp_ctx_post)} ${s.postSystolic}/${s.postDiastolic}" else null,
                    s.durationMin?.let { "$it ${stringResource(R.string.unit_min)}" },
                    s.note.ifBlank { null },
                ).joinToString(" · "),
                onDelete = {
                    scope.launch {
                        app.db.withTransaction {
                            app.db.weights().deleteForSession(s.id)
                            app.db.sessions().delete(s)
                        }
                    }
                },
            )
        }
    }
}

@Composable
private fun PrepChecklist() {
    val items = stringArrayResource(R.array.prep_items)
    var checked by rememberSaveable { mutableStateOf(setOf<Int>()) }
    ScrollColumn(androidx.compose.foundation.layout.PaddingValues()) {
        Text(stringResource(R.string.prep_intro), style = MaterialTheme.typography.bodyLarge)
        items.forEachIndexed { i, label ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(i in checked, { checked = if (it) checked + i else checked - i })
                Text(label, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Button(onClick = { checked = emptySet() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.prep_reset)) }
    }
}
