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
import androidx.compose.material3.Text
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.presentation.history.HistoryScreen
import com.caregiver.mobile.presentation.notes.NoteEditorScreen
import com.caregiver.mobile.presentation.summary.SummaryPeriodScreen
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.caregiver.mobile.test.WithTestOwner

/**
 * Task 4 visible flows against a fake server: list → add → create → return
 * with a refreshed list (proving the shared list/form instance), and detail
 * with all four actions reaching their destinations. Instrumentation-only.
 */
@RunWith(AndroidJUnit4::class)
class RecipientsFlowUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var server: MockWebServer
    private lateinit var graph: AppGraph
    private lateinit var nav: androidx.navigation.NavHostController

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        graph = AppGraph(context)
        runBlocking {
            graph.settings.setBaseUrl(server.url("/").toString())
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
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
                composable("history") { HistoryScreen(graph, nav) }
                composable("summary/{recipientId}") {
                    SummaryPeriodScreen(
                        it.arguments?.getString("recipientId")!!,
                        graph,
                        nav,
                    )
                }
                composable("plans") {
                    Text("plans")
                }
                }
            }
        }
    }

    private fun listJson(vararg people: Pair<String, String>) =
        people.joinToString(",", "[", "]") { (id, name) ->
            """{"id":"$id","name":"$name","active":true}"""
        }

    @Test
    fun addFlowRefreshesVisibleListOnReturn() {
        server.enqueue(MockResponse().setBody(listJson("a" to "Aisha")))
        server.enqueue(MockResponse().setBody(listJson("a" to "Aisha")))
        server.enqueue(
            MockResponse().setResponseCode(201)
                .setBody("""{"id":"b","name":"Karim","active":true}"""),
        )
        server.enqueue(MockResponse().setBody(listJson("a" to "Aisha", "b" to "Karim")))
        server.enqueue(MockResponse().setBody(listJson("a" to "Aisha", "b" to "Karim")))
        setNav()

        compose.onNodeWithText("Aisha").assertIsDisplayed()
        compose.onNodeWithText("Add care recipient").performClick()
        compose.onNodeWithTag("add_person_name").performTextInput("Karim")
        compose.onNodeWithTag("add_person_save").performClick()

        compose.onNodeWithText("Karim").assertIsDisplayed()
        compose.onNodeWithText("Aisha").assertIsDisplayed()
        compose.onAllNodesWithText("Add care recipient").assertCountEquals(0)
    }

    @Test
    fun detailShowsFourActionsAndEachNavigates() {
        server.enqueue(MockResponse().setBody(listJson("r1" to "Aisha")))
        server.enqueue(MockResponse().setBody(listJson("r1" to "Aisha")))
        server.enqueue(MockResponse().setBody(listJson("r1" to "Aisha")))
        server.enqueue(MockResponse().setBody("[]"))
        server.enqueue(MockResponse().setBody(listJson("r1" to "Aisha")))
        server.enqueue(MockResponse().setBody("[]"))
        setNav()
        compose.onNodeWithText("Aisha").performClick()

        compose.onNodeWithText("Aisha").assertIsDisplayed()
        compose.onNodeWithText("Add today’s note").performClick()
        assertEquals("note-editor/r1", nav.currentDestination?.route)
        compose.onNodeWithText("Daily note").assertIsDisplayed()
        compose.runOnUiThread { nav.popBackStack() }

        compose.onNodeWithText("History").performClick()
        assertEquals("history", nav.currentDestination?.route)
        compose.onNodeWithText("No care recipients yet. Add one to start recording notes.").assertIsDisplayed()
        compose.runOnUiThread { nav.popBackStack() }

        compose.onNodeWithText("Edit, then accept").performClick()
        assertEquals("plans", nav.currentDestination?.route)
        compose.runOnUiThread { nav.popBackStack() }

        compose.onNodeWithText("View summary").performClick()
        assertEquals("summary/{recipientId}", nav.currentDestination?.route)
        compose.onNodeWithText("View summary").assertIsDisplayed()
    }
}
