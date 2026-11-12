package com.nephrocare

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.nephrocare.data.AppDatabase
import com.nephrocare.data.ContentRepository
import com.nephrocare.data.SUPPORTED_LANGUAGES
import com.nephrocare.data.SettingsStore
import com.nephrocare.reminders.Notifications
import com.nephrocare.reminders.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

/** Everything the screens and receivers share. */
class AppContainer(val context: Context, inMemoryDb: Boolean = false) {
    val db: AppDatabase = AppDatabase.create(context, inMemoryDb)
    val settings = SettingsStore(context)
    val content = ContentRepository(context)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** The content language: the one chosen in the app, else the phone's when supported, else English. */
    fun language(): String {
        val chosen = settings.settings.value.language
        if (chosen in SUPPORTED_LANGUAGES) return chosen
        val system = Locale.getDefault().language
        return if (system in SUPPORTED_LANGUAGES) system else "en"
    }

    fun rescheduleReminders() {
        scope.launch { ReminderScheduler.rescheduleAll(this@AppContainer) }
    }
}

class NephroApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
        container.rescheduleReminders()
    }

    companion object {
        fun container(context: Context): AppContainer =
            (context.applicationContext as NephroApp).container
    }
}

object LocaleHelper {
    fun localeFor(language: String): Locale = when (language) {
        "ar" -> Locale("ar", "DZ")
        "fr" -> Locale.FRENCH
        "es" -> Locale("es")
        else -> Locale.ENGLISH
    }

    /** Applies the in-app language to activities (stored by AppCompat on older Android versions). */
    fun apply(language: String) {
        val list = if (language.isEmpty()) LocaleListCompat.getEmptyLocaleList()
        else LocaleListCompat.forLanguageTags(language)
        AppCompatDelegate.setApplicationLocales(list)
    }

    /** A context whose resources use [language], for notifications built outside an activity. */
    fun localized(context: Context, language: String): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(localeFor(language))
        return context.createConfigurationContext(config)
    }
}
