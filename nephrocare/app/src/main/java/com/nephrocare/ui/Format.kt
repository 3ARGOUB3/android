package com.nephrocare.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import com.nephrocare.AppContainer
import com.nephrocare.LocaleHelper
import com.nephrocare.R
import com.nephrocare.core.Prayer
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JTextStyle

val LocalApp = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

/** Current content language, read once per composition from the container. */
@Composable
fun currentLanguage(): String = LocalApp.current.language()

fun zone(): ZoneId = ZoneId.systemDefault()

fun Long.toLocalDateTime(): LocalDateTime = Instant.ofEpochMilli(this).atZone(zone()).toLocalDateTime()
fun Long.toLocalDate(): LocalDate = toLocalDateTime().toLocalDate()
fun LocalDateTime.toMillis(): Long = atZone(zone()).toInstant().toEpochMilli()
fun LocalDate.startMillis(): Long = atStartOfDay(zone()).toInstant().toEpochMilli()

object Fmt {
    private fun locale(language: String) = LocaleHelper.localeFor(language)

    fun date(millis: Long, language: String): String =
        DateTimeFormatter.ofPattern("d MMM yyyy", locale(language)).format(millis.toLocalDateTime())

    fun date(date: LocalDate, language: String): String =
        DateTimeFormatter.ofPattern("EEEE d MMMM", locale(language)).format(date)

    fun shortDate(millis: Long, language: String): String =
        DateTimeFormatter.ofPattern("d MMM", locale(language)).format(millis.toLocalDateTime())

    fun dateTime(millis: Long, language: String): String =
        DateTimeFormatter.ofPattern("d MMM yyyy  HH:mm", locale(language)).format(millis.toLocalDateTime())

    fun time(t: LocalTime): String = "%02d:%02d".format(t.hour, t.minute)
    fun time(millis: Long): String = time(millis.toLocalDateTime().toLocalTime())

    fun dayName(day: DayOfWeek, language: String, short: Boolean = false): String =
        day.getDisplayName(if (short) JTextStyle.SHORT else JTextStyle.FULL, locale(language))

    fun fullDateTime(t: LocalDateTime, language: String): String =
        DateTimeFormatter.ofPattern("EEEE d MMMM  HH:mm", locale(language)).format(t)
}

/** Parses a decimal typed with a comma or Arabic-Indic digits. */
fun parseDecimal(input: String): Double? {
    val normalized = buildString {
        for (ch in input.trim()) {
            when (ch) {
                in '٠'..'٩' -> append('0' + (ch - '٠'))
                ',', '٫' -> append('.')
                else -> append(ch)
            }
        }
    }
    return normalized.toDoubleOrNull()
}

fun parseInt(input: String): Int? = parseDecimal(input)?.takeIf { it == Math.floor(it) }?.toInt()

fun prayerName(context: Context, prayer: Prayer): String = context.getString(
    when (prayer) {
        Prayer.FAJR -> R.string.prayer_fajr
        Prayer.SUNRISE -> R.string.prayer_sunrise
        Prayer.DHUHR -> R.string.prayer_dhuhr
        Prayer.ASR -> R.string.prayer_asr
        Prayer.MAGHRIB -> R.string.prayer_maghrib
        Prayer.ISHA -> R.string.prayer_isha
    },
)

fun weightKindLabel(kind: String): Int = when (kind) {
    "DRY" -> R.string.weight_kind_dry
    "PRE" -> R.string.weight_kind_pre
    "POST" -> R.string.weight_kind_post
    else -> R.string.weight_kind_home
}

fun bpContextLabel(kind: String): Int = when (kind) {
    "PRE" -> R.string.bp_ctx_pre
    "POST" -> R.string.bp_ctx_post
    "HOME" -> R.string.bp_ctx_home
    else -> R.string.bp_ctx_other
}
