package com.caregiver.mobile.presentation.notes

import com.caregiver.mobile.R
import org.junit.Assert.assertEquals
import org.junit.Test

/** Pure logic behind #904's medication row — no device required. */
class MedicationSegmentsTest {

    @Test
    fun rowHasExactlyTheThreeStoredCodes() {
        assertEquals(setOf("taken", "missed", "unsure"), MedicationSegmentValues.toSet())
        assertEquals(NoteOptions.Medication.toSet(), MedicationSegmentValues.toSet())
    }

    @Test
    fun segmentLabelsUseTheNewCopy() {
        assertEquals(R.string.editor_medication_all_taken, medicationSegmentLabelRes("taken"))
        assertEquals(
            R.string.editor_medication_something_missed,
            medicationSegmentLabelRes("missed"),
        )
        assertEquals(R.string.editor_medication_not_sure, medicationSegmentLabelRes("unsure"))
    }
}
