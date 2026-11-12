package com.nephrocare.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nephrocare.R

@Composable
private fun kindLabel(kind: String): String = stringResource(
    when (kind) {
        "lab" -> R.string.section_labs
        "food", "dish", "nutrition", "recipe" -> R.string.section_nutrition
        "fistula" -> R.string.section_fistula
        "emergency" -> R.string.section_emergency
        "support" -> R.string.section_support
        "bp" -> R.string.section_bp
        "meds" -> R.string.section_meds
        "faq" -> R.string.search_faq
        else -> R.string.section_encyclopedia
    },
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(go: (String) -> Unit) {
    val app = LocalApp.current
    val language = currentLanguage()
    var query by rememberSaveable { mutableStateOf("") }
    val index = remember(language) { app.content.searchIndex(language) }
    val hits = remember(query, index) { index.search(query) }
    val suggestions = stringArrayResource(R.array.search_suggestions)

    ScreenScaffold(stringResource(R.string.nav_search), onBack = null) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(
                    query, { query = it }, singleLine = true,
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    label = { Text(stringResource(R.string.search_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (query.isBlank()) {
                item { Text(stringResource(R.string.search_try), style = MaterialTheme.typography.titleSmall) }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        suggestions.forEach { s -> SuggestionChip(onClick = { query = s }, label = { Text(s) }) }
                    }
                }
                item { Banner(stringResource(R.string.search_disclaimer)) }
            } else if (hits.isEmpty()) {
                item { EmptyState(stringResource(R.string.search_no_results)) }
            }
            items(hits, key = { it.doc.key }) { hit ->
                val doc = hit.doc
                val (type, id) = doc.key.split(':', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                AppCard(onClick = {
                    when (type) {
                        "article" -> go(Routes.article(id))
                        "lab" -> go(Routes.lab(id))
                        else -> go(Routes.NUTRITION)
                    }
                }) {
                    Text(kindLabel(doc.kind), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    Text(doc.title, style = MaterialTheme.typography.titleMedium)
                    if (type == "food") {
                        app.content.foods.foods.firstOrNull { it.id == id }?.let { FoodCard(it, language) }
                    } else if (type == "dish") {
                        app.content.foods.dishes.firstOrNull { it.id == id }?.let { DishCard(it, language) }
                    } else if (doc.text.isNotBlank()) {
                        Text(doc.text.take(160) + "…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
