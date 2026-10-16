package com.caregiver.mobile.presentation.summary

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
 * Task 7: the illustrative-demo label is driven by the actual serving
 * provider ([AppGraph.isDemo]) — present for offline demo summaries next
 * to their source-note evidence, absent for production/backend summaries.
 * Instrumentation-only.
 */
@RunWith(AndroidJUnit4::class)
class SummaryDemoLabelUiTest {

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

    private fun productionGraph(): AppGraph {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return AppGraph(context, forceProduction = true).also { graph ->
            runBlocking {
                graph.auth.signOut()
                graph.settings.resetBaseUrl()
                graph.settings.setBaseUrl(server.url("/").toString())
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

    @Test
    fun demoSummaryShowsIllustrativeLabelBesideEvidence() {
        setResultNav(demoGraph())

        compose.onNodeWithText("Illustrative demo summary").assertIsDisplayed()
        compose.onNodeWithText(
            "Generated offline from demo notes for illustration. Always check the source notes below.",
        ).assertIsDisplayed()
        // The label never replaces evidence: demo source notes stay visible.
        compose.onNodeWithText("Moral is high today", substring = true).assertIsDisplayed()
    }

    @Test
    fun productionSummaryShowsNoDemoLabel() {
        server.enqueue(
            MockResponse().setBody(
                """{"id":"s1","recipientId":"r1","periodDays":7,"text":"Reviewed 3 day(s) of notes.","redFlags":[],"""" +
                    """"evidence":[{"noteId":"n1","quote":"rest Matters"}],"uncertainties":[]}""",
            ),
        )
        setResultNav(productionGraph())

        compose.onNodeWithText("Reviewed 3 day(s) of notes.").assertIsDisplayed()
        compose.onNodeWithText("Illustrative demo summary").assertDoesNotExist()
        compose.onNodeWithText(
            "Generated offline from demo notes for illustration. Always check the source notes below.",
        ).assertDoesNotExist()
    }
}
