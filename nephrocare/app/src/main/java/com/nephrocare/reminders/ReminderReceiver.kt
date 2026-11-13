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
import com.nephrocare.data.FluidEntry
import com.nephrocare.data.MedStock
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
                    ACTION_ADD_FLUID -> onAddFluid(context, intent)
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
            ReminderScheduler.KIND_STOCK -> MedStock.notifyLow(container, MedStock.lowStock(container))
            ReminderScheduler.KIND_FLUID -> {
                val today = java.time.LocalDate.now()
                val total = container.db.fluids().all()
                    .filter { java.time.Instant.ofEpochMilli(it.time).atZone(ZoneId.systemDefault()).toLocalDate() == today }
                    .sumOf { it.ml }
                val target = container.settings.profile.value.fluidTargetMl
                val text = when {
                    target == null -> c.getString(R.string.notif_fluid_total, total)
                    total > target -> c.getString(R.string.notif_fluid_over, total, target)
                    else -> c.getString(R.string.notif_fluid_left, total, target, target - total)
                }
                fun add(ml: Int) = NotificationCompat.Action(
                    0, c.getString(R.string.notif_fluid_add, ml),
                    PendingIntent.getBroadcast(
                        context, code * 10 + ml / 100,
                        Intent(context, ReminderReceiver::class.java)
                            .setAction(ACTION_ADD_FLUID)
                            .putExtra(EXTRA_NOTIFICATION_ID, code)
                            .putExtra(EXTRA_ML, ml),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                    ),
                )
                Notifications.show(
                    context, code, Notifications.CHANNEL_FLUIDS,
                    c.getString(R.string.notif_fluid_title), text, "fluids",
                    listOf(add(100), add(200)),
                )
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
        if (med != null) MedStock.recordIntake(container, med, at, "TAKEN")
        Notifications.cancel(context, intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
        ReminderScheduler.rescheduleAll(container)
    }

    private suspend fun onAddFluid(context: Context, intent: Intent) {
        val ml = intent.getIntExtra(EXTRA_ML, 0)
        if (ml > 0) NephroApp.container(context).db.fluids().insert(FluidEntry(time = System.currentTimeMillis(), ml = ml))
        Notifications.cancel(context, intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0))
    }

    companion object {
        const val ACTION_TAKEN = "com.nephrocare.action.TAKEN"
        const val EXTRA_NOTIFICATION_ID = "notificationId"
        const val ACTION_ADD_FLUID = "com.nephrocare.action.ADD_FLUID"
        const val EXTRA_ML = "ml"
    }
}
