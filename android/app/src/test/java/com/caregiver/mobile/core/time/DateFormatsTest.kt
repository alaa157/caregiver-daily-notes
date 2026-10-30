package com.caregiver.mobile.core.time

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Day-month rendering must use Latin digits in both languages ("7 أكتوبر",
 * not "٧ أكتوبر") so dates match the design boards on every API level.
 * This exercises the java.time path that core-library desugaring backports
 * to API 21 devices.
 */
class DateFormatsTest {

    @Test
    fun dayMonthArabicUsesLatinDigits() {
        assertEquals(
            "7 أكتوبر",
            DateFormats.dayMonth(LocalDate.of(2026, 10, 7), Locale.forLanguageTag("ar")),
        )
    }

    @Test
    fun dayMonthEnglish() {
        assertEquals(
            "Oct 7",
            DateFormats.dayMonth(LocalDate.of(2026, 10, 7), Locale.ENGLISH),
        )
    }

    @Test
    fun historyDayArabicUsesLatinDigits() {
        assertEquals("7 أكتوبر 2026", DateFormats.historyDay("2026-10-07", true))
    }

    @Test
    fun historyDayEnglish() {
        assertEquals("Oct 7, 2026", DateFormats.historyDay("2026-10-07", false))
    }

    @Test
    fun historyDayFallsBackToRawOnGarbage() {
        assertEquals("not-a-date", DateFormats.historyDay("not-a-date", true))
        assertEquals("not-a-date", DateFormats.historyDay("not-a-date", false))
    }

    @Test
    fun parseIsoDateAcceptsDatesAndDateTimes() {
        val expected = LocalDate.of(2026, 10, 7)
        assertEquals(expected, DateFormats.parseIsoDate("2026-10-07"))
        assertEquals(expected, DateFormats.parseIsoDate("2026-10-07T18:30:00"))
        assertEquals(expected, DateFormats.parseIsoDate("2026-10-07T10:00:00.123456Z"))
        assertEquals(expected, DateFormats.parseIsoDate("2026-10-07T10:00:00+02:00"))
        assertEquals(expected, DateFormats.parseIsoDate(" 2026-10-07 18:30:00 "))
    }

    @Test
    fun parseIsoDateRejectsBlankAndGarbage() {
        assertNull(DateFormats.parseIsoDate(null))
        assertNull(DateFormats.parseIsoDate(""))
        assertNull(DateFormats.parseIsoDate("   "))
        assertNull(DateFormats.parseIsoDate("not-a-date"))
        assertNull(DateFormats.parseIsoDate("2026-13-40"))
        assertNull(DateFormats.parseIsoDate("2026-13-40T10:00:00"))
    }

    @Test
    fun addendumDateRendersDateTimeAsHistoryDay() {
        assertEquals("Oct 7, 2026", DateFormats.addendumDate("2026-10-07T18:30:00", false))
        assertEquals("7 أكتوبر 2026", DateFormats.addendumDate("2026-10-07T18:30:00", true))
    }

    @Test
    fun addendumDateAcceptsDateOnlyAndFractionalSeconds() {
        assertEquals("Oct 7, 2026", DateFormats.addendumDate("2026-10-07", false))
        assertEquals("Oct 7, 2026", DateFormats.addendumDate("2026-10-07T10:00:00.123", false))
        assertEquals("7 أكتوبر 2026", DateFormats.addendumDate("2026-10-07T10:00:00.123456", true))
    }

    @Test
    fun addendumDateReturnsNullForBlankOrUnparsable() {
        assertNull(DateFormats.addendumDate("", false))
        assertNull(DateFormats.addendumDate("   ", true))
        assertNull(DateFormats.addendumDate("not-a-date", false))
        assertNull(DateFormats.addendumDate("not-a-date", true))
        assertNull(DateFormats.addendumDate("2026-13-40", false))
        assertNull(DateFormats.addendumDate("2026-13-40T10:00:00", true))
    }
}
