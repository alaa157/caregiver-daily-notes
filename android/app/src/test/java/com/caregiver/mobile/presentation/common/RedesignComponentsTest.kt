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
    fun statusChipAlwaysShowsItsLabel() {
        listOf("Done" to StatusChipVariant.Done, "Not yet" to StatusChipVariant.NotYet)
            .forEach { (label, variant) ->
                compose.setContent {
                    CaregiverTheme {
                        StatusChip(label = label, variant = variant)
                    }
                }

                // Text is mandatory: state is never conveyed by colour alone.
                compose.onNodeWithTag(StatusChipTestTag).assertIsDisplayed()
                compose.onNodeWithText(label).assertIsDisplayed()
            }
    }

    @Test
    fun personAvatarShowsTheInitialAtBothSizes() {
        listOf(PersonAvatarSize.Small, PersonAvatarSize.Large).forEach { size ->
            compose.setContent {
                CaregiverTheme {
                    PersonAvatar(initial = "Layla", size = size)
                }
            }

            compose.onNodeWithTag(PersonAvatarTestTag).assertIsDisplayed()
            compose.onNodeWithText("L").assertIsDisplayed()
        }
    }
}
