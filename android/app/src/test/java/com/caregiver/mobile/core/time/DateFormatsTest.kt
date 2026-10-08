package com.caregiver.mobile.core.time

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
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
}
