package com.caregiver.mobile.presentation.notes

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.test.WithTestOwner
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * #907 UI: the saved confirmation names the person and keeps all three
 * navigation targets. Instrumentation-only; never runs under unit tests.
 */
@RunWith(AndroidJUnit4::class)
class NoteSavedUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var server: MockWebServer
    private lateinit var graph: AppGraph

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        graph = AppGraph(context, forceProduction = true)
        runBlocking {
            graph.settings.setBaseUrl(server.url("/").toString())
            graph.settings.setEmail("layla@example.com")
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun detailJson() =
        """{"id":"n1","recipientId":"r1","date":"2026-11-17","mood":"good",""" +
            """"appetite":"good","sleep":"good","mobility":"walks","medicationTaken":"taken",""" +
            """"pain":1,"fall":false,"text":"t","addenda":[],"painRecorded":true,"fallRecorded":false}"""

    private fun setSaved() {
        // First the note (gives us the recipient id), then the name lookup.
        server.enqueue(MockResponse().setBody(detailJson()))
        server.enqueue(
            MockResponse().setBody("""{"id":"r1","name":"Amina","active":true}"""),
        )
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    val nav = rememberNavController()
                    NavHost(nav, startDestination = "note-saved/n1") {
                        composable("home") { Text("home screen") }
                        composable("note/{noteId}") { entry ->
                            Text("detail:" + entry.arguments?.getString("noteId"))
                        }
                        composable("summary/{recipientId}") { entry ->
                            Text("summary:" + entry.arguments?.getString("recipientId"))
                        }
                        composable("note-saved/{noteId}") { entry ->
                            NoteSavedScreen(
                                entry.arguments?.getString("noteId")!!,
                                graph,
                                nav,
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun confirmationNamesThePersonAndOffersBothPrimaryActions() {
        setSaved()

        compose.onNodeWithText("Note saved for", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Back to Home").assertIsDisplayed()
        compose.onNodeWithTag("saved_back_home").assertIsDisplayed()
        compose.onNodeWithTag("saved_view").assertIsDisplayed()
        compose.onNodeWithText("Create summary").assertIsDisplayed()
    }

    @Test
    fun backToHomePopsToHomeSoItResumes() {
        setSaved()

        compose.onNodeWithTag("saved_back_home").performClick()

        compose.onNodeWithText("home screen").assertIsDisplayed()
    }

    @Test
    fun viewNoteTargetIsKept() {
        setSaved()

        compose.onNodeWithTag("saved_view").performClick()

        compose.onNodeWithText("detail:n1").assertIsDisplayed()
    }

    @Test
    fun summaryTargetIsKept() {
        setSaved()

        compose.onNodeWithText("Create summary").performClick()

        compose.onNodeWithText("summary:r1").assertIsDisplayed()
    }
}
