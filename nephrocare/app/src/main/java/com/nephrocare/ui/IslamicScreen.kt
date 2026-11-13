package com.nephrocare.ui

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.nephrocare.R
import com.nephrocare.core.AsrMethod
import com.nephrocare.core.CalcMethod
import com.nephrocare.core.Cities
import com.nephrocare.core.Dhikr
import com.nephrocare.core.OBLIGATORY_PRAYERS
import com.nephrocare.core.Prayer
import com.nephrocare.core.TextNormalizer
import com.nephrocare.data.QuranVerse
import com.nephrocare.reminders.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime

@Composable
fun IslamicScreen(back: () -> Unit, go: (String) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    ScreenScaffold(stringResource(R.string.section_islamic), back) { padding ->
        Column(Modifier.padding(padding)) {
            AppTabs(
                listOf(
                    R.string.isl_tab_prayer, R.string.isl_tab_morning, R.string.isl_tab_evening,
                    R.string.isl_tab_after_prayer, R.string.isl_tab_duas, R.string.isl_tab_quran,
                ).map { stringResource(it) },
                tab,
            ) { tab = it }
            when (tab) {
                0 -> PrayerTimesTab()
                1 -> AdhkarList(listOf("morning"))
                2 -> AdhkarList(listOf("evening"))
                3 -> AdhkarList(listOf("after_prayer"))
                4 -> AdhkarList(listOf("sick", "patience"))
                else -> QuranIndex(go)
            }
        }
    }
}

@Composable
private fun methodLabel(m: String): String = stringResource(
    when (m) {
        "MWL" -> R.string.method_mwl
        "EGYPT" -> R.string.method_egypt
        "UMM_AL_QURA" -> R.string.method_umm_al_qura
        "ISNA" -> R.string.method_isna
        "MOROCCO" -> R.string.method_morocco
        "TUNISIA" -> R.string.method_tunisia
        "FRANCE_UOIF" -> R.string.method_france
        "KARACHI" -> R.string.method_karachi
        else -> R.string.method_algeria
    },
)

