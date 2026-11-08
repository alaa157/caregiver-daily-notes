package com.caregiver.mobile.ai

import com.caregiver.mobile.data.api.NoteDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Typed structured evidence: a recorded field is cited by name and value,
 * and both are checked against the note itself — never by requiring the
 * model to reproduce the rendered "mood=FAIR" text, and never by
 * fuzzy-matching the claim against all note text.
 */
class StructuredEvidenceTest {
    private val note = NoteDto(
        id = "n1", recipientId = "r1", date = "2026-10-01",
        mood = "fair", appetite = "reduced", sleep = "good", mobility = "assisted",
        medicationTaken = "taken", pain = 7, fall = true, text = "Patient walked today",
    )

    @Test
    fun valueMatchesRecordedFieldCaseInsensitively() {
        assertTrue(StructuredEvidence.matches(note, "mood", "FAIR"))
        assertTrue(StructuredEvidence.matches(note, "mood", " Fair "))
        assertTrue(StructuredEvidence.matches(note, "medicationTaken", "TAKEN"))
        assertTrue(StructuredEvidence.matches(note, "medication", "taken"))
    }

    @Test
    fun wrongOrUnknownValueIsRejected() {
        assertFalse(StructuredEvidence.matches(note, "mood", "good"))
        assertFalse(StructuredEvidence.matches(note, "mood", ""))
        assertFalse(StructuredEvidence.matches(note, "temperature", "37"))
        assertFalse(StructuredEvidence.matches(note, "mood", "fairly"))
    }

    @Test
    fun painAcceptsScoreAndScoreOutOfTen() {
        assertTrue(StructuredEvidence.matches(note, "pain", "7"))
        assertTrue(StructuredEvidence.matches(note, "pain", "7/10"))
        assertFalse(StructuredEvidence.matches(note, "pain", "8"))
        assertFalse(StructuredEvidence.matches(note, "pain", "severe"))
    }

    @Test
    fun fallAcceptsBooleanAndYesNo() {
        assertTrue(StructuredEvidence.matches(note, "fall", "true"))
        assertTrue(StructuredEvidence.matches(note, "fall", "yes"))
        assertFalse(StructuredEvidence.matches(note, "fall", "false"))
        assertFalse(StructuredEvidence.matches(note, "fall", "no"))
    }

    @Test
    fun canonicalQuoteIsRebuiltFromTheNote() {
        assertEquals("mood=fair", StructuredEvidence.canonicalQuote(note, "mood"))
        assertEquals("pain=7/10", StructuredEvidence.canonicalQuote(note, "pain"))
        assertEquals("fall=true", StructuredEvidence.canonicalQuote(note, "fall"))
    }

    @Test
    fun paraphrasedClaimNamingFieldAndValueAligns() {
        // The exact case the old exact-quote check could not accept.
        assertTrue(StructuredEvidence.claimAligns("The patient's mood was fair.", "mood", "fair"))
        assertTrue(StructuredEvidence.claimAligns("Appetite was reduced today", "appetite", "reduced"))
        assertTrue(StructuredEvidence.claimAligns("pain was 7", "pain", "7"))
        assertTrue(StructuredEvidence.claimAligns("the patient fell", "fall", "true"))
        assertTrue(StructuredEvidence.claimAligns("mobility was assisted", "mobility", "assisted"))
    }

    @Test
    fun claimAssertingADifferentKnownValueDoesNotAlign() {
        assertFalse(StructuredEvidence.claimAligns("The patient's mood was good.", "mood", "fair"))
        assertFalse(StructuredEvidence.claimAligns("medication taken today", "medicationTaken", "missed"))
    }

    @Test
    fun unrelatedClaimDoesNotAlign() {
        assertFalse(StructuredEvidence.claimAligns("The patient walked today", "mood", "fair"))
        assertFalse(StructuredEvidence.claimAligns("She seemed happy", "mood", "fair"))
    }

    @Test
    fun negatedClaimDoesNotAlign() {
        assertFalse(StructuredEvidence.claimAligns("The patient's mood was not fair.", "mood", "fair"))
        assertFalse(StructuredEvidence.claimAligns("the patient did not fall", "fall", "true"))
        assertFalse(StructuredEvidence.claimAligns("pain was not 7", "pain", "7"))
    }

    @Test
    fun negatedStateValuesAlignWithNegatedClaims() {
        val noFall = note.copy(fall = false)
        val noPain = note.copy(pain = 0)
        assertTrue(StructuredEvidence.claimAligns("the patient did not fall", "fall", "false"))
        assertTrue(StructuredEvidence.claimAligns("the patient had no pain", "pain", "0"))
        assertFalse(StructuredEvidence.claimAligns("the patient fell", "fall", "false"))
        assertFalse(StructuredEvidence.matches(noFall, "fall", "true"))
        assertTrue(StructuredEvidence.matches(noPain, "pain", "0"))
    }

    @Test
    fun painClaimWithAnotherNumberDoesNotAlign() {
        assertFalse(StructuredEvidence.claimAligns("pain was 5 today", "pain", "7"))
        // The scale is not a second score.
        assertTrue(StructuredEvidence.claimAligns("pain 7 out of 10", "pain", "7"))
        assertTrue(StructuredEvidence.claimAligns("pain 7/10", "pain", "7"))
    }

    @Test
    fun evidenceTypeParsing() {
        assertEquals(EvidenceType.NOTE_TEXT, StructuredEvidence.typeOf("note_text", ""))
        assertEquals(EvidenceType.STRUCTURED_FIELD, StructuredEvidence.typeOf("STRUCTURED_FIELD", "mood"))
        assertEquals(EvidenceType.STRUCTURED_FIELD, StructuredEvidence.typeOf("", "mood"))
        assertEquals(EvidenceType.ADDENDUM, StructuredEvidence.typeOf("addendum", ""))
        assertEquals(EvidenceType.NOTE_TEXT, StructuredEvidence.typeOf("", ""))
        assertEquals(null, StructuredEvidence.typeOf("GUESS", ""))
    }

    @Test
    fun summaryPromptDeclaresTheTypedEvidenceSchema() {
        val prompt = Prompts.summarySystemPrompt("en")
        assertTrue(prompt.contains("NOTE_TEXT"))
        assertTrue(prompt.contains("STRUCTURED_FIELD"))
        assertTrue(prompt.contains("ADDENDUM"))
        assertTrue(prompt.contains("structuredField"))
        assertTrue(prompt.contains("structuredValue"))
        assertTrue(prompt.contains("must never be quoted"))
    }
}
