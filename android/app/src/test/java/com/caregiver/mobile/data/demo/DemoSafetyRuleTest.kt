package com.caregiver.mobile.data.demo

import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The single offline safety rule: a recorded fall raises the flag, and the
 * editor preview shows for falls or high pain (>= 7, mirroring the backend
 * HIGH_PAIN_THRESHOLD number only). The stub decides nothing else.
 */
class DemoSafetyRuleTest {

    private fun note(id: String, fall: Boolean, pain: Int = 0) = NoteEntity(
        id = id, recipientId = "r", date = LocalDate.now().toString(),
        mood = "good", appetite = "good", sleep = "good", mobility = "walks",
        medicationTaken = "taken", pain = pain, fall = fall, text = "t",
        createdAt = 0,
    )

    @Test
    fun noNotesMeansNoFlag() {
        assertFalse(DemoSafetyRule.hasFallFlag(emptyList()))
    }

    @Test
    fun recordedFallRaisesFlag() {
        assertTrue(DemoSafetyRule.hasFallFlag(listOf(note("a", false), note("b", true))))
    }

    @Test
    fun previewShowsForFallEvenWithoutPain() {
        assertTrue(DemoSafetyRule.showSafetyPreview(fall = true, pain = 0))
    }

    @Test
    fun previewShowsForHighPainAtThreshold() {
        assertTrue(DemoSafetyRule.showSafetyPreview(fall = false, pain = 7))
    }

    @Test
    fun previewHiddenBelowThresholdWithoutFall() {
        assertFalse(DemoSafetyRule.showSafetyPreview(fall = false, pain = 6))
    }
}
