package com.masroufi.core

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Parses dates as written by the expense-tracker export, e.g. "29 أكتوبر، 2026 10:53 AM".
 * Accepts Middle-East and Maghreb month names, English month names,
 * Arabic-Indic digits and AM/PM written as ص/م.
 */
object ArabicDateParser {

    private val months: Map<String, Int> = buildMap {
        fun add(month: Int, vararg names: String) = names.forEach { put(normalize(it), month) }
        add(1, "يناير", "جانفي", "كانون الثاني", "january", "jan")
        add(2, "فبراير", "فيفري", "شباط", "february", "feb")
        add(3, "مارس", "آذار", "march", "mar")
        add(4, "أبريل", "ابريل", "إبريل", "أفريل", "نيسان", "april", "apr")
        add(5, "مايو", "ماي", "أيار", "may")
        add(6, "يونيو", "يونيه", "جوان", "حزيران", "june", "jun")
        add(7, "يوليو", "يوليه", "جويلية", "تموز", "july", "jul")
        add(8, "أغسطس", "اغسطس", "أوت", "آب", "august", "aug")
        add(9, "سبتمبر", "أيلول", "september", "sep", "sept")
        add(10, "أكتوبر", "اكتوبر", "تشرين الأول", "october", "oct")
        add(11, "نوفمبر", "تشرين الثاني", "november", "nov")
        add(12, "ديسمبر", "كانون الأول", "december", "dec")
    }

    private val pattern = Regex(
        """^(\d{1,2})\s+(.+?)\s+(\d{4})(?:\s+(\d{1,2}):(\d{2})(?::(\d{2}))?\s*(am|pm|ص|م)?)?$""",
        RegexOption.IGNORE_CASE,
    )

    fun parse(raw: String): LocalDateTime? {
        val text = normalizeDigits(raw)
            .replace('،', ' ')
            .replace(',', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
        val match = pattern.matchEntire(text) ?: return null
        val (day, monthName, year, hour, minute, second, ampm) = match.destructured
        val month = months[normalize(monthName)] ?: return null
        val date = runCatching { LocalDate.of(year.toInt(), month, day.toInt()) }.getOrNull() ?: return null
        if (hour.isEmpty()) return date.atStartOfDay()

        var h = hour.toInt()
        when (ampm.lowercase()) {
            "pm", "م" -> if (h < 12) h += 12
            "am", "ص" -> if (h == 12) h = 0
        }
        val time = runCatching {
            LocalTime.of(h, minute.toInt(), second.ifEmpty { "0" }.toInt())
        }.getOrNull() ?: return null
        return date.atTime(time)
    }

    /** Lowercases and folds the alef/hamza and taa marbuta spellings so name variants match. */
    internal fun normalize(s: String): String = s.trim().lowercase()
        .replace('أ', 'ا').replace('إ', 'ا').replace('آ', 'ا')
        .replace('ة', 'ه').replace('ى', 'ي')
        .replace(Regex("\\s+"), " ")

    internal fun normalizeDigits(s: String): String = buildString(s.length) {
        for (c in s) {
            append(
                when (c) {
                    in '٠'..'٩' -> '0' + (c - '٠')
                    in '۰'..'۹' -> '0' + (c - '۰')
                    else -> c
                }
            )
        }
    }
}
