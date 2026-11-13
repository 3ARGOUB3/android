package com.nephrocare.core

import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

enum class WeightKind { DRY, PRE, POST, HOME }

data class WeightPoint(val time: Long, val kind: WeightKind, val kg: Double)

/** Weight gained between the end of one session and the start of the next. */
data class InterdialyticGain(val postTime: Long, val preTime: Long, val kg: Double, val percentOfDry: Double?)

object WeightCalc {
    private const val MAX_GAP_MS = 4L * 24 * 60 * 60 * 1000

    /** The latest dry weight recorded (dry weight is always prescribed by the medical team). */
    fun dryWeight(points: List<WeightPoint>): Double? =
        points.filter { it.kind == WeightKind.DRY }.maxByOrNull { it.time }?.kg

    /** The latest real weighing (pre, post or home). */
    fun latestWeighing(points: List<WeightPoint>): WeightPoint? =
        points.filter { it.kind != WeightKind.DRY }.maxByOrNull { it.time }

    /** Difference between a weight and the dry weight (positive = above dry weight). */
    fun aboveDry(weight: Double, dry: Double): Double = weight - dry

    /**
     * Pairs each pre-dialysis weight with the last post-dialysis weight before it
     * (no more than 4 days earlier) and returns the gains, oldest first.
     */
    fun interdialyticGains(points: List<WeightPoint>): List<InterdialyticGain> {
        val sorted = points.sortedBy { it.time }
        val result = mutableListOf<InterdialyticGain>()
        for (pre in sorted.filter { it.kind == WeightKind.PRE }) {
            val post = sorted.lastOrNull { it.kind == WeightKind.POST && it.time < pre.time } ?: continue
            if (pre.time - post.time > MAX_GAP_MS) continue
            val dry = sorted.lastOrNull { it.kind == WeightKind.DRY && it.time <= pre.time }?.kg
                ?: dryWeight(sorted)
            val gain = pre.kg - post.kg
            result += InterdialyticGain(post.time, pre.time, gain, dry?.let { gain / it * 100 })
        }
        return result
    }
}

object ScheduleCalc {
    /** The next session strictly after [now], or null when no day is selected. */
    fun nextSession(days: Set<DayOfWeek>, time: LocalTime, now: LocalDateTime): LocalDateTime? {
        if (days.isEmpty()) return null
        for (i in 0..7) {
            val candidate = now.toLocalDate().plusDays(i.toLong()).atTime(time)
            if (candidate.dayOfWeek in days && candidate.isAfter(now)) return candidate
        }
        return null
    }

    /** "08:00;20:30" -> times, ignoring malformed parts. */
    fun parseTimes(raw: String): List<LocalTime> = raw.split(';', ',')
        .mapNotNull { part ->
            val bits = part.trim().split(':')
            val h = bits.getOrNull(0)?.toIntOrNull()
            val m = bits.getOrNull(1)?.toIntOrNull()
            if (h != null && m != null && h in 0..23 && m in 0..59) LocalTime.of(h, m) else null
        }
        .distinct()
        .sorted()

    fun formatTimes(times: List<LocalTime>): String =
        times.distinct().sorted().joinToString(";") { "%02d:%02d".format(it.hour, it.minute) }
}

enum class Level { LOW, MEDIUM, HIGH }

/** Rough per-100 g bands used to colour the foods table (educational, not a prescription). */
object NutrientLevels {
    fun potassium(mg: Int) = when { mg < 150 -> Level.LOW; mg <= 250 -> Level.MEDIUM; else -> Level.HIGH }
    fun phosphorus(mg: Int) = when { mg < 100 -> Level.LOW; mg <= 200 -> Level.MEDIUM; else -> Level.HIGH }
    fun sodium(mg: Int) = when { mg < 140 -> Level.LOW; mg <= 400 -> Level.MEDIUM; else -> Level.HIGH }
    fun protein(g: Double) = when { g < 3 -> Level.LOW; g <= 10 -> Level.MEDIUM; else -> Level.HIGH }

    fun parse(code: String): Level = when (code.uppercase()) {
        "L" -> Level.LOW
        "M" -> Level.MEDIUM
        else -> Level.HIGH
    }
}

/** Blood-pressure readings that deserve attention whatever the personal target (shown with advice to call the team). */
object BpCalc {
    fun isVeryHigh(systolic: Int, diastolic: Int) = systolic >= 180 || diastolic >= 110
    fun isLow(systolic: Int) = systolic < 90
}

/**
 * Medicine stock counting. Units are whatever the patient counts (tablets, sachets, ampoules);
 * [perDose] is how many units one intake uses, as written on the prescription. Nothing here
 * suggests or changes a dose: it only counts what is left.
 */
object StockCalc {
    fun dailyUse(perDose: Double, timesPerDay: Int): Double = perDose * timesPerDay

    /** Days of treatment left, or null when the stock is not tracked or the medicine has no fixed times. */
    fun daysLeft(stock: Double?, perDose: Double, timesPerDay: Int): Double? {
        val daily = dailyUse(perDose, timesPerDay)
        if (stock == null || daily <= 0.0) return null
        return (stock / daily).coerceAtLeast(0.0)
    }

    /** Low when the stock covers [refillDays] days or less (or, without fixed times, one intake or less). */
    fun isLow(stock: Double?, perDose: Double, timesPerDay: Int, refillDays: Int): Boolean {
        if (stock == null) return false
        if (stock <= 0.0) return true
        val days = daysLeft(stock, perDose, timesPerDay) ?: return stock <= perDose
        return days <= refillDays
    }
}

object FluidReminderCalc {
    /** The reminder times of a day: start, start + interval, … up to end (inclusive). */
    fun times(startHour: Int, endHour: Int, intervalHours: Int): List<LocalTime> {
        if (intervalHours <= 0 || endHour < startHour) return emptyList()
        return (startHour..endHour step intervalHours).map { LocalTime.of(it.coerceIn(0, 23), 0) }
    }

    fun next(startHour: Int, endHour: Int, intervalHours: Int, now: LocalDateTime): LocalDateTime? {
        val times = times(startHour, endHour, intervalHours)
        if (times.isEmpty()) return null
        times.firstOrNull { now.toLocalDate().atTime(it).isAfter(now) }?.let { return now.toLocalDate().atTime(it) }
        return now.toLocalDate().plusDays(1).atTime(times.first())
    }
}
