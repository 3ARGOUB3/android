package com.nephrocare

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.test.core.app.ApplicationProvider
import com.nephrocare.core.Labs
import com.nephrocare.data.BackupManager
import com.nephrocare.data.BpEntry
import com.nephrocare.data.Center
import com.nephrocare.data.DiaryEntry
import com.nephrocare.data.DialysisSession
import com.nephrocare.data.DoctorQuestion
import com.nephrocare.data.FistulaCheck
import com.nephrocare.data.FluidEntry
import com.nephrocare.data.LabResult
import com.nephrocare.data.MedIntake
import com.nephrocare.data.Medication
import com.nephrocare.data.WeightEntry
import com.nephrocare.reminders.ReminderScheduler
import com.nephrocare.ui.NephroRoot
import com.nephrocare.ui.Routes
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

private const val DAY = 24L * 3600 * 1000

fun container(): AppContainer = NephroApp.container(ApplicationProvider.getApplicationContext())

/** Fills every table with a little data so the screens draw their charts and lists. */
fun seed(app: AppContainer) = runBlocking {
    val now = System.currentTimeMillis()
    val db = app.db
    db.weights().insertAll(
        listOf(
            WeightEntry(time = now - 10 * DAY, kind = "DRY", kg = 68.5),
            WeightEntry(time = now - 4 * DAY, kind = "PRE", kg = 70.9),
            WeightEntry(time = now - 4 * DAY + 4 * 3600_000, kind = "POST", kg = 68.7),
            WeightEntry(time = now - 2 * DAY, kind = "PRE", kg = 71.2),
            WeightEntry(time = now - 2 * DAY + 4 * 3600_000, kind = "POST", kg = 68.9),
            WeightEntry(time = now - DAY, kind = "HOME", kg = 69.8),
        ),
    )
    db.bp().insertAll(
        listOf(
            BpEntry(time = now - 3 * DAY, systolic = 150, diastolic = 85, pulse = 78, context = "PRE"),
            BpEntry(time = now - 2 * DAY, systolic = 118, diastolic = 70, pulse = 82, context = "POST"),
            BpEntry(time = now - DAY, systolic = 185, diastolic = 112, pulse = 90, context = "HOME"),
        ),
    )
    Labs.all.forEach { t ->
        db.labs().insert(LabResult(code = t.code, value = (t.general.high ?: 10.0) * 1.1, unit = t.mainUnit, date = now - 30 * DAY))
        db.labs().insert(LabResult(code = t.code, value = (t.general.low ?: 1.0) * 0.9, unit = t.mainUnit, date = now - 2 * DAY))
    }
    val medId = db.meds().insert(Medication(name = "Calcium carbonate", dose = "500 mg", times = "08:00;13:00;20:00", instructions = "With meals"))
    db.intakes().insert(MedIntake(medId = medId, medName = "Calcium carbonate", dose = "500 mg", scheduledAt = now - DAY, takenAt = now - DAY))
    db.sessions().insert(DialysisSession(date = now - 2 * DAY, durationMin = 240, preWeight = 71.2, postWeight = 68.9, preSystolic = 150, preDiastolic = 85))
    db.diary().insert(DiaryEntry(date = now - DAY, fatigue = 6, appetite = 1, sleepHours = 5.5, symptoms = "ITCH,CRAMPS", note = "Cramps at the end"))
    db.questions().insert(DoctorQuestion(created = now, text = "Can I eat dates?"))
    db.fluids().insertAll(listOf(FluidEntry(time = now, ml = 200), FluidEntry(time = now - DAY, ml = 750, kind = "HOT")))
    db.fistula().insert(FistulaCheck(time = now, thrill = true, redness = true))
    db.centers().insert(Center(name = "Centre d'hémodialyse", city = "Alger", phone = "021000000"))
    app.settings.updateProfile { it.copy(name = "Amina", fluidTargetMl = 700, nephrologistPhone = "0550000000") }
    app.settings.updateSchedule { it.copy(days = listOf(6, 1, 3)) }
    app.settings.updatePrayer { it.copy(reminders = listOf("FAJR", "MAGHRIB")) }
}

