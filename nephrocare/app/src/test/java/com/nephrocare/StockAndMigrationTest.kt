package com.nephrocare

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.core.app.ApplicationProvider
import com.nephrocare.data.AppDatabase
import com.nephrocare.data.BpEntry
import com.nephrocare.data.Center
import com.nephrocare.data.DiaryEntry
import com.nephrocare.data.DialysisSession
import com.nephrocare.data.DoctorQuestion
import com.nephrocare.data.FistulaCheck
import com.nephrocare.data.FluidEntry
import com.nephrocare.data.LabResult
import com.nephrocare.data.MedIntake
import com.nephrocare.data.MedStock
import com.nephrocare.data.Medication
import com.nephrocare.data.VideoItem
import com.nephrocare.data.WeightEntry
import com.nephrocare.reminders.ReminderScheduler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/** The medications table exactly as it was in database version 1 (NephroCare 1.0). */
@Entity(tableName = "medications")
data class MedicationV1(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dose: String,
    val form: String = "",
    val times: String = "",
    val instructions: String = "",
    val active: Boolean = true,
    val reminders: Boolean = true,
    val createdAt: Long = 0,
)

@Dao
interface MedicationV1Dao {
    @Insert
    fun insert(med: MedicationV1): Long
}

/** Database version 1: every other table is unchanged since 1.0. */
@Database(
    entities = [
        WeightEntry::class, BpEntry::class, LabResult::class, DialysisSession::class,
        MedicationV1::class, MedIntake::class, DiaryEntry::class, DoctorQuestion::class,
        FluidEntry::class, FistulaCheck::class, Center::class, VideoItem::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class DatabaseV1 : RoomDatabase() {
    abstract fun meds(): MedicationV1Dao
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StockAndMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun migrationFromVersion1KeepsMedicinesAndAddsStockColumns() {
        val name = "migration-test.db"
        context.deleteDatabase(name)
        val v1 = Room.databaseBuilder(context, DatabaseV1::class.java, name).allowMainThreadQueries().build()
        v1.meds().insert(MedicationV1(name = "Sevelamer", dose = "800 mg", times = "08:00;13:00;20:00"))
        v1.close()

        // Room checks every table against the version 2 entities after migrating and throws on any mismatch.
        val v2 = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        val med = runBlocking { v2.meds().all() }.single()
        assertEquals("Sevelamer", med.name)
        assertNull(med.stock)
        assertEquals(1.0, med.perDose, 0.0)
        assertEquals(5, med.refillDays)
        v2.close()
    }

    @Test
    fun takingDosesLowersStockAndDeletingRestoresIt() = runBlocking {
        val app = container()
        val id = app.db.meds().insert(
            Medication(name = "Calcium", dose = "1 tablet", times = "08:00;13:00;20:00", stock = 16.0, perDose = 1.0, refillDays = 5),
        )
        val med = app.db.meds().byId(id)!!
        assertFalse(MedStock.isLow(med))
        assertEquals(16.0 / 3, MedStock.daysLeft(med)!!, 1e-9)

        assertTrue(MedStock.recordIntake(app, med, 1000L, "TAKEN"))
        assertFalse("the same dose is recorded once", MedStock.recordIntake(app, med, 1000L, "TAKEN"))
        val after = app.db.meds().byId(id)!!
        assertEquals(15.0, after.stock!!, 0.0)
        assertTrue("15 tablets = 5 days: time to renew", MedStock.isLow(after))

        MedStock.recordIntake(app, after, 2000L, "SKIPPED")
        assertEquals("a skipped dose does not use stock", 15.0, app.db.meds().byId(id)!!.stock!!, 0.0)

        val taken = app.db.intakes().all().first { it.status == "TAKEN" }
        MedStock.deleteIntake(app, taken)
        assertEquals(16.0, app.db.meds().byId(id)!!.stock!!, 0.0)

        app.db.meds().refill(id, 30.0)
        assertEquals(46.0, app.db.meds().byId(id)!!.stock!!, 0.0)
    }

    @Test
    fun stockAndFluidRemindersAreScheduled() = runBlocking {
        val app = container()
        val now = LocalDateTime.of(2024, 6, 17, 11, 0)
        assertTrue(ReminderScheduler.computeAlarms(app, now).none { it.kind == ReminderScheduler.KIND_STOCK || it.kind == ReminderScheduler.KIND_FLUID })

        app.db.meds().insert(Medication(name = "Iron", dose = "1", times = "08:00", stock = 2.0, refillDays = 5))
        app.settings.updateFluidReminder { it.copy(enabled = true, startHour = 9, endHour = 21, intervalHours = 3) }
        val alarms = ReminderScheduler.computeAlarms(app, now)
        // Past 10:00, so the daily stock check is tomorrow; the next fluid reminder is at 12:00.
        assertEquals(LocalDateTime.of(2024, 6, 18, 10, 0), alarms.single { it.kind == ReminderScheduler.KIND_STOCK }.at)
        assertEquals(LocalDateTime.of(2024, 6, 17, 12, 0), alarms.single { it.kind == ReminderScheduler.KIND_FLUID }.at)
        assertEquals(alarms.size, alarms.map { it.code }.toSet().size)
    }
}
