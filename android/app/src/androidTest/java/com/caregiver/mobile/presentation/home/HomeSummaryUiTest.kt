package com.caregiver.mobile.presentation.home

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.core.navigation.AppRoutes
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.test.WithTestOwner
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Home redesign summary card and its single action (#896), against a fake
 * server. A small NavHost turns navigation into assertable text so a tap must
 * open the editor for the *right* recipient id.
 *
 * Instrumentation-only; never runs under testDebugUnitTest.
 */
@RunWith(AndroidJUnit4::class)
class HomeSummaryUiTest {

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

    private fun setHome() {
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    val navController = rememberNavController()
                    NavHost(navController, startDestination = "home-test") {
                        composable("home-test") { HomeScreen(graph, navController) }
                        composable(AppRoutes.NoteEditor.base) { backStackEntry ->
                            Text("editor:" + backStackEntry.arguments?.getString("recipientId"))
                        }
                    }
                }
            }
        }
    }

    private fun note(recipientId: String, date: String) = buildString {
        append("{\"id\":\"n-$recipientId\",\"recipientId\":\"$recipientId\",\"date\":\"$date\",")
        append("\"mood\":\"good\",\"appetite\":\"good\",\"sleep\":\"good\",")
        append("\"mobility\":\"walks\",\"medicationTaken\":\"taken\",")
        append("\"pain\":1,\"fall\":false,\"text\":\"t\"}")
    }

    @Test
    fun summaryCardShowsTheRealTodaysCount() {
        val today = LocalDate.now().toString()
        server.enqueue(
            MockResponse().setBody(
                """[{"id":"r1","name":"Amina","active":true},{"id":"r2","name":"Baker","active":true}]""",
            ),
        )
        server.enqueue(MockResponse().setBody("[${note("r1", today)}]"))

        setHome()

        compose.onNodeWithTag("home_summary_card").assertIsDisplayed()
        compose.onNodeWithText("1 / 2 done").assertIsDisplayed()
        compose.onNodeWithTag("home_summary_write_note").assertIsDisplayed()
    }

    @Test
    fun onePersonGoesStraightToTheirEditor() {
        server.enqueue(MockResponse().setBody("""[{"id":"r1","name":"Amina","active":true}]"""))
        server.enqueue(MockResponse().setBody("[]"))

        setHome()
        compose.onNodeWithTag("home_summary_write_note").performClick()

        compose.onNodeWithText("editor:r1").assertIsDisplayed()
        compose.onNodeWithText("Who is this note for?").assertDoesNotExist()
    }

    @Test
    fun severalPeopleOpenAPickerAndPickingOpensThatRecipientsEditor() {
        val today = LocalDate.now().toString()
        server.enqueue(
            MockResponse().setBody(
                """[{"id":"r1","name":"Zainab","active":true},{"id":"r2","name":"Baker","active":true},""" +
                    """{"id":"r3","name":"Amina","active":true}]""",
            ),
        )
        server.enqueue(MockResponse().setBody("[${note("r1", today)}]"))

        setHome()
        compose.onNodeWithTag("home_summary_write_note").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("Who is this note for?").fetchSemanticsNodes().isNotEmpty()
        }

        compose.onNodeWithText("Who is this note for?").assertIsDisplayed()
        compose.onNodeWithTag("picker_recipient_r1").assertIsDisplayed()
        compose.onNodeWithTag("picker_recipient_r2").assertIsDisplayed()
        compose.onNodeWithTag("picker_recipient_r3").assertIsDisplayed()
        // The picked id is the one from the Home card, not a re-derived one.
        compose.onNodeWithTag("picker_recipient_r3").performClick()
        compose.onNodeWithText("editor:r3").assertIsDisplayed()
    }
}
