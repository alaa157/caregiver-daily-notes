package com.caregiver.mobile.presentation.notes

import com.caregiver.mobile.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Chip labels in both languages, plus the raw fallback for unknown values. */
class OptionLabelsTest {

    @Test
    fun arabicLabels() {
        assertEquals("جيدة", OptionLabels.label(OptionGroup.Mood, "good", true))
        assertEquals("متقطع", OptionLabels.label(OptionGroup.Sleep, "broken", true))
        assertEquals("غير متأكد", OptionLabels.label(OptionGroup.Medication, "unsure", true))
        assertEquals("طريح الفراش", OptionLabels.label(OptionGroup.Mobility, "bed", true))
    }

    @Test
    fun englishLabels() {
        assertEquals("Good", OptionLabels.label(OptionGroup.Mood, "good", false))
        assertEquals("Unsure", OptionLabels.label(OptionGroup.Medication, "unsure", false))
    }

    @Test
    fun medicationLabelsCoverAllEditorValuesBothLanguages() {
        // The note detail grid renders medicationTaken through these labels,
        // so every wire value the editor records must resolve in en + ar.
        assertEquals("Taken", OptionLabels.label(OptionGroup.Medication, "taken", false))
        assertEquals("Missed", OptionLabels.label(OptionGroup.Medication, "missed", false))
        assertEquals("Unsure", OptionLabels.label(OptionGroup.Medication, "unsure", false))
        assertEquals("أخذ الدواء", OptionLabels.label(OptionGroup.Medication, "taken", true))
        assertEquals("فاته الدواء", OptionLabels.label(OptionGroup.Medication, "missed", true))
        assertEquals("غير متأكد", OptionLabels.label(OptionGroup.Medication, "unsure", true))
    }

    @Test
    fun unknownValuesFallBackToRaw() {
        assertEquals("weird", OptionLabels.label(OptionGroup.Mood, "weird", true))
        assertEquals("weird", OptionLabels.label(OptionGroup.Mood, "weird", false))
    }

    @Test
    fun labelResCoversEveryKnownPair() {
        val pairs = listOf(
            OptionGroup.Mood to listOf(
                "calm" to R.string.note_option_mood_calm,
                "happy" to R.string.note_option_mood_happy,
                "anxious" to R.string.note_option_mood_anxious,
                "agitated" to R.string.note_option_mood_agitated,
                "sad" to R.string.note_option_mood_sad,
                "confused" to R.string.note_option_mood_confused,
                "good" to R.string.note_option_mood_good,
                "fair" to R.string.note_option_mood_fair,
                "bad" to R.string.note_option_mood_bad,
            ),
            OptionGroup.Appetite to listOf(
                "good" to R.string.note_option_appetite_good,
                "fair" to R.string.note_option_appetite_fair,
                "poor" to R.string.note_option_appetite_poor,
                "refused" to R.string.note_option_appetite_refused,
                "reduced" to R.string.note_option_appetite_reduced,
            ),
            OptionGroup.Sleep to listOf(
                "restful" to R.string.note_option_sleep_restful,
                "restless" to R.string.note_option_sleep_restless,
                "poor" to R.string.note_option_sleep_poor,
                "sleepless" to R.string.note_option_sleep_sleepless,
                "good" to R.string.note_option_sleep_good,
                "broken" to R.string.note_option_sleep_broken,
            ),
            OptionGroup.Mobility to listOf(
                "independent" to R.string.note_option_mobility_independent,
                "assisted" to R.string.note_option_mobility_assisted,
                "bedridden" to R.string.note_option_mobility_bedridden,
                "walks" to R.string.note_option_mobility_walks,
                "bed" to R.string.note_option_mobility_bed,
            ),
            OptionGroup.Medication to listOf(
                "taken" to R.string.note_option_medication_taken,
                "missed" to R.string.note_option_medication_missed,
                "unsure" to R.string.note_option_medication_unsure,
            ),
        )
        var count = 0
        pairs.forEach { (group, values) ->
            values.forEach { (value, resId) ->
                assertEquals(resId, OptionLabels.labelRes(group, value))
                assertNotNull(OptionLabels.labelRes(group, value))
                count++
            }
        }
        assertEquals(28, count)
    }

    @Test
    fun labelResReturnsNullForUnknownValues() {
        assertNull(OptionLabels.labelRes(OptionGroup.Mood, "unknown"))
        assertNull(OptionLabels.labelRes(OptionGroup.Medication, "taken "))
    }
}
