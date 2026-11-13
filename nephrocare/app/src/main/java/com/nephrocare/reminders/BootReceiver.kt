package com.nephrocare.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nephrocare.NephroApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Alarms do not survive a reboot or a clock change: set them again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ReminderScheduler.rescheduleAll(NephroApp.container(context))
            } finally {
                pending.finish()
            }
        }
    }
}
