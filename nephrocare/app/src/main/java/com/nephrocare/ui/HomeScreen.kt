package com.nephrocare.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Emergency
import androidx.compose.material.icons.filled.Healing
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Mosque
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nephrocare.R
import com.nephrocare.core.ScheduleCalc
import com.nephrocare.core.WeightCalc
import com.nephrocare.core.WeightKind
import com.nephrocare.core.WeightPoint
import com.nephrocare.core.fmt
import com.nephrocare.reminders.Notifications
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

private data class Tile(val icon: ImageVector, val label: Int, val color: Color, val route: String)

private val tiles = listOf(
    Tile(Icons.Filled.MonitorWeight, R.string.section_weight, Blue, Routes.WEIGHT),
    Tile(Icons.Filled.MonitorHeart, R.string.section_bp, Color(0xFFD84343), Routes.BP),
    Tile(Icons.Filled.Science, R.string.section_labs, Teal, Routes.LABS),
    Tile(Icons.Filled.Restaurant, R.string.section_nutrition, Green, Routes.NUTRITION),
    Tile(Icons.Filled.WaterDrop, R.string.section_fluids, Color(0xFF2B8AD9), Routes.FLUIDS),
    Tile(Icons.Filled.Healing, R.string.section_fistula, Color(0xFF8E5BC8), Routes.FISTULA),
    Tile(Icons.Filled.CalendarMonth, R.string.section_schedule, BlueDark, Routes.SCHEDULE),
    Tile(Icons.Filled.Medication, R.string.section_meds, Color(0xFF2E9E7A), Routes.MEDS),
    Tile(Icons.AutoMirrored.Filled.EventNote, R.string.section_diary, Color(0xFFDB7C26), Routes.DIARY),
    Tile(Icons.AutoMirrored.Filled.MenuBook, R.string.section_encyclopedia, Teal, Routes.ENCYCLOPEDIA),
    Tile(Icons.Filled.VideoLibrary, R.string.section_media, Color(0xFF8E5BC8), Routes.MEDIA),
    Tile(Icons.Filled.Mosque, R.string.section_islamic, Color(0xFF1F8A5B), Routes.ISLAMIC),
    Tile(Icons.Filled.LocalHospital, R.string.section_centers, Color(0xFFD84343), Routes.CENTERS),
    Tile(Icons.Filled.PictureAsPdf, R.string.section_reports, BlueDark, Routes.REPORTS),
    Tile(Icons.Filled.VolunteerActivism, R.string.section_support, Color(0xFFDB7C26), Routes.SUPPORT),
    Tile(Icons.Filled.Emergency, R.string.section_emergency, Danger, Routes.EMERGENCY),
)

