package com.caregiver.mobile.presentation.notes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure logic behind #903's appetite row — no device required. */
class AppetiteSegmentsTest {

    @Test
    fun threeSegmentsReplaceTheFourChips() {
        assertEquals(
            setOf("good", "fair", "refused"),
            AppetiteChoices.map(::appetiteWireValue).toSet(),
        )
        // `poor` is display-only: it is not a fourth segment.
        assertEquals(3, AppetiteChoices.size)
    }

    @Test
    fun everyStoredAppetiteValueMapsToASegment() {
        assertEquals(AppetiteChoice.Well, appetiteChoiceFor("good"))
        assertEquals(AppetiteChoice.Little, appetiteChoiceFor("fair"))
        assertEquals(AppetiteChoice.Little, appetiteChoiceFor("poor"))
        assertEquals(AppetiteChoice.Refused, appetiteChoiceFor("refused"))

        assertNull(appetiteChoiceFor(null))
        assertNull(appetiteChoiceFor(""))
        assertNull(appetiteChoiceFor("unknown"))
    }

    @Test
    fun poorShowsAsALittleAndTappingWritesFair() {
        assertEquals(AppetiteChoice.Little, appetiteChoiceFor("poor"))
        assertEquals(R.string.editor_appetite_little, appetiteChoiceLabelRes(AppetiteChoice.Little))
        // Display mapping only: the row never writes `poor` back.
        assertEquals("fair", appetiteWireValue(appetiteChoiceFor("poor")!!))
    }

    @Test
    fun labelsMatchTheRequestedCopy() {
        assertEquals(R.string.editor_appetite_well, appetiteChoiceLabelRes(AppetiteChoice.Well))
        assertEquals(R.string.editor_appetite_little, appetiteChoiceLabelRes(AppetiteChoice.Little))
        assertEquals(R.string.editor_appetite_refused, appetiteChoiceLabelRes(AppetiteChoice.Refused))
    }
}
