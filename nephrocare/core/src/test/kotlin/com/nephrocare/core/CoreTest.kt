package com.nephrocare.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrayerTimesTest {
    @Test
    fun algiersSummerTimesAreInOrderAndPlausible() {
        val t = PrayerTimes.compute(LocalDate.of(2024, 6, 21), 36.7538, 3.0588, ZoneId.of("Africa/Algiers"))
        val order = listOf(Prayer.FAJR, Prayer.SUNRISE, Prayer.DHUHR, Prayer.ASR, Prayer.MAGHRIB, Prayer.ISHA).map { t[it]!! }
        assertEquals(order.sorted(), order)
        // Real values (ministry tables): Fajr ~03:3x, Dhuhr ~12:49, Maghrib ~20:11.
        // Solar noon in Algiers in late June is about 12:49.
        val dhuhr = t[Prayer.DHUHR]!!
        assertTrue(dhuhr.isAfter(LocalTime.of(12, 44)) && dhuhr.isBefore(LocalTime.of(12, 54)), "dhuhr=$dhuhr")
        val maghrib = t[Prayer.MAGHRIB]!!
        assertTrue(maghrib.isAfter(LocalTime.of(20, 2)) && maghrib.isBefore(LocalTime.of(20, 20)), "maghrib=$maghrib")
        val fajr = t[Prayer.FAJR]!!
        assertTrue(fajr.isAfter(LocalTime.of(3, 20)) && fajr.isBefore(LocalTime.of(3, 50)), "fajr=$fajr")
    }

    @Test
    fun highLatitudeStillGivesFajrAndIsha() {
        val t = PrayerTimes.compute(LocalDate.of(2024, 6, 21), 59.33, 18.06, ZoneId.of("Europe/Stockholm"), CalcMethod.MWL)
        assertNotNull(t[Prayer.FAJR])
        assertNotNull(t[Prayer.ISHA])
    }

    @Test
    fun offsetsAreApplied() {
        val d = LocalDate.of(2024, 1, 10)
        val base = PrayerTimes.compute(d, 36.75, 3.06, ZoneId.of("Africa/Algiers"))
        val shifted = PrayerTimes.compute(d, 36.75, 3.06, ZoneId.of("Africa/Algiers"), offsetsMinutes = mapOf(Prayer.MAGHRIB to 3))
        assertEquals(base[Prayer.MAGHRIB]!!.plusMinutes(3), shifted[Prayer.MAGHRIB])
    }
}

class TrackingTest {
    private val day = 24L * 3600 * 1000

    @Test
    fun interdialyticGainPairsPostWithNextPre() {
        val pts = listOf(
            WeightPoint(0, WeightKind.DRY, 70.0),
            WeightPoint(1 * day, WeightKind.PRE, 72.5),
            WeightPoint(1 * day + 4 * 3600_000, WeightKind.POST, 70.2),
            WeightPoint(3 * day, WeightKind.PRE, 72.6),
            WeightPoint(10 * day, WeightKind.PRE, 73.0),
        )
        val gains = WeightCalc.interdialyticGains(pts)
        assertEquals(1, gains.size)
        assertEquals(2.4, gains[0].kg, 1e-9)
        assertEquals(2.4 / 70.0 * 100, gains[0].percentOfDry!!, 1e-9)
        assertEquals(70.0, WeightCalc.dryWeight(pts))
        assertEquals(73.0, WeightCalc.latestWeighing(pts)!!.kg)
    }

    @Test
    fun nextSessionSkipsPastTimeToday() {
        val days = setOf(DayOfWeek.SATURDAY, DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY)
        // 2024-06-17 is a Monday.
        val now = LocalDateTime.of(2024, 6, 17, 9, 0)
        assertEquals(LocalDateTime.of(2024, 6, 19, 8, 0), ScheduleCalc.nextSession(days, LocalTime.of(8, 0), now))
        assertEquals(LocalDateTime.of(2024, 6, 17, 13, 0), ScheduleCalc.nextSession(days, LocalTime.of(13, 0), now))
        assertNull(ScheduleCalc.nextSession(emptySet(), LocalTime.NOON, now))
    }

    @Test
    fun timesRoundTrip() {
        val times = ScheduleCalc.parseTimes("20:30; 08:00,bad,25:00,08:00")
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(20, 30)), times)
        assertEquals("08:00;20:30", ScheduleCalc.formatTimes(times))
    }
}

class LabsTest {
    @Test
    fun allElevenTestsAreDefined() {
        assertEquals(
            listOf("UREA", "CREAT", "K", "PHOS", "CA", "HB", "FERRITIN", "ALB", "PTH", "CRP", "TSAT"),
            Labs.all.map { it.code },
        )
    }

    @Test
    fun unitConversionAndStatus() {
        val phos = Labs.byCode("PHOS")!!
        assertEquals(46.455, phos.toMain(1.5, "mmol/L"), 1e-6)
        assertEquals(LabStatus.IN_RANGE, Labs.status(phos, 46.0))
        val k = Labs.byCode("K")!!
        assertEquals(LabStatus.HIGH, Labs.status(k, 6.1))
        assertEquals(LabStatus.LOW, Labs.status(k, 3.0))
        assertEquals(LabStatus.NOT_FLAGGED, Labs.status(Labs.byCode("UREA")!!, 1.8))
        assertEquals("3.5 – 5.5", k.dialysis!!.label())
        assertEquals("≥ 40", Labs.byCode("ALB")!!.dialysis!!.label())
    }
}

class SearchTest {
    @Test
    fun arabicSearchIgnoresDiacriticsAndLetterVariants() {
        assertEquals(TextNormalizer.normalize("الفِيسْتُولَا"), TextNormalizer.normalize("الفيستولا"))
        assertEquals("ابوتاسيوم", TextNormalizer.normalize("أبوتاسيوم"))
        assertEquals("creatinine", TextNormalizer.normalize("Créatinine"))
        val index = SearchIndex(
            listOf(
                SearchDoc("a", "article", "ارتفاع البوتاسيوم", "خطر على القلب", listOf("potassium")),
                SearchDoc("b", "article", "فقر الدم", "الهيموغلوبين منخفض"),
            ),
        )
        assertEquals("a", index.search("البوتاسيوم").first().doc.key)
        assertEquals("b", index.search("الهيموغلوبين").single().doc.key)
        assertTrue(index.search("x").isEmpty())
    }
}

class BackupCryptoTest {
    @Test
    fun pbkdf2MatchesTheJdk() {
        val salt = "salt-1234".toByteArray()
        val ours = BackupCrypto.pbkdf2("passwd".toByteArray(), salt, 1000, 32)
        val jdk = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec("passwd".toCharArray(), salt, 1000, 256)).encoded
        assertContentEquals(jdk, ours)
    }

    @Test
    fun roundTripAndWrongPassword() {
        val data = "{\"weights\":[]} بيانات".toByteArray()
        val enc = BackupCrypto.encrypt(data, "secret", iterations = 1000)
        assertContentEquals(data, BackupCrypto.decrypt(enc, "secret"))
        assertFailsWith<WrongPasswordException> { BackupCrypto.decrypt(enc, "other") }
        assertFailsWith<WrongPasswordException> { BackupCrypto.decrypt("nope".toByteArray(), "secret") }
    }
}
