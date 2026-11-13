package com.nephrocare.data

import android.net.Uri
import androidx.room.withTransaction
import com.nephrocare.AppContainer
import com.nephrocare.core.BackupCrypto
import com.nephrocare.core.contentJson
import com.nephrocare.reminders.ReminderScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

@Serializable
data class BackupData(
    val version: Int = 1,
    val createdAt: Long = 0,
    val profile: Profile = Profile(),
    val schedule: DialysisSchedule = DialysisSchedule(),
    val prayer: PrayerSettings = PrayerSettings(),
    val fluidReminder: FluidReminder = FluidReminder(),
    val language: String = "",
    val theme: String = "system",
    val textScale: Float = 1f,
    val notificationDetails: Boolean = false,
    val weights: List<WeightEntry> = emptyList(),
    val bloodPressure: List<BpEntry> = emptyList(),
    val labs: List<LabResult> = emptyList(),
    val sessions: List<DialysisSession> = emptyList(),
    val medications: List<Medication> = emptyList(),
    val intakes: List<MedIntake> = emptyList(),
    val diary: List<DiaryEntry> = emptyList(),
    val questions: List<DoctorQuestion> = emptyList(),
    val fluids: List<FluidEntry> = emptyList(),
    val fistulaChecks: List<FistulaCheck> = emptyList(),
    val centers: List<Center> = emptyList(),
    val videos: List<VideoItem> = emptyList(),
)

/** Encrypted backup files: everything the user entered, protected by a password only the user knows. */
object BackupManager {

    suspend fun snapshot(app: AppContainer): BackupData {
        val db = app.db
        val s = app.settings.settings.value
        return BackupData(
            createdAt = System.currentTimeMillis(),
            profile = app.settings.profile.value,
            schedule = app.settings.schedule.value,
            prayer = app.settings.prayer.value,
            fluidReminder = app.settings.fluidReminder.value,
            language = s.language, theme = s.theme, textScale = s.textScale, notificationDetails = s.notificationDetails,
            weights = db.weights().all(), bloodPressure = db.bp().all(), labs = db.labs().all(),
            sessions = db.sessions().all(), medications = db.meds().all(), intakes = db.intakes().all(),
            diary = db.diary().all(), questions = db.questions().all(), fluids = db.fluids().all(),
            fistulaChecks = db.fistula().all(), centers = db.centers().all(), videos = db.videos().all(),
        )
    }

    suspend fun export(app: AppContainer, uri: Uri, password: String) = withContext(Dispatchers.IO) {
        val json = contentJson.encodeToString(BackupData.serializer(), snapshot(app))
        val bytes = BackupCrypto.encrypt(json.toByteArray(Charsets.UTF_8), password)
        app.context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(bytes) }
            ?: error("Cannot write the file")
    }

    /** Replaces all data with the backup. Throws [com.nephrocare.core.WrongPasswordException] on a wrong password. */
    suspend fun import(app: AppContainer, uri: Uri, password: String) = withContext(Dispatchers.IO) {
        val bytes = app.context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot read the file")
        val json = BackupCrypto.decrypt(bytes, password).toString(Charsets.UTF_8)
        restore(app, contentJson.decodeFromString(BackupData.serializer(), json))
    }

    suspend fun restore(app: AppContainer, b: BackupData) {
        val db = app.db
        db.withTransaction {
            db.clearEverything()
            db.weights().insertAll(b.weights); db.bp().insertAll(b.bloodPressure); db.labs().insertAll(b.labs)
            db.sessions().insertAll(b.sessions); db.meds().insertAll(b.medications); db.intakes().insertAll(b.intakes)
            db.diary().insertAll(b.diary); db.questions().insertAll(b.questions); db.fluids().insertAll(b.fluids)
            db.fistula().insertAll(b.fistulaChecks); db.centers().insertAll(b.centers); db.videos().insertAll(b.videos)
        }
        app.settings.updateProfile { b.profile }
        app.settings.updateSchedule { b.schedule }
        app.settings.updatePrayer { b.prayer }
        app.settings.updateFluidReminder { b.fluidReminder }
        app.settings.updateSettings {
            it.copy(theme = b.theme, textScale = b.textScale, notificationDetails = b.notificationDetails)
        }
        ReminderScheduler.rescheduleAll(app)
    }

    /** Deletes every record and setting (the language and the onboarding stay). */
    suspend fun deleteEverything(app: AppContainer) = withContext(Dispatchers.IO) {
        app.db.clearEverything()
        app.settings.clearAll()
        app.context.cacheDir.resolve("reports").deleteRecursively()
        ReminderScheduler.rescheduleAll(app)
    }
}
