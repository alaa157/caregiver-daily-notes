package com.caregiver.mobile.presentation.notes

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertDoesNotExist
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
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** #903 UI: one independent row, all three values writable, `poor` honest. */
@RunWith(AndroidJUnit4::class)
class AppetiteSegmentsUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun threeSegmentsWriteTheirWireValues() {
        val picked = mutableListOf<String>()
        compose.setContent {
            CaregiverTheme {
                AppetiteSegments(selected = null, onSelect = { picked += it })
            }
        }

        compose.onNodeWithText("Well").assertIsDisplayed()
        compose.onNodeWithText("A little").assertIsDisplayed()
        compose.onNodeWithText("Refused").assertIsDisplayed()

        listOf("good", "fair", "refused").forEach { value ->
            compose.onNodeWithTag("appetite_segment_$value").performClick()
        }

        assertEquals(listOf("good", "fair", "refused"), picked)
    }

    @Test
    fun storedPoorSelectsALittleAndShowsNoPoorSegment() {
        compose.setContent {
            CaregiverTheme {
                AppetiteSegments(selected = "poor", onSelect = {})
            }
        }

        compose.onNodeWithTag("appetite_segment_fair").assertIsSelected()
        compose.onNodeWithTag("appetite_segment_good").assertIsNotSelected()
        compose.onNodeWithTag("appetite_segment_refused").assertIsNotSelected()
        compose.onNodeWithText("Poor").assertDoesNotExist()
    }

    @Test
    fun tappingTheSelectedValueClearsIt() {
        val selected = mutableStateOf<String?>("fair")
        compose.setContent {
            CaregiverTheme {
                AppetiteSegments(
                    selected = selected.value,
                    onSelect = { value ->
                        selected.value = if (selected.value == value) null else value
                    },
                )
            }
        }

        compose.onNodeWithTag("appetite_segment_fair").assertIsSelected()
        compose.onNodeWithTag("appetite_segment_fair").performClick()

        assertNull(selected.value)
    }
}
