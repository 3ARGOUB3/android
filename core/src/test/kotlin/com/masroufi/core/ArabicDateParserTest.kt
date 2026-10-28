package com.masroufi.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import java.time.LocalDateTime

class ArabicDateParserTest {

    @Test
    fun parsesExportFormat() {
        assertEquals(LocalDateTime.of(2026, 10, 29, 10, 53), ArabicDateParser.parse("29 أكتوبر، 2026 10:53 AM"))
        assertEquals(LocalDateTime.of(2026, 10, 28, 16, 3), ArabicDateParser.parse("28 أكتوبر، 2026 04:03 PM"))
        assertEquals(LocalDateTime.of(2022, 12, 28, 19, 16), ArabicDateParser.parse("28 ديسمبر، 2022 07:16 PM"))
    }

    @Test
    fun handlesMidnightNoonAndArabicMarkers() {
        assertEquals(LocalDateTime.of(2025, 4, 1, 0, 5), ArabicDateParser.parse("1 ابريل، 2025 12:05 AM"))
        assertEquals(LocalDateTime.of(2025, 4, 1, 12, 5), ArabicDateParser.parse("1 أبريل، 2025 12:05 PM"))
        assertEquals(LocalDateTime.of(2025, 4, 1, 21, 0), ArabicDateParser.parse("١ أفريل، ٢٠٢٥ ٠٩:٠٠ م"))
    }

    @Test
    fun acceptsMaghrebAndEnglishMonths() {
        assertEquals(LocalDateTime.of(2024, 7, 5, 0, 0), ArabicDateParser.parse("5 جويلية 2024"))
        assertEquals(LocalDateTime.of(2024, 8, 5, 8, 30), ArabicDateParser.parse("5 August, 2024 08:30 AM"))
    }

    @Test
    fun rejectsGarbage() {
        assertNull(ArabicDateParser.parse(""))
        assertNull(ArabicDateParser.parse("31 فبراير، 2024 10:00 AM"))
        assertNull(ArabicDateParser.parse("hello"))
    }
}
