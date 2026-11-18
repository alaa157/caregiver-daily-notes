package com.caregiver.mobile.presentation.notes

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.core.theme.CaregiverTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** #903 UI: the segmented appetite row stays honest for stored `poor`. */
@RunWith(AndroidJUnit4::class)
class AppetiteSegmentsUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun storedPoorShowsALittleSelectedAndTappingItWritesFair() {
        val picked = mutableListOf<String>()
        compose.setContent {
            CaregiverTheme {
                AppetiteSegments(selected = "poor", onSelect = { picked += it })
            }
        }

        compose.onNodeWithText("Well").assertIsDisplayed()
        compose.onNodeWithText("A little").assertIsDisplayed()
        compose.onNodeWithText("Refused").assertIsDisplayed()
        compose.onNodeWithTag("appetite_segment_fair").assertIsSelected()
        compose.onNodeWithTag("appetite_segment_good").assertIsNotSelected()
        compose.onNodeWithTag("appetite_segment_refused").assertIsNotSelected()

        // Display-only fold: the editor receives `fair`, not `poor`.
        compose.onNodeWithTag("appetite_segment_fair").performClick()
        assertEquals(listOf("fair"), picked)
    }

    @Test
    fun eachSegmentWritesItsOwnCode() {
        val picked = mutableListOf<String>()
        compose.setContent {
            CaregiverTheme {
                AppetiteSegments(selected = null, onSelect = { picked += it })
            }
        }

        listOf("good", "fair", "refused").forEach { value ->
            compose.onNodeWithTag("appetite_segment_$value").performClick()
        }
        assertEquals(listOf("good", "fair", "refused"), picked)
    }

    @Test
    fun segmentsMeetTheMinimumTouchTarget() {
        compose.setContent {
            CaregiverTheme {
                AppetiteSegments(selected = null, onSelect = {})
            }
        }
        listOf("good", "fair", "refused").forEach { value ->
            compose.onNodeWithTag("appetite_segment_$value").assertHeightIsAtLeast(44.dp)
        }
    }
}
