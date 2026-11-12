package com.nephrocare.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/** kind: DRY (prescribed by the team), PRE / POST (around a session) or HOME. */
@Serializable
@Entity(tableName = "weights", indices = [Index("time")])
data class WeightEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val kind: String,
    val kg: Double,
    val sessionId: Long? = null,
    val note: String = "",
)

/** context: PRE, POST, HOME or OTHER. */
@Serializable
@Entity(tableName = "blood_pressure", indices = [Index("time")])
data class BpEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val systolic: Int,
    val diastolic: Int,
    val pulse: Int? = null,
    val context: String = "HOME",
    val note: String = "",
)

@Serializable
@Entity(tableName = "lab_results", indices = [Index("code"), Index("date")])
data class LabResult(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val value: Double,
    val unit: String,
    val date: Long,
    val note: String = "",
)

@Serializable
@Entity(tableName = "dialysis_sessions", indices = [Index("date")])
data class DialysisSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val durationMin: Int? = null,
    val preWeight: Double? = null,
    val postWeight: Double? = null,
    val preSystolic: Int? = null,
    val preDiastolic: Int? = null,
    val postSystolic: Int? = null,
    val postDiastolic: Int? = null,
    val note: String = "",
)

/** times: "08:00;20:00". The dose is copied exactly as prescribed; the app never computes doses. */
@Serializable
@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dose: String,
    val form: String = "",
    val times: String = "",
    val instructions: String = "",
    val active: Boolean = true,
    val reminders: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
)

/** status: TAKEN or SKIPPED. */
@Serializable
@Entity(tableName = "medication_intakes", indices = [Index("medId"), Index("takenAt")])
data class MedIntake(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val medId: Long,
    val medName: String,
    val dose: String,
    val scheduledAt: Long? = null,
    val takenAt: Long,
    val status: String = "TAKEN",
)

/** fatigue 0..10, appetite 0 poor / 1 average / 2 good, sleepQuality 0..2, symptoms: comma-separated codes. */
@Serializable
@Entity(tableName = "diary", indices = [Index("date")])
data class DiaryEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,
    val fatigue: Int = 0,
    val appetite: Int = 2,
    val sleepHours: Double? = null,
    val sleepQuality: Int = 2,
    val symptoms: String = "",
    val note: String = "",
)

@Serializable
@Entity(tableName = "doctor_questions")
data class DoctorQuestion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val created: Long,
    val text: String,
    val answered: Boolean = false,
    val answer: String = "",
)

/** kind: WATER, HOT, SOUP, MILK, OTHER. */
@Serializable
@Entity(tableName = "fluids", indices = [Index("time")])
data class FluidEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val ml: Int,
    val kind: String = "WATER",
)

@Serializable
@Entity(tableName = "fistula_checks", indices = [Index("time")])
data class FistulaCheck(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val time: Long,
    val thrill: Boolean,
    val redness: Boolean = false,
    val swelling: Boolean = false,
    val pain: Boolean = false,
    val note: String = "",
)

/** type: CENTER, HOSPITAL or NEPHROLOGIST. */
@Serializable
@Entity(tableName = "centers")
data class Center(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: String = "CENTER",
    val city: String = "",
    val address: String = "",
    val phone: String = "",
    val notes: String = "",
)

/** A video from the phone (content:// uri) or a web link, optionally with a subtitle file. */
@Serializable
@Entity(tableName = "videos")
data class VideoItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val uri: String,
    val subtitleUri: String? = null,
    val language: String = "",
    val category: String = "GENERAL",
    val description: String = "",
)
