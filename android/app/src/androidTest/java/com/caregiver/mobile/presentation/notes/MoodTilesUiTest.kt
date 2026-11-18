package com.caregiver.mobile.presentation.notes

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.core.theme.CaregiverTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** #902 UI: all six moods stay reachable and a stored rare mood is honest. */
@RunWith(AndroidJUnit4::class)
class MoodTilesUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun allSixMoodValuesCanBeWritten() {
        val picked = mutableListOf<String>()
        compose.setContent {
            CaregiverTheme {
                MoodTiles(selected = null, onSelect = { picked += it })
            }
        }

        compose.onNodeWithText("Good").assertIsDisplayed()
        compose.onNodeWithText("Okay").assertIsDisplayed()
        compose.onNodeWithText("Low").assertIsDisplayed()

        listOf("happy", "calm", "sad").forEach { value ->
            compose.onNodeWithTag("mood_tile_$value").performClick()
        }
        // The disclosure reveals the remaining three canonical values.
        compose.onNodeWithTag("mood_more_toggle").performClick()
        listOf("anxious", "agitated", "confused").forEach { value ->
            compose.onNodeWithTag("mood_tile_$value").performClick()
        }

        assertEquals(
            listOf("happy", "calm", "sad", "anxious", "agitated", "confused"),
            picked,
        )
    }

    @Test
    fun storedLessCommonMoodOpensDisclosureWithoutSelectingAHeadlineTile() {
        compose.setContent {
            CaregiverTheme {
                MoodTiles(selected = "anxious", onSelect = {})
            }
        }

        listOf("happy", "calm", "sad").forEach { value ->
            compose.onNodeWithTag("mood_tile_$value").assertIsNotSelected()
        }
        compose.onNodeWithTag("mood_tile_anxious").assertIsDisplayed().assertIsSelected()
    }
}
