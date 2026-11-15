package com.caregiver.mobile.presentation.summary

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class SummaryEndDateTest {
    private val today = LocalDate.of(2026, 11, 15)

    @Test
    fun missingDateFallsBackToToday() {
        assertEquals(today, parseEndArg(null, today))
    }

    @Test
    fun malformedDateFallsBackToToday() {
        assertEquals(today, parseEndArg("not-a-date", today))
    }

    @Test
    fun futureDateFallsBackToToday() {
        assertEquals(today, parseEndArg("2026-11-16", today))
    }

    @Test
    fun validPastDateIsReturned() {
        assertEquals(LocalDate.of(2026, 11, 14), parseEndArg("2026-11-14", today))
    }
}
