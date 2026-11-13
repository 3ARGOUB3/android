package com.nephrocare.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.nephrocare.AppContainer
import com.nephrocare.core.AsrMethod
import com.nephrocare.core.CalcMethod
import com.nephrocare.core.Cities
import com.nephrocare.core.Prayer
import com.nephrocare.core.PrayerTimes
import com.nephrocare.core.ScheduleCalc
import com.nephrocare.data.PrayerSettings
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Computes the next occurrence of every reminder and sets one alarm for each.
 * It runs at start-up, after every change, after each alarm and after a reboot,
 * so only the next occurrence ever needs to be scheduled.
 */
object ReminderScheduler {
    const val ACTION_ALARM = "com.nephrocare.action.ALARM"
    const val KIND_DIALYSIS = "dialysis"
    const val KIND_PREP = "prep"
    const val KIND_MED = "med"
    const val KIND_PRAYER = "prayer"
    const val EXTRA_KIND = "kind"
    const val EXTRA_CODE = "code"
    const val EXTRA_MED_ID = "medId"
    const val EXTRA_AT = "at"
    const val EXTRA_PRAYER = "prayer"

    private const val CODE_DIALYSIS = 1000
    private const val CODE_PREP = 1001
    private const val CODE_PRAYER = 2000
    private const val CODE_MED = 100_000

    data class Alarm(val code: Int, val kind: String, val at: LocalDateTime, val medId: Long = 0, val prayer: String = "")

    private fun zone() = ZoneId.systemDefault()
    fun millis(t: LocalDateTime): Long = t.atZone(zone()).toInstant().toEpochMilli()

    suspend fun rescheduleAll(c: AppContainer) {
        val alarms = computeAlarms(c, LocalDateTime.now())
        val am = c.context.getSystemService(AlarmManager::class.java) ?: return
        val newCodes = alarms.map { it.code.toString() }.toSet()
        for (old in c.settings.scheduledAlarmCodes - newCodes) {
            old.toIntOrNull()?.let { am.cancel(pendingIntent(c.context, it, null)) }
        }
        alarms.forEach { set(c.context, am, it) }
        c.settings.scheduledAlarmCodes = newCodes
    }

    suspend fun computeAlarms(c: AppContainer, now: LocalDateTime): List<Alarm> {
        val alarms = mutableListOf<Alarm>()

        val s = c.settings.schedule.value
        val days = s.days.mapNotNull { runCatching { DayOfWeek.of(it) }.getOrNull() }.toSet()
        if (days.isNotEmpty()) {
            val time = LocalTime.of(s.hour, s.minute)
            if (s.reminderEnabled) {
                val lead = s.reminderMinutesBefore.toLong()
                ScheduleCalc.nextSession(days, time, now.plusMinutes(lead))?.let {
                    alarms += Alarm(CODE_DIALYSIS, KIND_DIALYSIS, it.minusMinutes(lead))
                }
            }
            if (s.prepReminderEnabled) {
                val prep = LocalTime.of(s.prepHour, s.prepMinute)
                (0..8L).map { now.toLocalDate().plusDays(it) }
                    .filter { it.dayOfWeek in days }
                    .map { it.minusDays(1).atTime(prep) }
                    .firstOrNull { it.isAfter(now) }
                    ?.let { alarms += Alarm(CODE_PREP, KIND_PREP, it) }
            }
        }

        for (med in c.db.meds().all()) {
            if (!med.active || !med.reminders) continue
            ScheduleCalc.parseTimes(med.times).take(16).forEachIndexed { index, t ->
                var at = now.toLocalDate().atTime(t)
                if (!at.isAfter(now)) at = at.plusDays(1)
                alarms += Alarm(CODE_MED + (med.id * 16 + index).toInt(), KIND_MED, at, medId = med.id)
            }
        }

        val p = c.settings.prayer.value
        if (p.reminders.isNotEmpty()) {
            val today = prayerTimes(p, now.toLocalDate())
            val tomorrow = prayerTimes(p, now.toLocalDate().plusDays(1))
            for (name in p.reminders) {
                val prayer = runCatching { Prayer.valueOf(name) }.getOrNull() ?: continue
                val todayAt = today[prayer]?.let { now.toLocalDate().atTime(it) }
                val at = if (todayAt != null && todayAt.isAfter(now)) todayAt
                else tomorrow[prayer]?.let { now.toLocalDate().plusDays(1).atTime(it) }
                if (at != null) alarms += Alarm(CODE_PRAYER + prayer.ordinal, KIND_PRAYER, at, prayer = name)
            }
        }
        return alarms
    }

    fun prayerTimes(p: PrayerSettings, date: LocalDate): Map<Prayer, LocalTime?> {
        val city = Cities.byId(p.cityId) ?: Cities.all.first()
        val lat = p.customLatitude ?: city.latitude
        val lon = p.customLongitude ?: city.longitude
        val method = runCatching { CalcMethod.valueOf(p.method) }.getOrDefault(CalcMethod.ALGERIA)
        val asr = runCatching { AsrMethod.valueOf(p.asr) }.getOrDefault(AsrMethod.STANDARD)
        val offsets = p.offsets.mapNotNull { (k, v) -> runCatching { Prayer.valueOf(k) }.getOrNull()?.let { it to v } }.toMap()
        return PrayerTimes.compute(date, lat, lon, ZoneId.systemDefault(), method, asr, offsets)
    }

    private fun pendingIntent(context: Context, code: Int, alarm: Alarm?): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_ALARM)
            .setData(Uri.parse("nephrocare://alarm/$code"))
        if (alarm != null) {
            intent.putExtra(EXTRA_KIND, alarm.kind)
                .putExtra(EXTRA_CODE, alarm.code)
                .putExtra(EXTRA_MED_ID, alarm.medId)
                .putExtra(EXTRA_AT, millis(alarm.at))
                .putExtra(EXTRA_PRAYER, alarm.prayer)
        }
        return PendingIntent.getBroadcast(
            context, code, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun canUseExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
    }

    private fun set(context: Context, am: AlarmManager, alarm: Alarm) {
        val pi = pendingIntent(context, alarm.code, alarm)
        val at = millis(alarm.at)
        try {
            if (canUseExactAlarms(context)) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }
}
