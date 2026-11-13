package com.nephrocare.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nephrocare.R
import com.nephrocare.core.Dish
import com.nephrocare.core.Food
import com.nephrocare.core.NutrientLevels
import com.nephrocare.core.TextNormalizer
import com.nephrocare.core.fmt

@Composable
fun NutritionScreen(back: () -> Unit, go: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    ScreenScaffold(stringResource(R.string.section_nutrition), back) { padding ->
        Column(Modifier.padding(padding)) {
            AppTabs(
                listOf(R.string.nut_tab_guide, R.string.nut_tab_foods, R.string.nut_tab_dishes, R.string.nut_tab_recipes).map { stringResource(it) },
                tab,
            ) { tab = it }
            when (tab) {
                0 -> NutritionGuide(go)
                1 -> FoodsTable()
                2 -> DishesList()
                else -> ArticleLinks("recipe", go, Modifier.fillMaxSize())
            }
        }
    }
}

@Composable
private fun NutritionGuide(go: (String) -> Unit) {
    val app = LocalApp.current
    val language = currentLanguage()
    val articles = remember(language) { app.content.articles(language, "nutrition") }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { WarningBanner(stringResource(R.string.nut_personal_warning)) }
        items(articles, key = { it.id }) { a ->
            AppCard(onClick = { go(Routes.article(a.id)) }) {
                Text(a.title, style = MaterialTheme.typography.titleMedium)
                if (a.summary.isNotBlank()) Text(a.summary, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private val GROUPS = listOf("all", "fruit", "vegetable", "starch", "protein", "dairy", "legume", "nuts", "drink", "condiment", "processed")

@Composable
private fun groupLabel(g: String): String = stringResource(
    when (g) {
        "fruit" -> R.string.food_fruit
        "vegetable" -> R.string.food_vegetable
        "starch" -> R.string.food_starch
        "protein" -> R.string.food_protein
        "dairy" -> R.string.food_dairy
        "legume" -> R.string.food_legume
        "nuts" -> R.string.food_nuts
        "drink" -> R.string.food_drink
        "condiment" -> R.string.food_condiment
        "processed" -> R.string.food_processed
        else -> R.string.food_all
    },
)

@Composable
private fun FoodsTable() {
    val app = LocalApp.current
    val language = currentLanguage()
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable { mutableStateOf("all") }
    var sort by rememberSaveable { mutableStateOf("name") }
    val foods = app.content.foods.foods
    val q = TextNormalizer.normalize(query.trim())
    val shown = foods
        .filter { group == "all" || it.g == group }
        .filter { q.isEmpty() || it.n.values.any { n -> TextNormalizer.normalize(n).contains(q) } }
        .let { list ->
            when (sort) {
                "k" -> list.sortedBy { it.k }
                "p" -> list.sortedBy { it.p }
                "na" -> list.sortedBy { it.na }
                else -> list.sortedBy { it.n[language] ?: it.id }
            }
        }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            OutlinedTextField(
                query, { query = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                label = { Text(stringResource(R.string.food_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { ChoiceChips(GROUPS.map { it to groupLabel(it) }, group, { group = it }) }
        item {
            ChoiceChips(
                listOf(
                    "name" to stringResource(R.string.food_sort_name),
                    "k" to stringResource(R.string.food_sort_k),
                    "p" to stringResource(R.string.food_sort_p),
                    "na" to stringResource(R.string.food_sort_na),
                ),
                sort, { sort = it },
            )
        }
        item { Banner(stringResource(R.string.food_values_note)) }
        items(shown, key = { it.id }) { FoodCard(it, language) }
        if (shown.isEmpty()) item { EmptyState(stringResource(R.string.search_no_results)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoodCard(food: Food, language: String) {
    AppCard {
        Text(food.n[language] ?: food.n["en"].orEmpty(), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.food_per100, food.k, food.p, food.na, fmt(food.pr, 1)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LevelBadge(stringResource(R.string.nutrient_k), NutrientLevels.potassium(food.k))
            LevelBadge(stringResource(R.string.nutrient_p), NutrientLevels.phosphorus(food.p))
            LevelBadge(stringResource(R.string.nutrient_na), NutrientLevels.sodium(food.na))
            LevelBadge(stringResource(R.string.nutrient_protein), NutrientLevels.protein(food.pr))
        }
        if (food.add) Text("⚠ " + stringResource(R.string.food_additives), color = Danger, style = MaterialTheme.typography.bodySmall)
        if (food.water) Text("💧 " + stringResource(R.string.food_water_rich), color = Blue, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DishesList() {
    val app = LocalApp.current
    val language = currentLanguage()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Banner(stringResource(R.string.dish_note)) }
        items(app.content.foods.dishes, key = { it.id }) { DishCard(it, language) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DishCard(dish: Dish, language: String) {
    AppCard {
        Text(dish.n[language] ?: dish.n["en"].orEmpty(), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LevelBadge(stringResource(R.string.nutrient_k), NutrientLevels.parse(dish.k))
            LevelBadge(stringResource(R.string.nutrient_p), NutrientLevels.parse(dish.p))
            LevelBadge(stringResource(R.string.nutrient_na), NutrientLevels.parse(dish.na))
            LevelBadge(stringResource(R.string.nutrient_fluid), NutrientLevels.parse(dish.fl))
        }
        Text(dish.tip[language] ?: dish.tip["en"].orEmpty(), style = MaterialTheme.typography.bodyMedium)
    }
}
