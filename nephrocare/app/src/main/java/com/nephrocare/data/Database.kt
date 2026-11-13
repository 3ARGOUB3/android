package com.nephrocare.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

interface BaseDao<T> {
    @Insert
    suspend fun insert(item: T): Long

    @Insert
    suspend fun insertAll(items: List<T>)

    @Update
    suspend fun update(item: T)

    @Delete
    suspend fun delete(item: T)
}

@Dao
interface WeightDao : BaseDao<WeightEntry> {
    @Query("SELECT * FROM weights ORDER BY time DESC")
    fun observeAll(): Flow<List<WeightEntry>>

    @Query("SELECT * FROM weights ORDER BY time DESC")
    suspend fun all(): List<WeightEntry>

    @Query("DELETE FROM weights WHERE sessionId = :sessionId")
    suspend fun deleteForSession(sessionId: Long)

    @Query("DELETE FROM weights")
    suspend fun clear()
}

@Dao
interface BpDao : BaseDao<BpEntry> {
    @Query("SELECT * FROM blood_pressure ORDER BY time DESC")
    fun observeAll(): Flow<List<BpEntry>>

    @Query("SELECT * FROM blood_pressure ORDER BY time DESC")
    suspend fun all(): List<BpEntry>

    @Query("DELETE FROM blood_pressure")
    suspend fun clear()
}

@Dao
interface LabDao : BaseDao<LabResult> {
    @Query("SELECT * FROM lab_results ORDER BY date DESC")
    fun observeAll(): Flow<List<LabResult>>

    @Query("SELECT * FROM lab_results ORDER BY date DESC")
    suspend fun all(): List<LabResult>

    @Query("DELETE FROM lab_results")
    suspend fun clear()
}

@Dao
interface SessionDao : BaseDao<DialysisSession> {
    @Query("SELECT * FROM dialysis_sessions ORDER BY date DESC")
    fun observeAll(): Flow<List<DialysisSession>>

    @Query("SELECT * FROM dialysis_sessions ORDER BY date DESC")
    suspend fun all(): List<DialysisSession>

    @Query("DELETE FROM dialysis_sessions")
    suspend fun clear()
}

@Dao
interface MedicationDao : BaseDao<Medication> {
    @Query("SELECT * FROM medications ORDER BY active DESC, name COLLATE NOCASE")
    fun observeAll(): Flow<List<Medication>>

    @Query("SELECT * FROM medications ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<Medication>

    @Query("SELECT * FROM medications WHERE id = :id")
    suspend fun byId(id: Long): Medication?

    /** Removes units taken; never goes below zero. Does nothing when the stock is not tracked. */
    @Query("UPDATE medications SET stock = MAX(stock - :units, 0) WHERE id = :id AND stock IS NOT NULL")
    suspend fun useStock(id: Long, units: Double)

    /** Puts units back (undoing an intake) when the stock is tracked. */
    @Query("UPDATE medications SET stock = stock + :units WHERE id = :id AND stock IS NOT NULL")
    suspend fun restoreStock(id: Long, units: Double)

    /** Adds a refill; starts tracking the stock if it was not tracked. */
    @Query("UPDATE medications SET stock = COALESCE(stock, 0) + :units WHERE id = :id")
    suspend fun refill(id: Long, units: Double)

    @Query("DELETE FROM medications")
    suspend fun clear()
}

@Dao
interface IntakeDao : BaseDao<MedIntake> {
    @Query("SELECT * FROM medication_intakes ORDER BY takenAt DESC")
    fun observeAll(): Flow<List<MedIntake>>

    @Query("SELECT * FROM medication_intakes ORDER BY takenAt DESC")
    suspend fun all(): List<MedIntake>

    @Query("SELECT COUNT(*) FROM medication_intakes WHERE medId = :medId AND scheduledAt = :scheduledAt")
    suspend fun countFor(medId: Long, scheduledAt: Long): Int

    @Query("DELETE FROM medication_intakes")
    suspend fun clear()
}

@Dao
interface DiaryDao : BaseDao<DiaryEntry> {
    @Query("SELECT * FROM diary ORDER BY date DESC")
    fun observeAll(): Flow<List<DiaryEntry>>

