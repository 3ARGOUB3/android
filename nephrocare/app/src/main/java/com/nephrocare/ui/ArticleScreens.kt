package com.nephrocare.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nephrocare.R

fun dial(context: Context, number: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number.filter { it.isDigit() || it == '+' })))
    }
}

fun shareText(context: Context, title: String, text: String) {
    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, title)
        .putExtra(Intent.EXTRA_TEXT, text)
    runCatching { context.startActivity(Intent.createChooser(send, title)) }
}

@Composable
fun ArticleScreen(id: String, back: () -> Unit, go: (String) -> Unit) {
    val app = LocalApp.current
    val language = currentLanguage()
    val context = LocalContext.current
    val article = remember(id, language) { app.content.article(language, id) }
    ScreenScaffold(
        article?.title ?: stringResource(R.string.app_name), back,
        actions = {
            if (article != null) {
                IconButton(onClick = {
                    val body = buildString {
                        appendLine(article.title)
                        appendLine()
                        appendLine(article.summary)
                        article.s.forEach { s ->
                            s.h?.let { appendLine(); appendLine(it) }
                            s.p.forEach { appendLine(it) }
                            s.b.forEach { appendLine("• $it") }
                        }
                        appendLine()
                        append(context.getString(R.string.disclaimer_short))
                    }
                    shareText(context, article.title, body)
                }) { Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share)) }
            }
        },
    ) { padding ->
        ScrollColumn(padding) {
            if (article == null) {
                EmptyState(stringResource(R.string.not_found))
                return@ScrollColumn
            }
            if (article.cat == "emergency") EmergencyCallButtons()
            ArticleBody(article)
            if (article.cat == "fistula" || article.cat == "emergency") {
                OutlinedButton(onClick = { go(Routes.EMERGENCY) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.section_emergency))
                }
            }
            Spacer(Modifier.padding(4.dp))
            DisclaimerBanner()
        }
    }
}

@Composable
fun ArticleListScreen(category: String, titleRes: Int, back: () -> Unit, go: (String) -> Unit) {
    val app = LocalApp.current
    val language = currentLanguage()
    val articles = remember(language, category) { app.content.articles(language, category) }
    ScreenScaffold(stringResource(titleRes), back) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (category == "support") item { Banner(stringResource(R.string.support_intro)) }
            items(articles, key = { it.id }) { a ->
                AppCard(onClick = { go(Routes.article(a.id)) }) {
                    Text(a.title, style = MaterialTheme.typography.titleMedium)
                    if (a.summary.isNotBlank()) Text(a.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { DisclaimerBanner() }
        }
    }
}

/** Big call buttons: the team, the personal emergency contact and public emergency numbers. */
@Composable
fun EmergencyCallButtons() {
    val app = LocalApp.current
    val context = LocalContext.current
    val profile by app.settings.profile.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CallButton(stringResource(R.string.emergency_dz), "14", Modifier.weight(1f)) { dial(context, "14") }
            CallButton(stringResource(R.string.emergency_eu), "112", Modifier.weight(1f)) { dial(context, "112") }
        }
        if (profile.nephrologistPhone.isNotBlank()) {
            OutlinedButton(onClick = { dial(context, profile.nephrologistPhone) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Call, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.emergency_call_team, profile.nephrologist.ifBlank { profile.nephrologistPhone }))
            }
        }
        if (profile.emergencyPhone.isNotBlank()) {
            OutlinedButton(onClick = { dial(context, profile.emergencyPhone) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Call, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.emergency_call_contact, profile.emergencyName.ifBlank { profile.emergencyPhone }))
            }
        }
    }
}

@Composable
private fun CallButton(label: String, number: String, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = Danger, contentColor = androidx.compose.ui.graphics.Color.White),
        contentPadding = PaddingValues(vertical = 14.dp, horizontal = 8.dp),
    ) {
        Icon(Icons.Filled.Call, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Column {
            Text(number, style = MaterialTheme.typography.titleLarge)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun EmergencyScreen(go: (String) -> Unit) {
    val app = LocalApp.current
    val language = currentLanguage()
    val articles = remember(language) { app.content.articles(language, "emergency") }
    ScreenScaffold(stringResource(R.string.section_emergency), onBack = null) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { WarningBanner(stringResource(R.string.emergency_intro)) }
            item { EmergencyCallButtons() }
            item { SectionHeader(stringResource(R.string.emergency_signs)) }
            items(articles, key = { it.id }) { a ->
                AppCard(onClick = { go(Routes.article(a.id)) }) {
                    Text(a.title, style = MaterialTheme.typography.titleMedium, color = Danger)
                    if (a.summary.isNotBlank()) Text(a.summary)
                }
            }
            item { DisclaimerBanner() }
        }
    }
}
