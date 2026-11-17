package com.caregiver.mobile.presentation.notes

import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.R
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.test.WithTestOwner
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The always-visible optional notes card and its dictate button (#905). */
@RunWith(AndroidJUnit4::class)
class OptionalNotesCardTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun cardShowsLabelFieldAndDictateButtonWithoutDisclosure() {
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    OptionalNotesCard(value = "", onValueChange = {})
                }
            }
        }

        compose.onNodeWithTag("optional_notes_card").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.editor_anything_else))
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
        compose.onNodeWithTag("editor_free_text").assertIsDisplayed()
        compose.onNodeWithTag("editor_dictate_button")
            .assertIsDisplayed()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.ContentDescription,
                    listOf(context.getString(R.string.editor_dictate_cd)),
                ),
            )
        // The old "Add details" disclosure is gone: the field is always open.
        compose.onNodeWithText(context.getString(R.string.editor_add_details))
            .assertDoesNotExist()
    }

    @Test
    fun typingInTheCardUpdatesTheEditorState() {
        var typed = ""
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    OptionalNotesCard(value = "", onValueChange = { typed = it })
                }
            }
        }

        compose.onNodeWithTag("editor_free_text").performTextInput("A quiet afternoon")
        assertEquals("A quiet afternoon", typed)
    }
}
