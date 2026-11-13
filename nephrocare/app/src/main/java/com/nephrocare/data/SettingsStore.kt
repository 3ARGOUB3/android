package com.nephrocare.data

import android.content.Context
import com.nephrocare.core.contentJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
    val name: String = "",
    val birthYear: Int? = null,
    val sex: String = "",
    /** HD (haemodialysis), PD (peritoneal dialysis), CKD (not on dialysis), TX (transplant) */
    val treatment: String = "HD",
    val dialysisSince: String = "",
    val center: String = "",
    val nephrologist: String = "",
    val nephrologistPhone: String = "",
    val bloodType: String = "",
    val allergies: String = "",
    val emergencyName: String = "",
    val emergencyPhone: String = "",
    /** Daily fluid allowance in ml, only as prescribed by the medical team. */
    val fluidTargetMl: Int? = null,
)

@Serializable
data class DialysisSchedule(
    /** java.time.DayOfWeek values 1 (Monday) .. 7 (Sunday). */
    val days: List<Int> = emptyList(),
    val hour: Int = 8,
    val minute: Int = 0,
    val durationMin: Int = 240,
    val reminderEnabled: Boolean = true,
    val reminderMinutesBefore: Int = 60,
    val prepReminderEnabled: Boolean = true,
    val prepHour: Int = 20,
    val prepMinute: Int = 0,
)

@Serializable
data class PrayerSettings(
    val cityId: String = "algiers",
    val customLatitude: Double? = null,
    val customLongitude: Double? = null,
    val method: String = "ALGERIA",
    val asr: String = "STANDARD",
    /** Prayer names (FAJR, DHUHR, ...) with a reminder. */
    val reminders: List<String> = emptyList(),
    val offsets: Map<String, Int> = emptyMap(),
)

@Serializable
data class AppSettings(
    /** "", "ar", "fr", "en" or "es"; "" follows the phone. */
    val language: String = "",
    /** system, light or dark */
    val theme: String = "system",
    val textScale: Float = 1.0f,
    val onboardingDone: Boolean = false,
    /** Show medicine names in notifications (off by default for privacy). */
    val notificationDetails: Boolean = false,
    val pinHash: String? = null,
    val pinSalt: String? = null,
)

/** Small settings kept as JSON in SharedPreferences, exposed as StateFlows. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("nephrocare_settings", Context.MODE_PRIVATE)

    val settings = MutableStateFlow(read("settings", AppSettings.serializer()) ?: AppSettings())
    val profile = MutableStateFlow(read("profile", Profile.serializer()) ?: Profile())
    val schedule = MutableStateFlow(read("schedule", DialysisSchedule.serializer()) ?: DialysisSchedule())
    val prayer = MutableStateFlow(read("prayer", PrayerSettings.serializer()) ?: PrayerSettings())

    fun updateSettings(f: (AppSettings) -> AppSettings) = save("settings", AppSettings.serializer(), settings, f)
    fun updateProfile(f: (Profile) -> Profile) = save("profile", Profile.serializer(), profile, f)
    fun updateSchedule(f: (DialysisSchedule) -> DialysisSchedule) = save("schedule", DialysisSchedule.serializer(), schedule, f)
    fun updatePrayer(f: (PrayerSettings) -> PrayerSettings) = save("prayer", PrayerSettings.serializer(), prayer, f)

    /** Remembers which alarm request codes are set so stale ones can be cancelled. */
    var scheduledAlarmCodes: Set<String>
        get() = prefs.getStringSet("alarm_codes", emptySet()) ?: emptySet()
        set(value) { prefs.edit().putStringSet("alarm_codes", value).apply() }

    fun clearAll() {
        val keep = settings.value.copy(pinHash = null, pinSalt = null)
        val codes = scheduledAlarmCodes
        prefs.edit().clear().apply()
        scheduledAlarmCodes = codes
        profile.value = Profile()
        schedule.value = DialysisSchedule()
        prayer.value = PrayerSettings()
        updateSettings { keep }
    }

    private fun <T> read(key: String, serializer: KSerializer<T>): T? = try {
        prefs.getString(key, null)?.let { contentJson.decodeFromString(serializer, it) }
    } catch (e: Exception) {
        null
    }

    private fun <T> save(key: String, serializer: KSerializer<T>, flow: MutableStateFlow<T>, f: (T) -> T) {
        val next = f(flow.value)
        flow.value = next
        prefs.edit().putString(key, contentJson.encodeToString(serializer, next)).apply()
    }
}
