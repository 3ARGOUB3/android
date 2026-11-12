package com.nephrocare.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import com.nephrocare.R
import com.nephrocare.report.ReportGenerator
import com.nephrocare.report.ReportSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private fun sectionLabel(s: ReportSection): Int = when (s) {
    ReportSection.PROFILE -> R.string.report_patient
    ReportSection.WEIGHT -> R.string.section_weight
    ReportSection.BP -> R.string.section_bp
    ReportSection.LABS -> R.string.section_labs
    ReportSection.SESSIONS -> R.string.report_sessions
    ReportSection.MEDS -> R.string.section_meds
    ReportSection.DIARY -> R.string.report_diary
}

@Composable
fun ReportsScreen(back: () -> Unit) {
    val app = LocalApp.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var days by rememberSaveable { mutableStateOf(90) }
    var sections by rememberSaveable { mutableStateOf(ReportSection.entries.map { it.name }.toSet()) }
    var busy by remember { mutableStateOf(false) }
    var file by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf(false) }

    val saveAs = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val f = file
        if (uri != null && f != null) {
            scope.launch(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { out -> f.inputStream().use { it.copyTo(out) } } }
            }
        }
    }

    ScreenScaffold(stringResource(R.string.section_reports), back) { padding ->
        ScrollColumn(padding) {
            Text(stringResource(R.string.report_intro), style = MaterialTheme.typography.bodyLarge)
            SectionHeader(stringResource(R.string.report_choose_period))
            ChoiceChips(
                listOf(
                    "30" to stringResource(R.string.period_month),
                    "90" to stringResource(R.string.period_3months),
                    "365" to stringResource(R.string.period_year),
                    "0" to stringResource(R.string.period_all),
                ),
                days.toString(), { days = it.toInt() },
            )
            SectionHeader(stringResource(R.string.report_choose_sections))
            ReportSection.entries.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(s.name in sections, { on -> sections = if (on) sections + s.name else sections - s.name })
                    Text(stringResource(sectionLabel(s)))
                }
            }
            Button(
                enabled = !busy && sections.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    busy = true; error = false
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) {
                                ReportGenerator(app).generate(days, sections.map { ReportSection.valueOf(it) }.toSet())
                            }
                        }
                        file = result.getOrNull()
                        error = result.isFailure
                        busy = false
                    }
                },
            ) { Text(stringResource(R.string.report_generate)) }
            if (busy) CircularProgressIndicator()
            if (error) WarningBanner(stringResource(R.string.report_error))
            file?.let { f ->
                Banner(stringResource(R.string.report_ready))
                val uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)
                Button(onClick = {
                    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { context.startActivity(view) }
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.report_open)) }
                OutlinedButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("application/pdf")
                        .putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { context.startActivity(Intent.createChooser(send, context.getString(R.string.report_share))) }
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.report_share)) }
                OutlinedButton(onClick = { saveAs.launch(f.name) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.report_save)) }
            }
            Banner(stringResource(R.string.report_privacy))
        }
    }
}
