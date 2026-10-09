package com.caregiver.mobile.presentation.recipients

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.presentation.history.HistoryScreen
import com.caregiver.mobile.presentation.notes.NoteDetailScreen
import com.caregiver.mobile.presentation.notes.NoteEditorScreen
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.caregiver.mobile.test.WithTestOwner

/**
 * Offline visible flows against seeded Room data: list → add → create →
 * return with a refreshed list, and detail with add-note plus timeline
 * navigation. Instrumentation-only.
 */
@RunWith(AndroidJUnit4::class)
class RecipientsFlowUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var graph: AppGraph
    private lateinit var nav: androidx.navigation.NavHostController

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        graph = AppGraph(context)
        runBlocking {
            graph.demoNotes.resetToSeed()
        }
    }

    private fun setNav() {
        compose.setContent {
            nav = rememberNavController()
            WithTestOwner {
                NavHost(navController = nav, startDestination = "people") {
                composable("people") { RecipientsScreen(graph, nav) }
                composable("add-recipient") { AddRecipientScreen(graph, nav) }
                composable("recipient/{recipientId}") {
                    RecipientDetailScreen(
                        it.arguments?.getString("recipientId")!!,
                        graph,
                        nav,
                    )
                }
                composable("note-editor/{recipientId}") {
                    NoteEditorScreen(
                        it.arguments?.getString("recipientId")!!,
                        graph,
                        nav,
                    )
                }
                composable("note/{noteId}") {
                    NoteDetailScreen(
                        it.arguments?.getString("noteId")!!,
                        graph,
                        nav,
                    )
                }
                composable("history") { HistoryScreen(graph, nav) }
                }
            }
        }
    }

    @Test
    fun addFlowRefreshesVisibleListOnReturn() {
        setNav()

        compose.onNodeWithText("Layla H.").assertIsDisplayed()
        compose.onNodeWithText("Add care recipient").performClick()
        compose.onNodeWithTag("add_person_name").performTextInput("Karim")
        compose.onNodeWithTag("add_person_save").performClick()

        compose.onNodeWithText("Karim").assertIsDisplayed()
        compose.onNodeWithText("Layla H.").assertIsDisplayed()
        compose.onAllNodesWithText("Add care recipient").assertCountEquals(0)
    }

    @Test
    fun detailAddNoteAndTimelineNavigate() {
        setNav()
        compose.onNodeWithText("Layla H.").performClick()

        compose.onAllNodesWithText("Layla H.").assertCountEquals(2)
        compose.onNodeWithText("Add today’s note").performClick()
        assertEquals("note-editor/r-layla", nav.currentDestination?.route)
        compose.onNodeWithText("Daily note").assertIsDisplayed()
        compose.runOnUiThread { nav.popBackStack() }

        compose.onNodeWithText(
            "Ate well today and walked to the garden in the morning.",
            substring = true,
        ).performClick()
        assertTrue(
            (nav.currentDestination?.route ?: "").startsWith("note/"),
        )
    }
}
