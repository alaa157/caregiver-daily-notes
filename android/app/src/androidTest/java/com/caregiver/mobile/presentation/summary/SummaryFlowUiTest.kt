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
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task 7 summary flows against a fake server: period selection drives
 * navigation, the result labels illustrative content with disclaimer,
 * evidence and uncertainty, and failed generation retries cleanly.
 * Instrumentation-only.
 */
@RunWith(AndroidJUnit4::class)
class SummaryFlowUiTest {

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

    private fun setNav(start: String) {
        compose.setContent {
            nav = rememberNavController()
            WithTestOwner {
                CaregiverTheme {
                    NavHost(navController = nav, startDestination = start) {
                        composable("summary/{recipientId}") {
                            SummaryPeriodScreen(
                                it.arguments?.getString("recipientId")!!,
                                graph,
                                nav,
                            )
                        }
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
            """"evidence":[{"noteId":"n1","quote":"rest Matters"}],"uncertainties":[{"topic":"meds","detail":"unsure dose"}]}"""

    @Test
    fun periodSelectionAndGenerateNavigates() {
        setNav("summary/r1")

        compose.onNodeWithText("30 days").performClick()
        compose.onNodeWithText("Generate summary").performClick()

        assertEquals("summary-result/r1/30", nav.currentDestination?.route)
    }

    @Test
    fun disclaimerEvidenceAndFlagsShown() {
        server.enqueue(MockResponse().setBody(summaryJson("Demo summary body")))
        setNav("summary-result/r1/7")

        compose.onNodeWithText("A suggestion, not a prescription").assertIsDisplayed()
        compose.onNodeWithText("Fall alert").assertIsDisplayed()
        compose.onNodeWithText("Reported fall").assertIsDisplayed()
        compose.onNodeWithText("Demo summary body").assertIsDisplayed()
        compose.onNodeWithText("rest Matters", substring = true).assertIsDisplayed()
        compose.onNodeWithText("meds: unsure dose").assertIsDisplayed()
    }

    @Test
    fun failedGenerationShowsErrorAndRetry() {
        server.enqueue(MockResponse().setResponseCode(500))
        setNav("summary-result/r1/7")

        compose.onNodeWithText("Could not generate the summary. Please try again.")
            .assertIsDisplayed()
        server.enqueue(MockResponse().setBody(summaryJson("Recovered summary")))
        compose.onNodeWithText("Retry").performClick()
        compose.onNodeWithText("Recovered summary").assertIsDisplayed()
    }
}
