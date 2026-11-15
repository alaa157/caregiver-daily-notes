package com.caregiver.mobile.ai

import com.caregiver.mobile.data.api.SignalDto
import org.junit.Assert.assertEquals
import org.junit.Test

class TrendCalculatorTest {

    private fun signal(
        date: String = "2026-10-24",
        fallReported: Boolean = false,
        fallRecorded: Boolean = true,
        pain: Int? = null,
        painRecorded: Boolean = true,
        medication: String = "",
        medicationUnverified: Boolean = false,
        poorAppetite: Boolean = false,
        appetiteRecorded: Boolean = true,
    ) = SignalDto(
        noteId = "n-$date-${System.nanoTime()}",
        recipientId = "r1",
        date = date,
        fallReported = fallReported,
        pain = pain,
        medication = medication,
        medicationUnverified = medicationUnverified,
        poorAppetite = poorAppetite,
        text = "",
        fallRecorded = fallRecorded,
        painRecorded = painRecorded,
        appetiteRecorded = appetiteRecorded,
    )

    private fun trend(trends: List<TrendDto>, metric: String) =
        trends.first { it.metric == metric }

    @Test
    fun fallsCountEventsOverRecordedSample() {
        val current = listOf(signal(fallReported = true), signal(fallReported = false))

        val fall = trend(TrendCalculator.compute(current, emptyList()), "FALL_COUNT")

        assertEquals(1, fall.currentCount)
        assertEquals(2, fall.currentSampleSize)
        assertEquals(1, fall.delta)
    }

    @Test
    fun unrecordedFallIsNotInTheSample() {
        val current = listOf(
            signal(fallReported = true, fallRecorded = false),
            signal(fallRecorded = false),
        )

        val fall = trend(TrendCalculator.compute(current, emptyList()), "FALL_COUNT")

        assertEquals(0, fall.currentCount)
        assertEquals(0, fall.currentSampleSize)
    }

    @Test
    fun painSevenIsAnEventAndSixIsNot() {
        val current = listOf(signal(pain = 7), signal(pain = 6), signal(pain = null))

        val pain = trend(TrendCalculator.compute(current, emptyList()), "HIGH_PAIN_COUNT")

        assertEquals(1, pain.currentCount)
        assertEquals(2, pain.currentSampleSize)
    }

    @Test
    fun missedMedicationCountsDistinctDates() {
        val current = listOf(
            signal(date = "2026-10-20", medication = "missed"),
            signal(date = "2026-10-20", medication = "missed"),
            signal(date = "2026-10-21", medication = "missed"),
            signal(date = "2026-10-22", medication = "taken"),
        )

        val med = trend(TrendCalculator.compute(current, emptyList()), "MISSED_MED_COUNT")

        assertEquals(2, med.currentCount)
        assertEquals(3, med.currentSampleSize)
    }

    @Test
    fun unverifiedMedicationIsNotRecorded() {
        val current = listOf(
            signal(medication = "missed", medicationUnverified = true),
            signal(medication = ""),
        )

        val med = trend(TrendCalculator.compute(current, emptyList()), "MISSED_MED_COUNT")

        assertEquals(0, med.currentCount)
        assertEquals(0, med.currentSampleSize)
    }

    @Test
    fun emptyListsGiveFourZeroTrends() {
        val trends = TrendCalculator.compute(emptyList(), emptyList())

        assertEquals(4, trends.size)
        trends.forEach {
            assertEquals(0, it.previousCount)
            assertEquals(0, it.currentCount)
            assertEquals(0, it.delta)
            assertEquals(0, it.previousSampleSize)
            assertEquals(0, it.currentSampleSize)
        }
    }

    @Test
    fun metricOrderIsFixed() {
        val trends = TrendCalculator.compute(emptyList(), emptyList())

        assertEquals(
            listOf("FALL_COUNT", "HIGH_PAIN_COUNT", "MISSED_MED_COUNT", "POOR_APPETITE_COUNT"),
            trends.map { it.metric },
        )
    }

    @Test
    fun poorAppetiteCountsDistinctRecordedDates() {
        val current = listOf(
            signal(date = "2026-10-20", poorAppetite = true),
            signal(date = "2026-10-20", poorAppetite = false),
            signal(date = "2026-10-21", poorAppetite = true, appetiteRecorded = false),
        )

        val appetite = trend(TrendCalculator.compute(current, emptyList()), "POOR_APPETITE_COUNT")

        assertEquals(1, appetite.currentCount)
        assertEquals(1, appetite.currentSampleSize)
    }
}
