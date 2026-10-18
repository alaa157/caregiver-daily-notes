package com.caregiver.mobile.ai

import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.data.api.SignalDto
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Finding 3: the selected 7/14/30-day period must bound the data the model
 * sees. Notes and safety signals immediately inside the inclusive window are
 * used; those one day outside are not, for every supported period. An
 * observation citing an out-of-window note can never ground a summary.
 */
class SummaryWindowBoundaryTest {

    private class RecordingLlmClient(private val response: String) : LlmClient {
        var lastRequest: LlmRequest? = null

        override suspend fun complete(request: LlmRequest): LlmResult {
            lastRequest = request
            return LlmResult(LlmStatus.OK, response)
        }
    }

    /** Fixed "today" so boundary days are exact. */
    private val end = LocalDate.of(2026, 10, 18)
    private val emptyJson = """{"observations":[],"uncertainties":[]}"""

    private fun note(id: String, date: LocalDate, text: String = "note $id") = NoteDto(
        id = id,
        recipientId = "r1",
        date = date.toString(),
        mood = "ok",
        appetite = "good",
        sleep = "ok",
        mobility = "ok",
        medicationTaken = "taken",
        pain = 0,
        fall = false,
        text = text,
    )

    private fun signal(id: String, date: LocalDate, fall: Boolean = false) = SignalDto(
        noteId = id,
        recipientId = "r1",
        date = date.toString(),
        fallReported = fall,
        pain = 0,
        medication = "taken",
        medicationUnverified = false,
        poorAppetite = false,
        text = "signal $id",
    )

    @Test
    fun windowStartFollowsTheInclusiveBackendRule() {
        for (days in listOf(7, 14, 30)) {
            assertEquals("${days}d start", end.minusDays((days - 1).toLong()), SummaryWindow.start(end, days))
        }
        assertTrue(SummaryWindow.contains(end.toString(), end, 7))
        assertTrue(SummaryWindow.contains(end.minusDays(6).toString(), end, 7))
        assertFalse(SummaryWindow.contains(end.minusDays(7).toString(), end, 7))
        assertFalse(SummaryWindow.contains("not-a-date", end, 7))
        assertFalse(SummaryWindow.contains(null, end, 7))
    }

    @Test
    fun notesOutsideTheSelectedWindowNeverReachTheModel() = runBlocking {
        for (periodDays in listOf(7, 14, 30)) {
            val start = end.minusDays((periodDays - 1).toLong())
            val client = RecordingLlmClient(emptyJson)

            (SummaryAiService(client).summarize(
                recipientId = "r1",
                periodDays = periodDays,
                notes = listOf(
                    note("out-before", start.minusDays(1)),
                    note("in-start", start),
                    note("in-end", end),
                    note("out-after", end.plusDays(1)),
                ),
                signals = emptyList(),
                endInclusive = end,
            ) as SummaryAiResult.Success).summary

            val dataBlock = client.lastRequest!!.dataBlock
            assertTrue("$periodDays-day window must include its start boundary", dataBlock.contains("in-start"))
            assertTrue("$periodDays-day window must include its end boundary", dataBlock.contains("in-end"))
            assertFalse("$periodDays-day window must exclude the day before it", dataBlock.contains("out-before"))
            assertFalse("$periodDays-day window must exclude the day after it", dataBlock.contains("out-after"))
        }
    }

    @Test
    fun flagsOnlyReflectSignalsInsideTheSelectedWindow() = runBlocking {
        for (periodDays in listOf(7, 14, 30)) {
            val start = end.minusDays((periodDays - 1).toLong())

            val outsideResult = SummaryAiService(RecordingLlmClient(emptyJson)).summarize(
                recipientId = "r1",
                periodDays = periodDays,
                notes = emptyList(),
                signals = listOf(
                    signal("s-out-before", start.minusDays(1), fall = true),
                    signal("s-out-after", end.plusDays(1), fall = true),
                ),
                endInclusive = end,
            )
            val outsideRedFlags = when (outsideResult) {
                is SummaryAiResult.Success -> outsideResult.summary.redFlags
                is SummaryAiResult.Failure -> outsideResult.redFlags
            }
            assertFalse(
                "$periodDays-day window must not raise FALL_REPORTED from signals just outside it",
                outsideRedFlags.contains("FALL_REPORTED"),
            )

            val insideResult = SummaryAiService(RecordingLlmClient(emptyJson)).summarize(
                recipientId = "r1",
                periodDays = periodDays,
                notes = emptyList(),
                signals = listOf(
                    signal("s-in-start", start, fall = true),
                    signal("s-in-end", end, fall = true),
                ),
                endInclusive = end,
            )
            val insideRedFlags = when (insideResult) {
                is SummaryAiResult.Success -> insideResult.summary.redFlags
                is SummaryAiResult.Failure -> insideResult.redFlags
            }
            assertTrue(
                "$periodDays-day window must raise FALL_REPORTED from signals on its boundaries",
                insideRedFlags.contains("FALL_REPORTED"),
            )
        }
    }

    @Test
    fun observationsCitingOutOfWindowNotesAreRejected() = runBlocking {
        val json = """{"observations":[{"text":"older fall","noteId":"out-before","quote":"older fall note"}],"uncertainties":[]}"""
        val client = RecordingLlmClient(json)

        val summaryResult = SummaryAiService(client).summarize(
            recipientId = "r1",
            periodDays = 7,
            notes = listOf(
                note("in-window", end, text = "walked today"),
                note("out-before", end.minusDays(7), text = "older fall note"),
            ),
            signals = emptyList(),
            endInclusive = end,
        )

        assertFalse(client.lastRequest!!.dataBlock.contains("out-before"))
        assertTrue(summaryResult is SummaryAiResult.Failure)
        assertEquals("invalid_evidence", (summaryResult as SummaryAiResult.Failure).reasonCode)
    }
}
