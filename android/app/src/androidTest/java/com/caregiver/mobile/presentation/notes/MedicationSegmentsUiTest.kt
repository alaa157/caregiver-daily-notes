package com.caregiver.mobile.presentation.notes

import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.core.theme.CaregiverTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** #904 UI: all three medication values are writable and honestly announced. */
@RunWith(AndroidJUnit4::class)
class MedicationSegmentsUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun eachSegmentWritesItsOwnCode() {
        val picked = mutableListOf<String>()
        compose.setContent {
            CaregiverTheme {
                MedicationSegments(selected = null, onSelect = { picked += it })
            }
        }

        compose.onNodeWithContentDescription("All taken").assertIsDisplayed()
        compose.onNodeWithContentDescription("Something missed").assertIsDisplayed()
        compose.onNodeWithContentDescription("Not sure").assertIsDisplayed()

        listOf("taken", "missed", "unsure").forEach { value ->
            compose.onNodeWithTag("medication_segment_$value").performClick()
        }
        assertEquals(listOf("taken", "missed", "unsure"), picked)
    }

    @Test
    fun selectedSegmentIsAnnouncedAsSelected() {
        compose.setContent {
            CaregiverTheme {
                MedicationSegments(selected = "missed", onSelect = {})
            }
        }

        compose.onNodeWithTag("medication_segment_missed").assertIsSelected()
        compose.onNodeWithTag("medication_segment_taken").assertIsNotSelected()
        compose.onNodeWithTag("medication_segment_unsure").assertIsNotSelected()
    }

    @Test
    fun segmentsMeetTheMinimumTouchTarget() {
        compose.setContent {
            CaregiverTheme {
                MedicationSegments(selected = null, onSelect = {})
            }
        }
        listOf("taken", "missed", "unsure").forEach { value ->
            compose.onNodeWithTag("medication_segment_$value").assertHeightIsAtLeast(44.dp)
        }
    }
}
