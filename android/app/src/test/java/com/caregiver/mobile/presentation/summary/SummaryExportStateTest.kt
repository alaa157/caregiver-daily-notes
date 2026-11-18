package com.caregiver.mobile.presentation.summary

import com.caregiver.mobile.data.api.SummaryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Failure-state export visibility and DTO construction for
 * [SummaryExportActions]. Pure logic — no Android framework, no launch.
 */
class SummaryExportStateTest {

    private fun dto(
        text: String = "Overview text",
        redFlags: List<String> = listOf("FALL_REPORTED"),
    ) = SummaryDto(
        id = "s1",
        recipientId = "r1",
        periodDays = 7,
        text = text,
        redFlags = redFlags,
        evidence = listOf(SummaryDto.EvidenceDto("n1", "quote")),
        uncertainties = listOf(
            SummaryDto.UncertaintyDto("meds", "unsure dose"),
        ),
    )

    // -- summaryExportVisible ---------------------------------------------

    @Test
    fun flagsMakeExportVisible() {
        assertTrue(
            summaryExportVisible("network", listOf("FALL_REPORTED"), null),
        )
    }

    @Test
    fun previousSummaryMakesExportVisibleEvenWithoutFlags() {
        assertTrue(summaryExportVisible("timeout", emptyList(), dto()))
    }

    @Test
    fun nothingToExportHidesTheActions() {
        assertFalse(summaryExportVisible("network", emptyList(), null))
        assertFalse(summaryExportVisible(null, emptyList(), null))
    }

    @Test
    fun noSourceDataAlwaysHidesTheActions() {
        // The failure itself means "nothing to export", even if the state
        // happened to carry flags or a previous summary.
        assertFalse(summaryExportVisible("no_source_data", listOf("FALL_REPORTED"), dto()))
        assertFalse(summaryExportVisible("no_source_data", emptyList(), dto()))
        assertFalse(summaryExportVisible("no_source_data", emptyList(), null))
    }

    // -- exportDtoFor ------------------------------------------------------

    @Test
    fun lastSummaryIsReusedWithCurrentFlags() {
        val last = dto(redFlags = listOf("HIGH_PAIN"))
        val out = exportDtoFor("r1", 7, listOf("FALL_REPORTED"), last)

        assertEquals("s1", out.id)
        assertEquals("Overview text", out.text)
        assertEquals(listOf("FALL_REPORTED"), out.redFlags)
        assertEquals(1, out.evidence.size)
        assertEquals(1, out.uncertainties.size)
    }

    @Test
    fun bareFlagsOnlyDtoIsSynthesizedWithoutALastSummary() {
        val out = exportDtoFor("r9", 14, listOf("FALL_REPORTED", "HIGH_PAIN"), null)

        assertEquals("r9", out.recipientId)
        assertEquals(14, out.periodDays)
        assertEquals(listOf("FALL_REPORTED", "HIGH_PAIN"), out.redFlags)
        assertEquals("", out.text)
        assertTrue(out.evidence.isEmpty())
        assertTrue(out.uncertainties.isEmpty())
    }
}