@Composable
private fun PrayerTimesTab() {
    val app = LocalApp.current
    val context = LocalContext.current
    val language = currentLanguage()
    val p by app.settings.prayer.collectAsState()
    var pickCity by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val times = remember(p, today) { ReminderScheduler.prayerTimes(p, today) }
    val now = LocalDateTime.now()
    val next = OBLIGATORY_PRAYERS.firstOrNull { pr -> times[pr]?.let { today.atTime(it).isAfter(now) } == true }
    val city = Cities.byId(p.cityId) ?: Cities.all.first()

    ScrollColumn(PaddingValues()) {
        AppCard {
            Text(
                if (p.customLatitude != null) stringResource(R.string.prayer_custom_location) else city.name(language),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(Fmt.date(today, language), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Prayer.entries.forEach { pr ->
                val t = times[pr]
                val isNext = pr == next
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        prayerName(context, pr),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
                        color = if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        t?.let { Fmt.time(it) } ?: "—",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
                        color = if (isNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                    if (pr != Prayer.SUNRISE) {
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = pr.name in p.reminders,
                            onCheckedChange = { on ->
                                app.settings.updatePrayer { s -> s.copy(reminders = if (on) s.reminders + pr.name else s.reminders - pr.name) }
                                app.rescheduleReminders()
                            },
                        )
                    }
                }
            }
            Text(stringResource(R.string.prayer_switch_hint), style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(onClick = { pickCity = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.prayer_change_city)) }

        SectionHeader(stringResource(R.string.prayer_method))
        ChoiceChips(CalcMethod.entries.map { it.name to methodLabel(it.name) }, p.method, { m ->
            app.settings.updatePrayer { it.copy(method = m) }; app.rescheduleReminders()
        })
        SectionHeader(stringResource(R.string.prayer_asr_method))
        ChoiceChips(
            listOf(AsrMethod.STANDARD.name to stringResource(R.string.asr_standard), AsrMethod.HANAFI.name to stringResource(R.string.asr_hanafi)),
            p.asr, { a -> app.settings.updatePrayer { it.copy(asr = a) }; app.rescheduleReminders() },
        )
        SectionHeader(stringResource(R.string.prayer_adjust))
        OBLIGATORY_PRAYERS.forEach { pr ->
            val off = p.offsets[pr.name] ?: 0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(prayerName(context, pr), Modifier.weight(1f))
                TextButton(onClick = { setOffset(app, pr, off - 1) }) { Text("−") }
                Text(stringResource(R.string.prayer_minutes, off))
                TextButton(onClick = { setOffset(app, pr, off + 1) }) { Text("+") }
            }
        }
        Banner(stringResource(R.string.prayer_note))
    }

    if (pickCity) CityDialog(onDismiss = { pickCity = false })
}

private fun setOffset(app: com.nephrocare.AppContainer, prayer: Prayer, minutes: Int) {
    val v = minutes.coerceIn(-30, 30)
    app.settings.updatePrayer { it.copy(offsets = it.offsets + (prayer.name to v)) }
    app.rescheduleReminders()
}

@Composable
private fun CityDialog(onDismiss: () -> Unit) {
    val app = LocalApp.current
    val language = currentLanguage()
    var query by remember { mutableStateOf("") }
    var lat by remember { mutableStateOf("") }
    var lon by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.prayer_change_city)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(query, { query = it }, label = { Text(stringResource(R.string.centers_search)) }, singleLine = true)
                val q = TextNormalizer.normalize(query)
                Cities.all.filter { q.isEmpty() || TextNormalizer.normalize(it.arabic + " " + it.latin).contains(q) }.forEach { c ->
                    TextButton(
                        onClick = {
                            app.settings.updatePrayer { it.copy(cityId = c.id, customLatitude = null, customLongitude = null) }
                            app.rescheduleReminders()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(c.name(language), modifier = Modifier.fillMaxWidth()) }
                }
                SectionHeader(stringResource(R.string.prayer_coordinates))
                NumberField(lat, { lat = it }, stringResource(R.string.prayer_latitude))
                NumberField(lon, { lon = it }, stringResource(R.string.prayer_longitude))
                val la = parseDecimal(lat)
                val lo = parseDecimal(lon)
                FilledTonalButton(
                    enabled = la != null && lo != null && la in -66.0..66.0 && lo in -180.0..180.0,
                    onClick = {
                        app.settings.updatePrayer { it.copy(customLatitude = la, customLongitude = lo) }
                        app.rescheduleReminders()
                        onDismiss()
                    },
                ) { Text(stringResource(R.string.save)) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun AdhkarList(categories: List<String>) {
    val app = LocalApp.current
    val language = currentLanguage()
    val items = remember(categories) { app.content.adhkar.items.filter { it.cat in categories } }
    val counters = remember { mutableStateMapOf<String, Int>() }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items, key = { it.id }) { d -> DhikrCard(d, language, counters[d.id] ?: 0) { counters[d.id] = (counters[d.id] ?: 0) + 1 } }
        item { Text(stringResource(R.string.adhkar_source_note), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun DhikrCard(d: Dhikr, language: String, count: Int, onCount: () -> Unit) {
    val app = LocalApp.current
    val verses by produceState<List<QuranVerse>>(emptyList(), d.q, language) {
        value = d.q?.let { ref -> withContext(Dispatchers.IO) { app.content.quranRef(ref, language) } } ?: emptyList()
    }
    AppCard {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(Modifier.fillMaxWidth()) {
                if (d.q != null) {
                    Text(
                        verses.joinToString(" ") { "${it.arabic} ﴿${toArabicDigits(it.number)}﴾" },
                        style = QuranTextStyle, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Text(d.ar, style = QuranTextStyle.copy(fontSize = QuranTextStyle.fontSize * 0.85f), modifier = Modifier.fillMaxWidth())
                }
            }
        }
        if (language != "ar") {
            val translation = if (d.q != null) verses.mapNotNull { it.translation }.joinToString(" ") else d.tr[language].orEmpty()
            if (translation.isNotBlank()) Text(translation, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                listOfNotNull(
                    d.src.ifBlank { null },
                    d.q?.let { stringResource(R.string.quran_ref, it) },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
            )
            if (d.count > 1) {
                FilledTonalButton(onClick = onCount) {
                    Text(if (count >= d.count) "✓ ${d.count}" else "$count / ${d.count}")
                }
            }
        }
    }
}

fun toArabicDigits(n: Int): String = n.toString().map { '٠' + (it - '0') }.joinToString("")

@Composable
private fun QuranIndex(go: (String) -> Unit) {
    val app = LocalApp.current
    val language = currentLanguage()
    var query by rememberSaveable { mutableStateOf("") }
    val q = TextNormalizer.normalize(query)
    val surahs = app.content.surahs.filter {
        q.isEmpty() || TextNormalizer.normalize("${it.id} ${it.name} ${it.transliteration} ${it.names.values.joinToString(" ")}").contains(q)
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            OutlinedTextField(query, { query = it }, singleLine = true, label = { Text(stringResource(R.string.quran_search)) }, modifier = Modifier.fillMaxWidth())
        }
        items(surahs, key = { it.id }) { s ->
            AppCard(onClick = { go(Routes.surah(s.id)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${s.id}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(s.transliteration + (s.names[language]?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyMedium)
                        Text(stringResource(R.string.quran_verses, s.count), style = MaterialTheme.typography.bodySmall)
                    }
                    Text(s.name, style = MaterialTheme.typography.titleLarge)
                }
            }
        }
        item { Text(stringResource(R.string.quran_source), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
fun SurahScreen(id: Int, back: () -> Unit) {
    val app = LocalApp.current
    val language = currentLanguage()
    val info = app.content.surahs.firstOrNull { it.id == id }
    var showTranslation by rememberSaveable { mutableStateOf(language != "ar") }
    val verses by produceState<List<QuranVerse>?>(null, id, language) {
        value = withContext(Dispatchers.IO) { app.content.surah(id, if (language == "ar") "en" else language) }
    }
    ScreenScaffold(info?.let { "${it.name} · ${it.transliteration}" } ?: "", back) { padding ->
        LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.quran_show_translation), Modifier.weight(1f))
                    Switch(showTranslation, { showTranslation = it })
                }
            }
            if (id != 1 && id != 9) {
                item {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Text(
                            app.content.surah(1, "ar").firstOrNull()?.arabic ?: "",
                            style = QuranTextStyle, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            items(verses ?: emptyList(), key = { it.number }) { v ->
                Column {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        Text("${v.arabic} ﴿${toArabicDigits(v.number)}﴾", style = QuranTextStyle, modifier = Modifier.fillMaxWidth())
                    }
                    if (showTranslation && v.translation != null) {
                        Text("${v.number}. ${v.translation}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item { Text(stringResource(R.string.quran_source), style = MaterialTheme.typography.bodySmall) }
        }
    }
}
