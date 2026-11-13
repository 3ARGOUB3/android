package com.nephrocare.core

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

enum class Prayer { FAJR, SUNRISE, DHUHR, ASR, MAGHRIB, ISHA }

/** The five obligatory prayers (sunrise is shown but is not a prayer). */
val OBLIGATORY_PRAYERS = listOf(Prayer.FAJR, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA)

/**
 * Twilight conventions. [ishaMinutes] replaces the Isha angle when set
 * (Umm al-Qura counts 90 minutes after Maghrib).
 */
enum class CalcMethod(val fajrAngle: Double, val ishaAngle: Double, val ishaMinutes: Int? = null) {
    ALGERIA(18.0, 17.0),
    MWL(18.0, 17.0),
    EGYPT(19.5, 17.5),
    UMM_AL_QURA(18.5, 0.0, 90),
    ISNA(15.0, 15.0),
    MOROCCO(19.0, 17.0),
    TUNISIA(18.0, 18.0),
    FRANCE_UOIF(12.0, 12.0),
    KARACHI(18.0, 18.0),
}

/** Shadow factor for Asr: 1 for the majority (Maliki, Shafi'i, Hanbali), 2 for Hanafi. */
enum class AsrMethod(val factor: Int) { STANDARD(1), HANAFI(2) }

/**
 * Astronomical prayer-time calculation (the widely used PrayTimes algorithm),
 * fully offline. Results are local clock times for [zone].
 */
object PrayerTimes {

