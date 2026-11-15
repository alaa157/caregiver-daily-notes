package com.caregiver.mobile.presentation.summary

import org.junit.Assert.assertEquals
import org.junit.Test

class TrendsSectionTest {

    @Test
    fun zeroCountHasNoBar() {
        assertEquals(0f, trendBarFraction(0, 4, 2), 0f)
    }

    @Test
    fun equalPreviousAndCurrentCountsUseTheirSharedMaximum() {
        assertEquals(0.5f, trendBarFraction(2, 4, 4), 0f)
    }

    @Test
    fun largerPeriodCountFillsTheBar() {
        assertEquals(1f, trendBarFraction(7, 2, 7), 0f)
        assertEquals(1f, trendBarFraction(7, 7, 2), 0f)
    }

    @Test
    fun bothZeroCountsProduceZeroFraction() {
        assertEquals(0f, trendBarFraction(0, 0, 0), 0f)
    }
}
