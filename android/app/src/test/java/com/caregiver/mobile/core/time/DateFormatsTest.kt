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
}