@Composable
fun HomeScreen(go: (String) -> Unit) {
    val app = LocalApp.current
    val language = currentLanguage()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by app.settings.profile.collectAsState()
    val schedule by app.settings.schedule.collectAsState()
    val weights by app.db.weights().observeAll().collectAsState(initial = emptyList())
    val bps by app.db.bp().observeAll().collectAsState(initial = emptyList())
    val meds by app.db.meds().observeAll().collectAsState(initial = emptyList())
    val intakes by app.db.intakes().observeAll().collectAsState(initial = emptyList())
    val fluids by app.db.fluids().observeAll().collectAsState(initial = emptyList())
    var permissionTick by remember { mutableIntStateOf(0) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permissionTick++ }

    ScreenScaffold(stringResource(R.string.app_name), onBack = null) { padding ->
        ScrollColumn(padding) {
            val greeting = if (profile.name.isBlank()) stringResource(R.string.home_hello)
            else stringResource(R.string.home_hello_name, profile.name)
            Column {
                Text(greeting, style = MaterialTheme.typography.titleLarge)
                Text(Fmt.date(LocalDate.now(), language), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            val notificationsOk = remember(permissionTick) { Notifications.canPost(context) }
            if (!notificationsOk) {
                Banner(
                    stringResource(R.string.home_enable_notifications),
                    icon = Icons.Filled.NotificationsActive,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.startActivity(
                                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                            )
                        }
                    },
                )
            }

            // Weight
            val points = weights.map { WeightPoint(it.time, runCatching { WeightKind.valueOf(it.kind) }.getOrDefault(WeightKind.HOME), it.kg) }
            val dry = WeightCalc.dryWeight(points)
            val latest = WeightCalc.latestWeighing(points)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    stringResource(R.string.home_current_weight),
                    latest?.let { "${fmt(it.kg, 1)} ${stringResource(R.string.unit_kg)}" } ?: "—",
                    Modifier.weight(1f),
                    subtitle = if (latest != null && dry != null) {
                        val diff = WeightCalc.aboveDry(latest.kg, dry)
                        stringResource(R.string.home_vs_dry, (if (diff >= 0) "+" else "") + fmt(diff, 1))
                    } else null,
                    icon = Icons.Filled.MonitorWeight,
                    onClick = { go(Routes.WEIGHT) },
                )
                StatCard(
                    stringResource(R.string.home_dry_weight),
                    dry?.let { "${fmt(it, 1)} ${stringResource(R.string.unit_kg)}" } ?: "—",
                    Modifier.weight(1f),
                    subtitle = stringResource(R.string.home_dry_by_team),
                    icon = Icons.Filled.MonitorWeight,
                    tint = Green,
                    onClick = { go(Routes.WEIGHT) },
                )
            }

            // Next session and blood pressure
            val days = schedule.days.mapNotNull { runCatching { DayOfWeek.of(it) }.getOrNull() }.toSet()
            val next = ScheduleCalc.nextSession(days, LocalTime.of(schedule.hour, schedule.minute), LocalDateTime.now())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard(
                    stringResource(R.string.home_next_session),
                    next?.let { Fmt.time(it.toLocalTime()) } ?: "—",
                    Modifier.weight(1f),
                    subtitle = next?.let { Fmt.date(it.toLocalDate(), language) } ?: stringResource(R.string.home_set_schedule),
                    icon = Icons.Filled.CalendarMonth,
                    tint = BlueDark,
                    onClick = { go(Routes.SCHEDULE) },
                )
                val lastBp = bps.firstOrNull()
                StatCard(
                    stringResource(R.string.home_last_bp),
                    lastBp?.let { "${it.systolic}/${it.diastolic}" } ?: "—",
                    Modifier.weight(1f),
                    subtitle = lastBp?.let { Fmt.dateTime(it.time, language) } ?: stringResource(R.string.home_no_data),
                    icon = Icons.Filled.MonitorHeart,
                    tint = Color(0xFFD84343),
                    onClick = { go(Routes.BP) },
                )
            }

            // Medicines today
            val doses = todayDoses(meds, intakes)
            val lowStock = meds.filter { com.nephrocare.data.MedStock.isLow(it) }
            if (lowStock.isNotEmpty()) {
                WarningBanner(
                    stringResource(R.string.stock_low_banner, lowStock.joinToString(", ") { it.name }),
                    onClick = { go(Routes.MEDS) },
                )
            }
            AppCard(onClick = { go(Routes.MEDS) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Medication, contentDescription = null, tint = Green)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_meds_today), style = MaterialTheme.typography.titleMedium)
                }
                if (doses.isEmpty()) {
                    Text(stringResource(R.string.home_no_meds), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    doses.take(6).forEach { dose ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("${Fmt.time(dose.time)}  ${dose.med.name}", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                            if (dose.status == "TAKEN") {
                                Icon(Icons.Filled.CheckCircle, contentDescription = stringResource(R.string.med_taken), tint = Ok)
                            } else if (dose.status == null) {
                                FilledTonalButton(onClick = { scope.launch { markDose(app, dose, "TAKEN") } }) {
                                    Text(stringResource(R.string.action_taken))
                                }
                            } else {
                                Text(stringResource(R.string.med_skipped), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            // Fluids today
            val today = LocalDate.now()
            val todayMl = fluids.filter { it.time.toLocalDate() == today }.sumOf { it.ml }
            AppCard(onClick = { go(Routes.FLUIDS) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = Color(0xFF2B8AD9))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.home_fluids_today), style = MaterialTheme.typography.titleMedium)
                }
                val target = profile.fluidTargetMl
                Text(
                    if (target != null) stringResource(R.string.fluids_of_target, todayMl, target)
                    else stringResource(R.string.fluids_ml, todayMl),
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (target != null && target > 0) {
                    LinearProgressIndicator(
                        progress = { (todayMl.toFloat() / target).coerceIn(0f, 1f) },
                        color = if (todayMl > target) Danger else Color(0xFF2B8AD9),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            // Tip of the day
            val tips = app.content.pack(language).tips
            if (tips.isNotEmpty()) {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = Warning)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.home_tip), style = MaterialTheme.typography.titleMedium)
                    }
                    Text(tips[(LocalDate.now().dayOfYear) % tips.size], style = MaterialTheme.typography.bodyLarge)
                }
            }

            SectionHeader(stringResource(R.string.home_sections))
            tiles.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { tile ->
                        NavTile(tile.icon, stringResource(tile.label), tile.color, Modifier.weight(1f)) { go(tile.route) }
                    }
                    repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Spacer(Modifier.height(4.dp))
            DisclaimerBanner()
        }
    }
}
