package com.nephrocare.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nephrocare.R
import com.nephrocare.core.fmt
import com.nephrocare.data.DiaryEntry
import com.nephrocare.data.DoctorQuestion
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

val SYMPTOMS = listOf(
    "ITCH" to R.string.sym_itch,
    "CRAMPS" to R.string.sym_cramps,
    "FATIGUE" to R.string.sym_fatigue,
    "NAUSEA" to R.string.sym_nausea,
    "BREATH" to R.string.sym_breath,
    "SWELLING" to R.string.sym_swelling,
    "HEADACHE" to R.string.sym_headache,
    "DIZZY" to R.string.sym_dizzy,
    "PAIN" to R.string.sym_pain,
    "INSOMNIA" to R.string.sym_insomnia,
    "RESTLESS_LEGS" to R.string.sym_restless_legs,
    "LOW_MOOD" to R.string.sym_low_mood,
    "CHEST_PAIN" to R.string.sym_chest_pain,
)

@Composable
fun symptomLabels(codes: String): String {
    val map = SYMPTOMS.toMap()
    return codes.split(',').filter { it.isNotBlank() }.mapNotNull { map[it] }.map { stringResource(it) }.joinToString(", ")
}

@Composable
fun DiaryScreen(back: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    ScreenScaffold(stringResource(R.string.section_diary), back) { padding ->
        Column(Modifier.padding(padding)) {
            AppTabs(listOf(stringResource(R.string.diary_tab_entries), stringResource(R.string.diary_tab_questions)), tab) { tab = it }
            if (tab == 0) DiaryEntries() else DoctorQuestions()
        }
    }
}

@Composable
private fun DiaryEntries() {
    val app = LocalApp.current
    val language = currentLanguage()
    val scope = rememberCoroutineScope()
    val entries by app.db.diary().observeAll().collectAsState(initial = emptyList())
    var at by rememberSaveable { mutableStateOf(nowMinute()) }
    var fatigue by rememberSaveable { mutableFloatStateOf(3f) }
    var appetite by rememberSaveable { mutableStateOf("2") }
    var sleepQuality by rememberSaveable { mutableStateOf("2") }
    var sleepHours by rememberSaveable { mutableStateOf("") }
    var symptoms by rememberSaveable { mutableStateOf(setOf<String>()) }
    var note by rememberSaveable { mutableStateOf("") }

    ScrollColumn(PaddingValues()) {
        SectionHeader(stringResource(R.string.diary_new))
        DateTimeField(at, { at = it })
        Text(stringResource(R.string.diary_fatigue, fatigue.roundToInt()), style = MaterialTheme.typography.titleSmall)
        Slider(value = fatigue, onValueChange = { fatigue = it }, valueRange = 0f..10f, steps = 9)
        Text(stringResource(R.string.diary_appetite), style = MaterialTheme.typography.titleSmall)
        ChoiceChips(
            listOf("2" to stringResource(R.string.level_good), "1" to stringResource(R.string.level_average), "0" to stringResource(R.string.level_poor)),
            appetite, { appetite = it },
        )
        Text(stringResource(R.string.diary_sleep), style = MaterialTheme.typography.titleSmall)
        ChoiceChips(
            listOf("2" to stringResource(R.string.level_good), "1" to stringResource(R.string.level_average), "0" to stringResource(R.string.level_poor)),
            sleepQuality, { sleepQuality = it },
        )
        NumberField(sleepHours, { sleepHours = it }, stringResource(R.string.diary_sleep_hours), suffix = stringResource(R.string.unit_hours))
        Text(stringResource(R.string.diary_symptoms), style = MaterialTheme.typography.titleSmall)
        MultiChoiceChips(
            SYMPTOMS.map { (code, res) -> code to stringResource(res) },
            symptoms,
            { code -> symptoms = if (code in symptoms) symptoms - code else symptoms + code },
        )
        if ("CHEST_PAIN" in symptoms || "BREATH" in symptoms) WarningBanner(stringResource(R.string.diary_urgent_symptom))
        TextInput(note, { note = it }, stringResource(R.string.diary_note), singleLine = false)
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    app.db.diary().insert(
                        DiaryEntry(
                            date = at.toMillis(), fatigue = fatigue.roundToInt(), appetite = appetite.toInt(),
                            sleepHours = parseDecimal(sleepHours), sleepQuality = sleepQuality.toInt(),
                            symptoms = symptoms.joinToString(","), note = note.trim(),
                        ),
                    )
                    symptoms = emptySet(); note = ""; sleepHours = ""; at = nowMinute()
                }
            },
        ) { Text(stringResource(R.string.save)) }

        SectionHeader(stringResource(R.string.history))
        if (entries.isEmpty()) EmptyState(stringResource(R.string.no_entries))
        val levels = listOf(stringResource(R.string.level_poor), stringResource(R.string.level_average), stringResource(R.string.level_good))
        entries.take(100).forEach { e ->
            HistoryRow(
                Fmt.dateTime(e.date, language) + " · " + stringResource(R.string.diary_fatigue, e.fatigue),
                listOfNotNull(
                    stringResource(R.string.diary_appetite) + ": " + levels.getOrElse(e.appetite) { "" },
                    stringResource(R.string.diary_sleep) + ": " + levels.getOrElse(e.sleepQuality) { "" } +
                        (e.sleepHours?.let { " (${fmt(it, 1)} ${stringResource(R.string.unit_hours)})" } ?: ""),
                    symptomLabels(e.symptoms).ifBlank { null },
                    e.note.ifBlank { null },
                ).joinToString("\n"),
                onDelete = { scope.launch { app.db.diary().delete(e) } },
            )
        }
    }
}

@Composable
private fun DoctorQuestions() {
    val app = LocalApp.current
    val language = currentLanguage()
    val scope = rememberCoroutineScope()
    val questions by app.db.questions().observeAll().collectAsState(initial = emptyList())
    var text by rememberSaveable { mutableStateOf("") }
    ScrollColumn(PaddingValues()) {
        Text(stringResource(R.string.questions_intro), style = MaterialTheme.typography.bodyLarge)
        TextInput(text, { text = it }, stringResource(R.string.questions_new), singleLine = false)
        Button(
            enabled = text.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                scope.launch {
                    app.db.questions().insert(DoctorQuestion(created = System.currentTimeMillis(), text = text.trim()))
                    text = ""
                }
            },
        ) { Text(stringResource(R.string.save)) }
        if (questions.isEmpty()) EmptyState(stringResource(R.string.no_entries))
        questions.forEach { q ->
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(q.answered, { checked -> scope.launch { app.db.questions().update(q.copy(answered = checked)) } })
                    Column(Modifier.weight(1f)) {
                        Text(q.text, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            Fmt.date(q.created, language) + if (q.answered) " · " + stringResource(R.string.questions_answered) else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                HistoryRow("", "", onDelete = { scope.launch { app.db.questions().delete(q) } })
            }
        }
    }
}
