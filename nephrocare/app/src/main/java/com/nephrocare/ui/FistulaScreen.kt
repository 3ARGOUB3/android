package com.nephrocare.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.nephrocare.R
import com.nephrocare.data.FistulaCheck
import kotlinx.coroutines.launch

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun FistulaScreen(back: () -> Unit, go: (String) -> Unit) {
    val app = LocalApp.current
    val language = currentLanguage()
    val scope = rememberCoroutineScope()
    val checks by app.db.fistula().observeAll().collectAsState(initial = emptyList())
    val articles = remember(language) { app.content.articles(language, "fistula") }
    var thrill by rememberSaveable { mutableStateOf(true) }
    var redness by rememberSaveable { mutableStateOf(false) }
    var swelling by rememberSaveable { mutableStateOf(false) }
    var pain by rememberSaveable { mutableStateOf(false) }
    var note by rememberSaveable { mutableStateOf("") }

    ScreenScaffold(stringResource(R.string.section_fistula), back) { padding ->
        ScrollColumn(padding) {
            FistulaIllustration(Modifier.fillMaxWidth())
            Text(stringResource(R.string.illus_fistula_caption), style = MaterialTheme.typography.bodySmall)

            SectionHeader(stringResource(R.string.fistula_check_title))
            Text(stringResource(R.string.fistula_check_hint))
            CheckRow(stringResource(R.string.fistula_thrill), thrill) { thrill = it }
            CheckRow(stringResource(R.string.fistula_redness), redness) { redness = it }
            CheckRow(stringResource(R.string.fistula_swelling), swelling) { swelling = it }
            CheckRow(stringResource(R.string.fistula_pain), pain) { pain = it }
            if (!thrill) WarningBanner(stringResource(R.string.fistula_no_thrill_warning), onClick = { go(Routes.EMERGENCY) })
            else if (redness || swelling || pain) WarningBanner(stringResource(R.string.fistula_infection_warning))
            TextInput(note, { note = it }, stringResource(R.string.note_optional))
            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    scope.launch {
                        app.db.fistula().insert(
                            FistulaCheck(time = System.currentTimeMillis(), thrill = thrill, redness = redness, swelling = swelling, pain = pain, note = note.trim()),
                        )
                        note = ""
                    }
                },
            ) { Text(stringResource(R.string.save)) }

            SectionHeader(stringResource(R.string.fistula_learn))
            articles.forEach { a ->
                AppCard(onClick = { go(Routes.article(a.id)) }) {
                    Text(a.title, style = MaterialTheme.typography.titleMedium)
                    if (a.summary.isNotBlank()) Text(a.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            SectionHeader(stringResource(R.string.history))
            if (checks.isEmpty()) EmptyState(stringResource(R.string.no_entries))
            checks.take(60).forEach { c ->
                val problems = listOfNotNull(
                    if (!c.thrill) stringResource(R.string.fistula_no_thrill) else null,
                    if (c.redness) stringResource(R.string.fistula_redness) else null,
                    if (c.swelling) stringResource(R.string.fistula_swelling) else null,
                    if (c.pain) stringResource(R.string.fistula_pain) else null,
                )
                HistoryRow(
                    if (problems.isEmpty()) stringResource(R.string.fistula_ok) else problems.joinToString(" · "),
                    Fmt.dateTime(c.time, language) + if (c.note.isNotBlank()) " · ${c.note}" else "",
                    accent = if (!c.thrill) Danger else if (problems.isEmpty()) Ok else Warning,
                    onDelete = { scope.launch { app.db.fistula().delete(c) } },
                )
            }
            DisclaimerBanner()
        }
    }
}