/** Opens every screen, with data, in each content language: none may crash. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
class ScreensSmokeTest(private val route: String, private val language: String) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun screenRenders() {
        val app = container()
        app.settings.updateSettings { it.copy(onboardingDone = true, language = language) }
        seed(app)
        compose.setContent { NephroRoot(app, pendingRoute = route, onRouteHandled = {}) }
        compose.waitForIdle()
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0} {1}")
        fun params(): List<Array<Any>> {
            val routes = Routes.simple + listOf(
                "lab/K", "lab/UREA", "lab/PTH", "article/ckd_what", "article/nut_potassium", "article/em_no_thrill",
                "surah/1", "surah/2", "illustration/kidney", "illustration/fistula", "illustration/dialysis", "med/0", "med/1",
            )
            return routes.flatMap { r -> listOf("en", "ar", "fr", "es").map { arrayOf<Any>(r, it) } }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppLogicTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun onboardingAndLockScreenRender() {
        val app = container()
        compose.setContent { NephroRoot(app, null, {}) }
        compose.waitForIdle()
        assertTrue(compose.onAllNodesWithText("العربية").fetchSemanticsNodes().isNotEmpty())
    }

    @Test
    fun contentIsComplete() {
        val app = container()
        for (lang in listOf("ar", "fr", "en", "es")) {
            val pack = app.content.pack(lang)
            assertTrue("$lang articles", pack.articles.size >= 60)
            assertEquals("$lang labs", Labs.all.map { it.code }, pack.labs.map { it.code })
            assertTrue("$lang tips", pack.tips.isNotEmpty())
            assertTrue(app.content.searchIndex(lang).search(if (lang == "ar") "البوتاسيوم" else "potass").isNotEmpty())
        }
        assertTrue(app.content.foods.foods.size > 50)
        assertEquals(114, app.content.surahs.size)
        assertEquals(286, app.content.surah(2, "fr").size)
        assertEquals(2, app.content.quranRef("2:285-286", "en").size)
        app.content.adhkar.items.forEach { d ->
            if (d.q != null) assertTrue(d.id, app.content.quranRef(d.q!!, "ar").isNotEmpty())
            else assertTrue(d.id, d.ar.isNotBlank() && d.tr.keys.containsAll(listOf("fr", "en", "es")))
        }
    }

    @Test
    fun backupRoundTrip() = runBlocking {
        val app = container()
        seed(app)
        val before = BackupManager.snapshot(app)
        BackupManager.deleteEverything(app)
        assertTrue(app.db.weights().all().isEmpty())
        assertEquals("", app.settings.profile.value.name)
        BackupManager.restore(app, before)
        val after = BackupManager.snapshot(app)
        assertEquals(before.weights, after.weights)
        assertEquals(before.medications, after.medications)
        assertEquals(before.labs.size, after.labs.size)
        assertEquals("Amina", app.settings.profile.value.name)
    }

    @Test
    fun remindersAreComputed() = runBlocking {
        val app = container()
        seed(app)
        val now = LocalDateTime.of(2024, 6, 17, 9, 0) // Monday
        val alarms = ReminderScheduler.computeAlarms(app, now)
        val kinds = alarms.groupBy { it.kind }
        assertEquals(3, kinds[ReminderScheduler.KIND_MED]?.size)
        assertEquals(LocalDateTime.of(2024, 6, 17, 13, 0), kinds[ReminderScheduler.KIND_MED]!!.minOf { it.at })
        // Next session Wednesday 08:00, reminded 60 minutes before.
        assertEquals(LocalDateTime.of(2024, 6, 19, 7, 0), kinds[ReminderScheduler.KIND_DIALYSIS]!!.single().at)
        // Bag reminder the evening before: Tuesday 20:00.
        assertEquals(LocalDateTime.of(2024, 6, 18, 20, 0), kinds[ReminderScheduler.KIND_PREP]!!.single().at)
        assertEquals(2, kinds[ReminderScheduler.KIND_PRAYER]?.size)
        assertEquals(alarms.size, alarms.map { it.code }.toSet().size)
    }
}