    fun compute(
        date: LocalDate,
        latitude: Double,
        longitude: Double,
        zone: ZoneId,
        method: CalcMethod = CalcMethod.ALGERIA,
        asr: AsrMethod = AsrMethod.STANDARD,
        offsetsMinutes: Map<Prayer, Int> = emptyMap(),
    ): Map<Prayer, LocalTime?> {
        val tz = zone.rules.getOffset(date.atTime(12, 0)).totalSeconds / 3600.0
        val jDate = julian(date.year, date.monthValue, date.dayOfMonth) - longitude / (15.0 * 24.0)

        fun sunPosition(jd: Double): Pair<Double, Double> {
            val d = jd - 2451545.0
            val g = fixAngle(357.529 + 0.98560028 * d)
            val q = fixAngle(280.459 + 0.98564736 * d)
            val l = fixAngle(q + 1.915 * dSin(g) + 0.020 * dSin(2 * g))
            val e = 23.439 - 0.00000036 * d
            val ra = dAtan2(dCos(e) * dSin(l), dCos(l)) / 15.0
            val eqt = q / 15.0 - fixHour(ra)
            val decl = dAsin(dSin(e) * dSin(l))
            return decl to eqt
        }

        fun midDay(time: Double): Double {
            val eqt = sunPosition(jDate + time).second
            return fixHour(12 - eqt)
        }

        fun sunAngleTime(angle: Double, time: Double, ccw: Boolean): Double {
            val decl = sunPosition(jDate + time).first
            val noon = midDay(time)
            val cosT = (-dSin(angle) - dSin(decl) * dSin(latitude)) / (dCos(decl) * dCos(latitude))
            if (cosT < -1.0 || cosT > 1.0) return Double.NaN
            val t = dAcos(cosT) / 15.0
            return noon + if (ccw) -t else t
        }

        fun asrTime(factor: Int, time: Double): Double {
            val decl = sunPosition(jDate + time).first
            val angle = -dAcot(factor + dTan(abs(latitude - decl)))
            return sunAngleTime(angle, time, false)
        }

        // Two passes: the first with rough guesses, the second refined.
        var fajr = 5.0; var sunrise = 6.0; var dhuhr = 12.0; var asrT = 13.0
        var sunset = 18.0; var isha = 18.0
        repeat(2) {
            val f = sunAngleTime(method.fajrAngle, fajr / 24, true)
            val sr = sunAngleTime(0.833, sunrise / 24, true)
            val dh = midDay(dhuhr / 24)
            val a = asrTime(asr.factor, asrT / 24)
            val ss = sunAngleTime(0.833, sunset / 24, false)
            val i = if (method.ishaMinutes != null) Double.NaN else sunAngleTime(method.ishaAngle, isha / 24, false)
            fajr = if (f.isNaN()) fajr else f
            sunrise = if (sr.isNaN()) sunrise else sr
            dhuhr = dh
            asrT = if (a.isNaN()) asrT else a
            sunset = if (ss.isNaN()) sunset else ss
            isha = if (i.isNaN()) isha else i
        }
        // Recompute to know which ones are genuinely undefined (high latitudes).
        var fajrFinal = sunAngleTime(method.fajrAngle, fajr / 24, true)
        val sunriseFinal = sunAngleTime(0.833, sunrise / 24, true)
        val sunsetFinal = sunAngleTime(0.833, sunset / 24, false)
        var ishaFinal = if (method.ishaMinutes != null) {
            sunsetFinal + method.ishaMinutes / 60.0
        } else sunAngleTime(method.ishaAngle, isha / 24, false)

        // High-latitude rule (angle-based): cap twilight to a fraction of the night.
        if (!sunriseFinal.isNaN() && !sunsetFinal.isNaN()) {
            val night = timeDiff(sunsetFinal, sunriseFinal)
            val fajrPortion = method.fajrAngle / 60.0 * night
            if (fajrFinal.isNaN() || timeDiff(fajrFinal, sunriseFinal) > fajrPortion) {
                fajrFinal = sunriseFinal - fajrPortion
            }
            if (method.ishaMinutes == null) {
                val ishaPortion = method.ishaAngle / 60.0 * night
                if (ishaFinal.isNaN() || timeDiff(sunsetFinal, ishaFinal) > ishaPortion) {
                    ishaFinal = sunsetFinal + ishaPortion
                }
            }
        }

        val shift = tz - longitude / 15.0
        val raw = mapOf(
            Prayer.FAJR to fajrFinal,
            Prayer.SUNRISE to sunriseFinal,
            Prayer.DHUHR to midDay(dhuhr / 24),
            Prayer.ASR to asrTime(asr.factor, asrT / 24),
            Prayer.MAGHRIB to sunsetFinal,
            Prayer.ISHA to ishaFinal,
        )
        return raw.mapValues { (prayer, hours) ->
            if (hours.isNaN()) null
            else toLocalTime(hours + shift + (offsetsMinutes[prayer] ?: 0) / 60.0)
        }
    }

    private fun toLocalTime(hours: Double): LocalTime {
        val totalMinutes = (fixHour(hours) * 60).roundToInt() % (24 * 60)
        return LocalTime.of(totalMinutes / 60, totalMinutes % 60)
    }

    private fun julian(year: Int, month: Int, day: Int): Double {
        var y = year
        var m = month
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + day + b - 1524.5
    }

    private fun timeDiff(a: Double, b: Double) = fixHour(b - a)
    private fun fixAngle(a: Double) = fix(a, 360.0)
    private fun fixHour(a: Double) = fix(a, 24.0)
    private fun fix(a: Double, b: Double): Double {
        val r = a - b * floor(a / b)
        return if (r < 0) r + b else r
    }

    private fun rad(d: Double) = Math.toRadians(d)
    private fun deg(r: Double) = Math.toDegrees(r)
    private fun dSin(d: Double) = sin(rad(d))
    private fun dCos(d: Double) = cos(rad(d))
    private fun dTan(d: Double) = tan(rad(d))
    private fun dAsin(x: Double) = deg(asin(x))
    private fun dAcos(x: Double) = deg(acos(x))
    private fun dAtan2(y: Double, x: Double) = deg(atan2(y, x))
    private fun dAcot(x: Double) = deg(atan(1 / x))
}
