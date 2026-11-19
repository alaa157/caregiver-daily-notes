package com.caregiver.mobile.presentation.notes

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import com.caregiver.mobile.data.NoteDraftStore
import com.caregiver.mobile.test.WithTestOwner
import java.time.LocalDate
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation checks for the visible side of #906: the "Draft saved"
 * header and restoring an in-progress note after leaving the editor.
 * Instrumentation-only; never runs under testDebugUnitTest.
 */
@RunWith(AndroidJUnit4::class)
class NoteEditorDraftUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var graph: AppGraph
    private lateinit var nav: NavHostController

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        graph = AppGraph(context, forceDemo = true)
        // Start each test with no left-over draft for the demo person.
        NoteDraftStore.create(context)
            .clear(NoteDraftStore.key("r1", LocalDate.now().toString()))
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

    private val moodGood = SemanticsMatcher.expectValue(
        SemanticsProperties.Role,
        Role.RadioButton,
    ) and hasText("Good")

    @Test
    fun changingAFieldShowsDraftSavedInTheHeader() {
        openEditor()

        compose.onNodeWithText("Draft saved").assertIsDisplayed()
        compose.onAllNodesWithText("Good")[0].performClick()

        // Once autosaved the line carries the save time.
        compose.onNodeWithText("Draft saved", substring = true).assertIsDisplayed()
        compose.onAllNodes(moodGood)[0].assertIsSelected()
    }

    @Test
    fun leavingAndReopeningTheEditorRestoresTheDraft() {
        openEditor()
        compose.onAllNodesWithText("Good")[0].performClick()

        compose.runOnIdle { nav.popBackStack() }
        compose.runOnIdle { nav.navigate("note-editor/r1") }

        compose.onAllNodes(moodGood)[0].assertIsSelected()
        compose.onNodeWithText("Draft saved", substring = true).assertIsDisplayed()
    }
}
