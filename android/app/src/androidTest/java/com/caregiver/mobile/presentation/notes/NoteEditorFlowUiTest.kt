package com.caregiver.mobile.presentation.notes

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.core.time.DateFormats
import com.caregiver.mobile.test.WithTestOwner
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task 5 note flows against the offline demo backend (debug builds serve
 * [com.caregiver.mobile.data.demo.DemoBackendApis], so no server is
 * involved): required-field validation, save round trip, double-tap
 * safety, and original/addendum separation. Instrumentation-only.
 */
@RunWith(AndroidJUnit4::class)
class NoteEditorFlowUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var graph: AppGraph
    private lateinit var nav: androidx.navigation.NavHostController

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        graph = AppGraph(context)
    }

    private fun setNav(start: String) {
        compose.setContent {
            nav = rememberNavController()
            WithTestOwner {
                CaregiverTheme {
                    NavHost(navController = nav, startDestination = start) {
                        composable("note-editor/{recipientId}") {
                            NoteEditorScreen(
                                it.arguments?.getString("recipientId")!!,
                                graph,
                                nav,
                            )
                        }
                        composable("note-saved/{noteId}") {
                            Text("saved:" + it.arguments?.getString("noteId"))
                        }
                        composable("note/{noteId}") {
                            NoteDetailScreen(
                                it.arguments?.getString("noteId")!!,
                                graph,
                                nav,
                            )
                        }
                        composable("addendum/{noteId}") {
                            AddendumScreen(
                                it.arguments?.getString("noteId")!!,
                                graph,
                                nav,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun demoNotesFor(recipientId: String) = runBlocking {
        graph.apis.notes().history(recipientId, null, null)
    }

    private fun fillValidNote(text: String) {
        // Mood/Appetite/Sleep each offer "Good"; document order is Mood first.
        compose.onAllNodesWithText("Good").assertCountEquals(3)
        compose.onAllNodesWithText("Good")[0].performClick()
        compose.onNodeWithText("Write any important notes about today…")
            .performTextInput(text)
    }

    @Test
    fun noteFormValidatesRequiredMoodAndText() {
        setNav("note-editor/r1")

        compose.onNodeWithText("Save note").performClick()

        compose.onNodeWithText("Select a mood").assertIsDisplayed()
        compose.onNodeWithText("Enter the note text").assertIsDisplayed()
    }

    @Test
    fun validNoteAppearsAfterSave() {
        setNav("note-editor/r1")
        fillValidNote("Zebra demo note 12345")

        compose.onNodeWithText("Save note").performClick()

        assertEquals("note-saved/{noteId}", nav.currentDestination?.route)
        assertTrue(demoNotesFor("r1").any { it.text == "Zebra demo note 12345" })
    }

    @Test
    fun doubleTapSaveCreatesOneDemoNote() {
        setNav("note-editor/r1")
        fillValidNote("Zebra double tap 67890")

        compose.onNodeWithText("Save note").performClick()
        // The second tap races recomposition: it is either ignored on the
        // disabled button or throws for the missing action. Either way the
        // provider must hold exactly one copy.
        runCatching { compose.onNodeWithText("Save note").performClick() }
        assertEquals("note-saved/{noteId}", nav.currentDestination?.route)
        assertEquals(1, demoNotesFor("r1").count { it.text == "Zebra double tap 67890" })
    }

    @Test
    fun noteDetailSeparatesOriginalFromAddenda() {
        setNav("note/n2")

        compose.onNodeWithText(
            "سقوط بسيط في الحمام — minor fall in the bathroom, pain 8/10.",
        ).assertIsDisplayed()
        compose.onNodeWithText(
            "تصحيح: لم يحدث كسر، مجرد كدمة — correction: bruise only, no fracture.",
        ).assertIsDisplayed()
    }

    @Test
    fun noteDetailShowsMedicationStatus() {
        setNav("note/n2")

        // Demo note n2 records medicationTaken=missed.
        compose.onNodeWithText("Medication").assertIsDisplayed()
        compose.onNodeWithText("Missed").assertIsDisplayed()
    }

    @Test
    fun addendumCardShowsItsCreationDate() {
        setNav("note/n2")

        // Demo addendum a1 shares n2's calendar day, so both the top-bar
        // subtitle and the addendum card render it.
        val expected = DateFormats.historyDay(LocalDate.now().minusDays(2).toString(), false)
        compose.onAllNodesWithText(expected).assertCountEquals(2)
    }

    @Test
    fun addendumAppendsChronologically() {
        setNav("note/n2")

        compose.onNodeWithText("Add correction").performClick()
        assertEquals("addendum/n2", nav.currentDestination?.route)
        compose.onNodeWithText("Write the new information or correction…")
            .performTextInput("Second demo correction")
        compose.onNodeWithText("Save correction").performClick()

        assertEquals("note/n2", nav.currentDestination?.route)
        compose.onNodeWithText(
            "سقوط بسيط في الحمام — minor fall in the bathroom, pain 8/10.",
        ).assertIsDisplayed()
        compose.onNodeWithText(
            "تصحيح: لم يحدث كسر، مجرد كدمة — correction: bruise only, no fracture.",
        ).assertIsDisplayed()
        compose.onNodeWithText("Second demo correction").assertIsDisplayed()

        compose.onNodeWithText("Add correction").performClick()
        assertEquals("addendum/n2", nav.currentDestination?.route)
        compose.onNodeWithText("Write the new information or correction…")
            .performTextInput("Third demo correction")
        compose.onNodeWithText("Save correction").performClick()

        assertEquals("note/n2", nav.currentDestination?.route)
        compose.onNodeWithText("Third demo correction").assertIsDisplayed()
    }
}
