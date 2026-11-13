package com.nephrocare.reminders

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nephrocare.LocaleHelper
import com.nephrocare.MainActivity
import com.nephrocare.NephroApp
import com.nephrocare.R

object Notifications {
    const val CHANNEL_DIALYSIS = "dialysis"
    const val CHANNEL_MEDS = "medication"
    const val CHANNEL_PRAYER = "prayer"
    const val CHANNEL_FLUIDS = "fluids"
    const val EXTRA_ROUTE = "route"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val c = LocaleHelper.localized(context, NephroApp.container(context).language())
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        listOf(
            NotificationChannel(CHANNEL_DIALYSIS, c.getString(R.string.channel_dialysis), NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CHANNEL_MEDS, c.getString(R.string.channel_meds), NotificationManager.IMPORTANCE_HIGH),
            NotificationChannel(CHANNEL_PRAYER, c.getString(R.string.channel_prayer), NotificationManager.IMPORTANCE_DEFAULT),
            NotificationChannel(CHANNEL_FLUIDS, c.getString(R.string.channel_fluids), NotificationManager.IMPORTANCE_DEFAULT),
        ).forEach {
            // Lock screens only show the generic public version of each notification.
            it.lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            nm.createNotificationChannel(it)
        }
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun openAppIntent(context: Context, route: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Posts a notification. The public (lock screen) version never contains health details:
     * it only says that NephroCare has a reminder.
     */
    @SuppressLint("MissingPermission")
    fun show(
        context: Context,
        id: Int,
        channel: String,
        title: String,
        text: String,
        route: String,
        actions: List<NotificationCompat.Action> = emptyList(),
    ) {
        if (!canPost(context)) return
        val c = LocaleHelper.localized(context, NephroApp.container(context).language())
        val publicVersion = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(c.getString(R.string.app_name))
            .setContentText(c.getString(R.string.notif_public_text))
            .build()
        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF1E6FB8.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(publicVersion)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, route, id))
        actions.forEach { builder.addAction(it) }
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    fun cancel(context: Context, id: Int) = NotificationManagerCompat.from(context).cancel(id)
}
