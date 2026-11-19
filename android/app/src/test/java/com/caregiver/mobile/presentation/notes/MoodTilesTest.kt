package com.caregiver.mobile.presentation.notes

import com.caregiver.mobile.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure logic behind #902's mood tiles — no device required. */
class MoodTilesTest {

    @Test
    fun tilesAndDisclosureCoverEveryMoodValue() {
        assertEquals(setOf("happy", "calm", "sad"), PrimaryMoodValues.toSet())
        assertEquals(setOf("anxious", "agitated", "confused"), MoreMoodValues.toSet())
        // Nothing the model supports is lost: the union is the canonical six.
        assertEquals(NoteOptions.Mood.toSet(), (PrimaryMoodValues + MoreMoodValues).toSet())
    }

    @Test
    fun storedLessCommonMoodOpensTheDisclosure() {
        listOf("anxious", "agitated", "confused").forEach {
            assertTrue(shouldExpandMoreMoods(it))
        }
        listOf("happy", "calm", "sad", "good", null).forEach {
            assertFalse(shouldExpandMoreMoods(it))
        }
    }

    @Test
    fun headlineTilesUseTheNewGoodOkayLowCopy() {
        assertEquals(R.string.editor_mood_tile_good, moodTileLabelRes("happy"))
        assertEquals(R.string.editor_mood_tile_okay, moodTileLabelRes("calm"))
        assertEquals(R.string.editor_mood_tile_low, moodTileLabelRes("sad"))
    }
}
