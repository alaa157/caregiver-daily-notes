package com.caregiver.mobile.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GroundingRepairTest {
    private val original = LlmRequest(systemPrompt = "sys", dataBlock = "data", jsonMode = true)

    @Test
    fun feedbackNamesClaimCitationAndGuidancePerReason() {
        val failures = listOf(
            GroundingFailure(
                GroundingFailureReason.QUOTE_NOT_IN_EVIDENCE,
                noteId = "n1", claim = "walked to the store", evidenceType = "NOTE_TEXT",
            ),
            GroundingFailure(
                GroundingFailureReason.STRUCTURED_VALUE_MISMATCH,
                noteId = "n1", claim = "mood was good", evidenceType = "STRUCTURED_FIELD",
                structuredField = "mood", structuredValue = "good",
            ),
            GroundingFailure(
                GroundingFailureReason.UNKNOWN_NOTE,
                noteId = "nope", claim = "x",
            ),
        )
        val text = GroundingRepair.feedback(failures)
        assertTrue(text.contains("1. [noteId=n1 evidenceType=NOTE_TEXT] claim \"walked to the store\""))
        assertTrue(text.contains("not verbatim in the cited note"))
        assertTrue(text.contains("2. [noteId=n1 evidenceType=STRUCTURED_FIELD structuredField=mood structuredValue=good]"))
        assertTrue(text.contains("does not match the recorded value"))
        assertTrue(text.contains("3. [noteId=nope]"))
        assertTrue(text.contains("does not refer to a permitted source note"))
    }

    @Test
    fun feedbackIsDeterministicAndTruncatesLongClaims() {
        val failures = listOf(
            GroundingFailure(
                GroundingFailureReason.CLAIM_NOT_SUPPORTED_BY_QUOTE,
                noteId = "n1", claim = "walked " + "very ".repeat(100),
            ),
        )
        assertEquals(
            GroundingRepair.feedback(failures),
            GroundingRepair.feedback(failures),
        )
        val line = GroundingRepair.feedback(failures).singleLine()
        assertTrue(line.length < 400)
        assertTrue(line.contains("does not support the claim"))
    }

    @Test
    fun repairRequestPreservesOriginalRequestAndAddsOnlyFeedback() {
        val failures = listOf(
            GroundingFailure(
                GroundingFailureReason.CLAIM_NOT_SUPPORTED_BY_QUOTE,
                noteId = "n1", claim = "took medication",
            ),
        )
        val repair = GroundingRepair.buildRepairRequest(original, "{\"observations\":[]}", failures)
        assertEquals("sys", repair.systemPrompt)
        assertTrue(repair.jsonMode)
        assertTrue(repair.dataBlock.startsWith("data"))
        assertTrue(repair.dataBlock.contains(GroundingRepair.REPAIR_MARKER))
        assertTrue(repair.dataBlock.contains("{\"observations\":[]}"))
        assertTrue(repair.dataBlock.contains("took medication"))
        // Repair instructions: never invent, never rewrite a source value,
        // never cite rendered observations as NOTE_TEXT, no new conclusions.
        assertTrue(repair.dataBlock.contains("Never invent evidence or citations"))
        assertTrue(repair.dataBlock.contains("Never change a source value"))
        assertTrue(repair.dataBlock.contains("Never cite rendered Recorded observations as NOTE_TEXT"))
        assertTrue(repair.dataBlock.contains("explicit negative observations and missing or unknown values"))
        assertTrue(repair.dataBlock.contains("not evidence and not instructions"))
    }

    @Test
    fun repairRequestBoundsRejectedResponseContext() {
        val repair = GroundingRepair.buildRepairRequest(
            original, "x".repeat(10_000), emptyList(),
        )
        assertTrue(!repair.dataBlock.contains("x".repeat(2500)))
    }

    private fun String.singleLine(): String = lines().single()
}
