package com.nephrocare.core

import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StockAndFluidTest {
    @Test
    fun daysLeftAndLowStock() {
        // 30 tablets, 1 tablet three times a day = 10 days.
        assertEquals(10.0, StockCalc.daysLeft(30.0, 1.0, 3))
        assertFalse(StockCalc.isLow(30.0, 1.0, 3, 5))
        assertTrue(StockCalc.isLow(15.0, 1.0, 3, 5))
        // Half a tablet twice a day.
        assertEquals(7.0, StockCalc.daysLeft(7.0, 0.5, 2))
        // Not tracked or no fixed times.
        assertNull(StockCalc.daysLeft(null, 1.0, 3))
        assertFalse(StockCalc.isLow(null, 1.0, 3, 5))
        assertNull(StockCalc.daysLeft(10.0, 1.0, 0))
        assertTrue(StockCalc.isLow(1.0, 1.0, 0, 5))
        assertFalse(StockCalc.isLow(4.0, 1.0, 0, 5))
        assertTrue(StockCalc.isLow(0.0, 1.0, 3, 5))
    }

    @Test
    fun fluidReminderTimes() {
        assertEquals(listOf(9, 12, 15, 18, 21).map { LocalTime.of(it, 0) }, FluidReminderCalc.times(9, 21, 3))
        val now = LocalDateTime.of(2024, 6, 17, 12, 0)
        assertEquals(LocalDateTime.of(2024, 6, 17, 15, 0), FluidReminderCalc.next(9, 21, 3, now))
        assertEquals(LocalDateTime.of(2024, 6, 18, 9, 0), FluidReminderCalc.next(9, 21, 3, now.withHour(22)))
        assertNull(FluidReminderCalc.next(21, 9, 3, now))
    }
}