    @Query("SELECT * FROM diary ORDER BY date DESC")
    suspend fun all(): List<DiaryEntry>

    @Query("DELETE FROM diary")
    suspend fun clear()
}

@Dao
interface QuestionDao : BaseDao<DoctorQuestion> {
    @Query("SELECT * FROM doctor_questions ORDER BY answered, created DESC")
    fun observeAll(): Flow<List<DoctorQuestion>>

    @Query("SELECT * FROM doctor_questions ORDER BY created DESC")
    suspend fun all(): List<DoctorQuestion>

    @Query("DELETE FROM doctor_questions")
    suspend fun clear()
}

@Dao
interface FluidDao : BaseDao<FluidEntry> {
    @Query("SELECT * FROM fluids ORDER BY time DESC")
    fun observeAll(): Flow<List<FluidEntry>>

    @Query("SELECT * FROM fluids ORDER BY time DESC")
    suspend fun all(): List<FluidEntry>

    @Query("DELETE FROM fluids")
    suspend fun clear()
}

@Dao
interface FistulaDao : BaseDao<FistulaCheck> {
    @Query("SELECT * FROM fistula_checks ORDER BY time DESC")
    fun observeAll(): Flow<List<FistulaCheck>>

    @Query("SELECT * FROM fistula_checks ORDER BY time DESC")
    suspend fun all(): List<FistulaCheck>

    @Query("DELETE FROM fistula_checks")
    suspend fun clear()
}

@Dao
interface CenterDao : BaseDao<Center> {
    @Query("SELECT * FROM centers ORDER BY city COLLATE NOCASE, name COLLATE NOCASE")
    fun observeAll(): Flow<List<Center>>

    @Query("SELECT * FROM centers ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<Center>

    @Query("DELETE FROM centers")
    suspend fun clear()
}

@Dao
interface VideoDao : BaseDao<VideoItem> {
    @Query("SELECT * FROM videos ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<VideoItem>>

    @Query("SELECT * FROM videos ORDER BY title COLLATE NOCASE")
    suspend fun all(): List<VideoItem>

    @Query("SELECT * FROM videos WHERE id = :id")
    suspend fun byId(id: Long): VideoItem?

    @Query("DELETE FROM videos")
    suspend fun clear()
}

@Database(
    entities = [
        WeightEntry::class, BpEntry::class, LabResult::class, DialysisSession::class,
        Medication::class, MedIntake::class, DiaryEntry::class, DoctorQuestion::class,
        FluidEntry::class, FistulaCheck::class, Center::class, VideoItem::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun weights(): WeightDao
    abstract fun bp(): BpDao
    abstract fun labs(): LabDao
    abstract fun sessions(): SessionDao
    abstract fun meds(): MedicationDao
    abstract fun intakes(): IntakeDao
    abstract fun diary(): DiaryDao
    abstract fun questions(): QuestionDao
    abstract fun fluids(): FluidDao
    abstract fun fistula(): FistulaDao
    abstract fun centers(): CenterDao
    abstract fun videos(): VideoDao

    suspend fun clearEverything() {
        weights().clear(); bp().clear(); labs().clear(); sessions().clear()
        meds().clear(); intakes().clear(); diary().clear(); questions().clear()
        fluids().clear(); fistula().clear(); centers().clear(); videos().clear()
    }

    companion object {
        /** Version 2 adds medicine stock tracking. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medications ADD COLUMN stock REAL")
                db.execSQL("ALTER TABLE medications ADD COLUMN perDose REAL NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE medications ADD COLUMN refillDays INTEGER NOT NULL DEFAULT 5")
            }
        }

        fun create(context: Context, inMemory: Boolean = false): AppDatabase {
            val builder = if (inMemory) {
                Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries()
            } else {
                Room.databaseBuilder(context, AppDatabase::class.java, "nephrocare.db")
                    .addMigrations(MIGRATION_1_2)
            }
            return builder.build()
        }
    }
}
