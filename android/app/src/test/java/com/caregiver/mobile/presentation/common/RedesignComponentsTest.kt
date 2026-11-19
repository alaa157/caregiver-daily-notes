package com.caregiver.mobile.presentation.common

import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.core.theme.CaregiverTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * UI coverage for the three new redesign components (#894), run on the JVM
 * under Robolectric as part of testDebugUnitTest. The fourth component is the
 * existing AuthPrimaryButton, already covered by AuthPrimaryButtonTest.
 */
@RunWith(AndroidJUnit4::class)
class RedesignComponentsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun sectionCardRendersItsContent() {
        compose.setContent {
            CaregiverTheme {
                SectionCard { Text("Today's notes") }
            }
        }

        compose.onNodeWithTag(SectionCardTestTag).assertIsDisplayed()
        compose.onNodeWithText("Today's notes").assertIsDisplayed()
    }

    @Test
    fun statusChipAlwaysShowsItsLabelDone() {
        compose.setContent {
            CaregiverTheme {
                StatusChip(label = "Done", variant = StatusChipVariant.Done)
            }
        }

        // Text is mandatory: state is never conveyed by colour alone.
        compose.onNodeWithTag(StatusChipTestTag).assertIsDisplayed()
        compose.onNodeWithText("Done").assertIsDisplayed()
    }

    @Test
    fun statusChipAlwaysShowsItsLabelNotYet() {
        compose.setContent {
            CaregiverTheme {
                StatusChip(label = "Not yet", variant = StatusChipVariant.NotYet)
            }
        }

        compose.onNodeWithTag(StatusChipTestTag).assertIsDisplayed()
        compose.onNodeWithText("Not yet").assertIsDisplayed()
    }

    @Test
    fun personAvatarShowsTheInitialAtSmallSize() {
        compose.setContent {
            CaregiverTheme {
                PersonAvatar(initial = "Layla", size = PersonAvatarSize.Small)
            }
        }

        compose.onNodeWithTag(PersonAvatarTestTag).assertIsDisplayed()
        compose.onNodeWithText("L").assertIsDisplayed()
    }

    @Test
    fun personAvatarShowsTheInitialAtLargeSize() {
        compose.setContent {
            CaregiverTheme {
                PersonAvatar(initial = "Layla", size = PersonAvatarSize.Large)
            }
        }

        compose.onNodeWithTag(PersonAvatarTestTag).assertIsDisplayed()
        compose.onNodeWithText("L").assertIsDisplayed()
    }
}
