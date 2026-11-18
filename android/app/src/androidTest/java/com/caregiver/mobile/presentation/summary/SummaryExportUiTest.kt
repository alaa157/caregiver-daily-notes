package com.caregiver.mobile.presentation.summary

import android.content.Context
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
 * PDF export/share entry points on the summary result screen: the privacy
 * confirmation gates sharing, the demo summary is never shareable, and a
 * failure state with nothing to export hides the actions (the PDF is only
 * offered in Content, or in failure states that carry flags/a previous
 * summary). Instrumentation-only.
 */
@RunWith(AndroidJUnit4::class)
class SummaryExportUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun productionGraph(): AppGraph {
        // forceProduction: the backend summary path (no demo label).
        val context = ApplicationProvider.getApplicationContext<Context>()
        return AppGraph(context, forceProduction = true).also { graph ->
            runBlocking {
                graph.auth.signOut()
                graph.settings.resetBaseUrl()
                graph.settings.setBaseUrl(server.url("/").toString())
            }
        }
    }

    private fun demoGraph(): AppGraph {
        // Debug test builds serve DemoBackendApis; forceProduction stays
        // false so this exercises the real provider-selection path.
        val context = ApplicationProvider.getApplicationContext<Context>()
        return AppGraph(context).also { graph ->
            runBlocking {
                graph.auth.signOut()
                graph.settings.resetBaseUrl()
            }
        }
    }

    private fun setResultNav(graph: AppGraph) {
        compose.setContent {
            val nav = rememberNavController()
            WithTestOwner {
                CaregiverTheme {
                    NavHost(navController = nav, startDestination = "summary-result/r1/7") {
                        composable("summary-result/{recipientId}/{periodDays}") { entry ->
                            SummaryResultScreen(
                                entry.arguments?.getString("recipientId")!!,
                                entry.arguments?.getString("periodDays")?.toIntOrNull() ?: 7,
                                graph,
                                nav,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun summaryJson(text: String) =
        """{"id":"s1","recipientId":"r1","periodDays":7,"text":"$text","redFlags":["FALL_REPORTED"],""" +
            """"evidence":[{"noteId":"n1","quote":"rest Matters"}],"uncertainties":[]}"""

    @Test
    fun contentShowsExportPdfAndShare() {
        server.enqueue(MockResponse().setBody(summaryJson("Server summary body")))
        setResultNav(productionGraph())

        compose.onNodeWithText("Export PDF").assertIsDisplayed()
        compose.onNodeWithText("Share with doctor").assertIsDisplayed()
    }

    @Test
    fun shareRequiresConfirmationBeforeTheChooser() {
        server.enqueue(MockResponse().setBody(summaryJson("Server summary body")))
        setResultNav(productionGraph())

        compose.onNodeWithText("Share with doctor").performClick()
        // Tapping never shares on its own: a confirmation appears first.
        compose.onNodeWithText("Share care summary?").assertIsDisplayed()
        compose.onNodeWithText(
            "This summary may contain sensitive care or health information. Continue only if you intend to share it with someone you trust.",
        ).assertIsDisplayed()

        // Cancelling dismisses without any share attempt: no export error,
        // the screen is intact.
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Share care summary?").assertDoesNotExist()
        compose.onNodeWithText("Could not create the PDF. Please try again.").assertDoesNotExist()
        compose.onNodeWithText("Server summary body").assertIsDisplayed()
    }

    @Test
    fun continueDismissesTheConfirmation() {
        server.enqueue(MockResponse().setBody(summaryJson("Server summary body")))
        setResultNav(productionGraph())

        compose.onNodeWithText("Share with doctor").performClick()
        compose.onNodeWithText("Share care summary?").assertIsDisplayed()

        // "Continue" dismisses the confirmation and fires the single share
        // attempt (guarded by shareLaunched). In a bare test device the
        // chooser has no handler, so the launch fails softly into the
        // localized error instead of crashing — the important assertions
        // are that the dialog is gone and the screen stays alive.
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Share care summary?").assertDoesNotExist()
        compose.onNodeWithText("Export PDF").assertIsDisplayed()
    }

    @Test
    fun demoSummaryHasExportButNoShare() {
        setResultNav(demoGraph())

        compose.onNodeWithText("Illustrative demo summary").assertIsDisplayed()
        // Export is allowed (local data), sharing is not (illustrative).
        compose.onNodeWithText("Export PDF").assertIsDisplayed()
        compose.onNodeWithText("Share with doctor").assertDoesNotExist()
    }

    @Test
    fun failureWithoutAnythingToExportHidesTheActions() {
        // HTTP failure on first generation: Rejected state, no flags and
        // no previous summary — there is nothing meaningful to export.
        server.enqueue(MockResponse().setResponseCode(500))
        setResultNav(productionGraph())

        compose.onNodeWithText("Could not generate the summary. Please try again.")
            .assertIsDisplayed()
        compose.onNodeWithText("Export PDF").assertDoesNotExist()
        compose.onNodeWithText("Share with doctor").assertDoesNotExist()
    }
}
