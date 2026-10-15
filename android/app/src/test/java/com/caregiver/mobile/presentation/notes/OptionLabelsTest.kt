package com.caregiver.mobile.presentation.notes

import org.junit.Assert.assertEquals
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
}
