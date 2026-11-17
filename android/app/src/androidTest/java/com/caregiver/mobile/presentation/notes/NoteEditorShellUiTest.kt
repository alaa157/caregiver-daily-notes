package com.caregiver.mobile.presentation.notes

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNode
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.presentation.common.SectionCardTestTag
import com.caregiver.mobile.test.WithTestOwner
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation checks for the #901 shell on the offline demo backend:
 * header, progress bar, one SectionCard per group, and the close/discard
 * rule. Instrumentation-only — never runs under testDebugUnitTest.
 */
@RunWith(AndroidJUnit4::class)
class NoteEditorShellUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var graph: AppGraph
    private lateinit var nav: NavHostController

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        graph = AppGraph(context)
    }

    private fun openEditor() {
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    nav = rememberNavController()
                    NavHost(nav, startDestination = "left") {
                        composable("left") { Text("left screen") }
                        composable("note-editor/{recipientId}") { entry ->
                            NoteEditorScreen(
                                entry.arguments?.getString("recipientId")!!,
                                graph,
                                nav,
                            )
                        }
                    }
                }
            }
        }
        compose.runOnIdle { nav.navigate("note-editor/r1") }
    }

    @Test
    fun headerShowsNameDraftStatusAndProgress() {
        openEditor()

        // Substring avoids the bidi isolate characters around the name.
        compose.onNodeWithText("Note for", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Today's note").assertIsDisplayed()
        compose.onNodeWithText("Draft saved", substring = true).assertIsDisplayed()
        compose.onNodeWithText("0 of 7 done").assertIsDisplayed()
        compose.onNodeWithText("Nothing is required").assertIsDisplayed()
        compose.onNodeWithTag("editor_progress").assertIsDisplayed()
        compose.onNode(hasContentDescription("Close editor")).assertIsDisplayed()
    }

    @Test
    fun sevenGroupsAndTheFreeTextCardAreSectionCards() {
        openEditor()

        // Seven question groups plus the free-text card, in one column.
        compose.onAllNodesWithTag(SectionCardTestTag).assertCountEquals(8)
        compose.onNodeWithText("Mood").assertIsDisplayed()
        compose.onNodeWithText("Appetite").assertIsDisplayed()
        compose.onNodeWithText("Medication").assertIsDisplayed()
        compose.onNodeWithText("Pain").assertIsDisplayed()
    }

    @Test
    fun saveButtonStaysPinnedAndCountTracksAnswers() {
        openEditor()
        compose.onNodeWithTag("editor_save").assertIsDisplayed()

        // Mood/Appetite/Sleep each offer "Good"; mood is first in document order.
        compose.onAllNodesWithText("Good")[0].performClick()

        compose.onNodeWithText("1 of 7 done").assertIsDisplayed()
        compose.onNodeWithTag("editor_save").assertIsDisplayed()
    }

    @Test
    fun closingAnEmptyFormLeavesWithoutAsking() {
        openEditor()

        compose.onNode(hasContentDescription("Close editor")).performClick()

        compose.onNodeWithText("Discard unsaved changes?").assertDoesNotExist()
        compose.onNodeWithText("left screen").assertIsDisplayed()
    }

    @Test
    fun closingWithChangesAsksAndKeepEditingStays() {
        openEditor()
        compose.onAllNodesWithText("Good")[0].performClick()

        compose.onNode(hasContentDescription("Close editor")).performClick()

        compose.onNodeWithText("Discard unsaved changes?").assertIsDisplayed()
        compose.onNodeWithText("Keep editing").performClick()
        compose.onNodeWithText("Discard unsaved changes?").assertDoesNotExist()
        compose.onNodeWithText("1 of 7 done").assertIsDisplayed()
    }
}
