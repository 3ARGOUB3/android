package com.nephrocare.data

import androidx.room.withTransaction
import com.nephrocare.AppContainer
import com.nephrocare.LocaleHelper
import com.nephrocare.R
import com.nephrocare.core.ScheduleCalc
import com.nephrocare.core.StockCalc
import com.nephrocare.core.fmt
import com.nephrocare.reminders.Notifications
import kotlin.math.floor

/** Recording intakes and keeping the medicine stock in step with them. */
object MedStock {
    const val NOTIFICATION_STOCK = 1002

    fun timesPerDay(med: Medication) = ScheduleCalc.parseTimes(med.times).size

    fun daysLeft(med: Medication): Double? = StockCalc.daysLeft(med.stock, med.perDose, timesPerDay(med))

    fun isLow(med: Medication): Boolean =
        med.active && StockCalc.isLow(med.stock, med.perDose, timesPerDay(med), med.refillDays)

    /**
     * Records a dose once per scheduled time. A taken dose removes [Medication.perDose] units from the
     * stock; if that makes the stock low, a reminder is shown right away.
     * Returns false when this dose was already recorded.
     */
    suspend fun recordIntake(app: AppContainer, med: Medication, scheduledAt: Long?, status: String): Boolean {
        val db = app.db
        var becameLow = false
        val recorded = db.withTransaction {
            if (scheduledAt != null && db.intakes().countFor(med.id, scheduledAt) > 0) return@withTransaction false
            db.intakes().insert(
                MedIntake(
                    medId = med.id, medName = med.name, dose = med.dose,
                    scheduledAt = scheduledAt, takenAt = System.currentTimeMillis(), status = status,
                ),
            )
            if (status == "TAKEN") {
                val before = db.meds().byId(med.id)
                db.meds().useStock(med.id, med.perDose)
                val after = db.meds().byId(med.id)
                becameLow = before != null && after != null && !isLow(before) && isLow(after)
            }
            true
        }
        if (becameLow) db.meds().byId(med.id)?.let { notifyLow(app, listOf(it)) }
        return recorded
    }

    /** Deleting a taken dose from the history puts its units back. */
    suspend fun deleteIntake(app: AppContainer, intake: MedIntake) {
        app.db.withTransaction {
            app.db.intakes().delete(intake)
            if (intake.status == "TAKEN") {
                val med = app.db.meds().byId(intake.medId)
                if (med != null) app.db.meds().restoreStock(med.id, med.perDose)
            }
        }
    }

    suspend fun lowStock(app: AppContainer): List<Medication> = app.db.meds().all().filter { isLow(it) }

    /** Posts the "running low" reminder. Medicine names only appear when the user allowed it. */
    fun notifyLow(app: AppContainer, meds: List<Medication>) {
        if (meds.isEmpty()) return
        val c = LocaleHelper.localized(app.context, app.language())
        val details = app.settings.settings.value.notificationDetails
        val text = if (details) {
            meds.joinToString("\n") { m ->
                val days = daysLeft(m)
                if (days != null) c.getString(R.string.notif_stock_line_days, m.name, floor(days).toInt())
                else c.getString(R.string.notif_stock_line_units, m.name, fmt(m.stock ?: 0.0, 1))
            }
        } else {
            c.getString(R.string.notif_stock_text_generic, meds.size)
        }
        Notifications.show(
            app.context, NOTIFICATION_STOCK, Notifications.CHANNEL_MEDS,
            c.getString(R.string.notif_stock_title), text, "meds",
        )
    }
}
