package com.caregiver.mobile.presentation.history

import android.content.Context
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.presentation.notes.NoteDetailScreen
import com.caregiver.mobile.test.WithTestOwner
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task 6 history flows against a fake server: recipient filtering,
 * empty/error states with retry, entry-to-detail navigation, and long
 * Arabic previews. Date-dialog interaction stays in
 * `HistoryViewModelTest`; range plumbing is asserted there.
 * Instrumentation-only.
 */
@RunWith(AndroidJUnit4::class)
class HistoryFlowUiTest {

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
        graph = AppGraph(context, forceProduction = true)
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
                CaregiverTheme {
                    NavHost(navController = nav, startDestination = "history") {
                        composable("history") { HistoryScreen(graph, nav) }
                        composable("note/{noteId}") {
                            NoteDetailScreen(
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

    private fun people() =
        """[{"id":"r1","name":"Aisha","active":true},{"id":"r2","name":"Karim","active":true}]"""

    private fun note(
        id: String,
        recipientId: String,
        date: String,
        text: String,
        pain: Int = 1,
        fall: Boolean = false,
    ) = """{"id":"$id","recipientId":"$recipientId","date":"$date","mood":"good","appetite":"good","sleep":"good","mobility":"walks","medicationTaken":"taken","pain":$pain,"fall":$fall,"text":"$text"}"""

    private fun detail(id: String, recipientId: String, date: String, text: String) =
        """{"id":"$id","recipientId":"$recipientId","date":"$date","mood":"good","appetite":"good","sleep":"good","mobility":"walks","medicationTaken":"taken","pain":1,"fall":false,"text":"$text","addenda":[]}"""

    @Test
    fun recipientFilterUpdatesResults() {
        val day = "2026-10-07"
        server.enqueue(MockResponse().setBody(people()))
        server.enqueue(
            MockResponse().setBody(
                "[" + note("n1", "r1", day, "Alpha note") + "," +
                    note("n2", "r2", day, "Beta note") + "]",
            ),
        )
        server.enqueue(MockResponse().setBody(people()))
        server.enqueue(MockResponse().setBody("[" + note("n1", "r1", day, "Alpha note") + "]"))
        setNav()

        compose.onNodeWithText("Alpha note").assertIsDisplayed()
        compose.onNodeWithText("Beta note").assertIsDisplayed()
        compose.onNodeWithText("All").performClick()
        compose.onNodeWithText("Aisha").performClick()

        compose.onNodeWithText("Alpha note").assertIsDisplayed()
        compose.onNodeWithText("Beta note").assertDoesNotExist()
    }

    @Test
    fun emptyRangeShowsEmptyState() {
        server.enqueue(MockResponse().setBody(people()))
        server.enqueue(MockResponse().setBody("[]"))
        setNav()

        compose.onNodeWithText("No notes").assertIsDisplayed()
    }

    @Test
    fun failedHistoryCanRetry() {
        server.enqueue(MockResponse().setBody(people()))
        server.enqueue(MockResponse().setResponseCode(500))
        setNav()

        compose.onNodeWithText("Could not load").assertIsDisplayed()
        server.enqueue(MockResponse().setBody(people()))
        server.enqueue(MockResponse().setBody("[]"))
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("No notes").assertIsDisplayed()
    }

    @Test
    fun entryOpensNoteDetail() {
        val day = "2026-10-07"
        server.enqueue(MockResponse().setBody(people()))
        server.enqueue(MockResponse().setBody("[" + note("n1", "r1", day, "Alpha note") + "]"))
        server.enqueue(MockResponse().setBody(detail("n1", "r1", day, "Alpha note")))
        setNav()

        compose.onNodeWithText("View note").performClick()

        assertEquals("note/n1", nav.currentDestination?.route)
        compose.onNodeWithText("Alpha note").assertIsDisplayed()
    }

    @Test
    fun longArabicPreviewWraps() {
        val long = "ملاحظة طويلة جدا عن حالة اليوم ".repeat(20)
        server.enqueue(MockResponse().setBody(people()))
        server.enqueue(
            MockResponse().setBody("[" + note("n9", "r1", "2026-10-07", long) + "]"),
        )
        setNav()

        compose.onNodeWithText(long.take(120)).assertIsDisplayed()
    }
}
