package com.nephrocare.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import android.app.PendingIntent
import com.nephrocare.LocaleHelper
import com.nephrocare.NephroApp
import com.nephrocare.R
import com.nephrocare.core.Prayer
import com.nephrocare.data.MedIntake
import com.nephrocare.ui.prayerName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ReminderScheduler.ACTION_ALARM -> onAlarm(context, intent)
                    ACTION_TAKEN -> onTaken(context, intent)
                }
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun onAlarm(context: Context, intent: Intent) {
        val container = NephroApp.container(context)
        val c = LocaleHelper.localized(context, container.language())
        val code = intent.getIntExtra(ReminderScheduler.EXTRA_CODE, 0)
        val at = intent.getLongExtra(ReminderScheduler.EXTRA_AT, System.currentTimeMillis())
        when (intent.getStringExtra(ReminderScheduler.EXTRA_KIND)) {
            ReminderScheduler.KIND_DIALYSIS -> {
                val s = container.settings.schedule.value
                val time = "%02d:%02d".format(s.hour, s.minute)
                Notifications.show(
                    context, code, Notifications.CHANNEL_DIALYSIS,
                    c.getString(R.string.notif_dialysis_title),
                    c.getString(R.string.notif_dialysis_text, time),
                    "schedule",
                )
            }
            ReminderScheduler.KIND_PREP -> Notifications.show(
                context, code, Notifications.CHANNEL_DIALYSIS,
                c.getString(R.string.notif_prep_title),
                c.getString(R.string.notif_prep_text),
                "schedule",
            )
            ReminderScheduler.KIND_MED -> {
                val medId = intent.getLongExtra(ReminderScheduler.EXTRA_MED_ID, 0)
                val med = container.db.meds().byId(medId)
                if (med != null && med.active && med.reminders) {
                    val details = container.settings.settings.value.notificationDetails
                    val text = if (details) c.getString(R.string.notif_med_text_detail, med.name, med.dose)
                    else c.getString(R.string.notif_med_text_generic)
                    val taken = Intent(context, ReminderReceiver::class.java)
                        .setAction(ACTION_TAKEN)
                        .putExtra(EXTRA_NOTIFICATION_ID, code)
                        .putExtra(ReminderScheduler.EXTRA_MED_ID, medId)
                        .putExtra(ReminderScheduler.EXTRA_AT, at)
                    val takenPi = PendingIntent.getBroadcast(
                        context, code, taken,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    )
                    val time = DateTimeFormatter.ofPattern("HH:mm")
                        .format(Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()))
                    Notifications.show(
                        context, code, Notifications.CHANNEL_MEDS,
                        c.getString(R.string.notif_med_title, time), text, "meds",
                        listOf(NotificationCompat.Action(0, c.getString(R.string.action_taken), takenPi)),
                    )
                }
            }
            ReminderScheduler.KIND_PRAYER -> {
                val prayer = runCatching { Prayer.valueOf(intent.getStringExtra(ReminderScheduler.EXTRA_PRAYER) ?: "") }.getOrNull()
                if (prayer != null) {
                    Notifications.show(
                        context, code, Notifications.CHANNEL_PRAYER,
                        c.getString(R.string.notif_prayer_title, prayerName(c, prayer)),
                        c.getString(R.string.notif_prayer_text),
                        "islamic",
                    )
                }
            }
        }
        ReminderScheduler.rescheduleAll(container)
    }

    private suspend fun onTaken(context: Context, intent: Intent) {
        val container = NephroApp.container(context)
        val medId = intent.getLongExtra(ReminderScheduler.EXTRA_MED_ID, 0)
        val at = intent.getLongExtra(ReminderScheduler.EXTRA_AT, 0)
        val med = container.db.meds().byId(medId)
        if (med != null && container.db.intakes().countFor(medId, at) == 0) {
            container.db.intakes().insert(
                MedIntake(medId = medId, medName = med.name, dose = med.dose, scheduledAt = at, takenAt = System.currentTimeMillis()),
            )
        }
        Notifications.cancel(context, intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
    }

    companion object {
        const val ACTION_TAKEN = "com.nephrocare.action.TAKEN"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
    }
}
