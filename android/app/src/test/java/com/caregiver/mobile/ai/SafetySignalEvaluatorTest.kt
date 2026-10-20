package com.caregiver.mobile.ai

import com.caregiver.mobile.data.api.SignalDto
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Contract tests for the local safety evaluator, mirroring the backend's:
 * appetite flags on CONSECUTIVE days (streak semantics, not scattered
 * counts), and free text raising the same flags the checkboxes raise.
 */
class SafetySignalEvaluatorTest {

    private fun signal(
        noteId: String,
        date: LocalDate,
        fall: Boolean = false,
        pain: Int? = 1,
        medication: String = "taken",
        medicationUnverified: Boolean = false,
        poorAppetite: Boolean = false,
        text: String = "",
    ) = SignalDto(
        noteId = noteId,
        recipientId = "r1",
        date = date.toString(),
        fallReported = fall,
        pain = pain,
        medication = medication,
        medicationUnverified = medicationUnverified,
        poorAppetite = poorAppetite,
        text = text,
    )

    @Test
    fun scatteredPoorAppetiteDaysDoNotFlag() {
        // The old rule counted distinct days ANYWHERE in the window, so a
        // 30-day summary fired on three loose bad days. Streak semantics
        // require adjacency; gaps reset the run.
        val flags = SafetySignalEvaluator.evaluate(
            listOf(
                signal("n1", LocalDate.of(2026, 10, 1), poorAppetite = true),
                signal("n2", LocalDate.of(2026, 10, 4), poorAppetite = true),
                signal("n3", LocalDate.of(2026, 10, 7), poorAppetite = true),
            ),
        )
        assertFalse("POOR_APPETITE_DAYS" in flags)
    }

    @Test
    fun consecutivePoorAppetiteDaysFlagOnceTheStreakReachesTheThreshold() {
        val flags = SafetySignalEvaluator.evaluate(
            listOf(
                signal("n1", LocalDate.of(2026, 10, 5), poorAppetite = true),
                signal("n2", LocalDate.of(2026, 10, 6), poorAppetite = true),
                signal("n3", LocalDate.of(2026, 10, 7), poorAppetite = true),
                signal("n4", LocalDate.of(2026, 10, 9), poorAppetite = true),
            ),
        )
        assertTrue("POOR_APPETITE_DAYS" in flags)
    }

    @Test
    fun textDescribingAFallRaisesTheFlagEvenWithoutTheCheckbox() {
        val flags = SafetySignalEvaluator.evaluate(
            listOf(signal("n1", LocalDate.of(2026, 10, 2), fall = false, text = "she fell down in the kitchen today")),
        )
        assertTrue("FALL_REPORTED" in flags)
    }

    @Test
    fun textMentioningChestPainRaisesHighPainEvenAtPainNone() {
        val flags = SafetySignalEvaluator.evaluate(
            listOf(signal("n1", LocalDate.of(2026, 10, 2), pain = 1, text = "chest pain started at night")),
        )
        assertTrue("HIGH_PAIN" in flags)
    }

    @Test
    fun arabicTextSurvivesTypesettingVariants() {
        // Zero-width marks, Persian comma, Arabic-Indic digit and tatweel —
        // a model (or the caregiver) re-typesetting must not lose the match.
        val flags = SafetySignalEvaluator.evaluate(
            listOf(
                signal(
                    "n1",
                    LocalDate.of(2026, 10, 2),
                    fall = false,
                    text = "سقطت\u200F بالأمس\u200B، ألم في صدرها ٧ مرة",
                ),
            ),
        )
        assertTrue("FALL_REPORTED" in flags)
        assertTrue("HIGH_PAIN" in flags)
    }

    @Test
    fun fellAsleepIsNotAFall() {
        val flags = SafetySignalEvaluator.evaluate(
            listOf(signal("n1", LocalDate.of(2026, 10, 2), text = "she fell asleep peacefully after dinner")),
        )
        assertFalse("FALL_REPORTED" in flags)
    }

    @Test
    fun cleanFlagsStayClean() {
        val flags = SafetySignalEvaluator.evaluate(
            listOf(signal("n1", LocalDate.of(2026, 10, 2), text = "she ate well, walked in the garden")),
        )
        assertEquals(emptyList<String>(), flags)
    }
}
