package com.caregiver.mobile.presentation.notes

import com.caregiver.mobile.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure logic behind #903's appetite segmented row — no device required. */
class AppetiteSegmentsTest {

    @Test
    fun poorIsFoldedIntoALittleForDisplayOnly() {
        assertEquals("fair", displayedAppetite("poor"))
        assertEquals("good", displayedAppetite("good"))
        assertEquals("fair", displayedAppetite("fair"))
        assertEquals("refused", displayedAppetite("refused"))
        assertNull(displayedAppetite(null))
        assertNull(displayedAppetite(""))
        assertNull(displayedAppetite("reduced"))
    }

    @Test
    fun rowHasExactlyTheThreeCurrentCodes() {
        assertEquals(setOf("good", "fair", "refused"), AppetiteSegmentValues.toSet())
    }

    @Test
    fun segmentLabelsUseTheNewCopy() {
        assertEquals(R.string.editor_appetite_well, appetiteSegmentLabelRes("good"))
        assertEquals(R.string.editor_appetite_little, appetiteSegmentLabelRes("fair"))
        assertEquals(R.string.editor_appetite_refused, appetiteSegmentLabelRes("refused"))
    }
}
