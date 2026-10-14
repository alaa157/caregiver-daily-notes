package com.caregiver.mobile.presentation.plans

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
 * Task 7 plan flows against a fake server: empty list, suggestion
 * acceptance, and version-history navigation. Edit-save round trips are
 * covered by `PlanEditViewModelTest`. Instrumentation-only.
 */
@RunWith(AndroidJUnit4::class)
class PlanFlowUiTest {

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
                    NavHost(navController = nav, startDestination = "plans") {
                        composable("plans") { PlansScreen(graph, nav) }
                        composable("plan-proposal/{planId}") {
                            PlanProposalScreen(
                                it.arguments?.getString("planId")!!,
                                graph,
                                nav,
                            )
                        }
                        composable("plan-versions/{planId}") {
                            PlanVersionsScreen(
                                it.arguments?.getString("planId")!!,
                                graph,
                                nav,
                            )
                        }
                    }
                }
            }
        }
    }

    private fun recipients() = """[{"id":"r1","name":"Aisha","active":true}]"""

    private fun plan(status: String) =
        """{"id":"p1","recipientId":"r1","versions":[{"version":1,"status":"Accepted","items":["Walk"],"createdAt":"2026-10-01","reason":""},{"version":2,"status":"$status","items":["Mat"],"createdAt":"2026-10-07","reason":""}]}"""

    @Test
    fun emptyPlanListShowsEmptyState() {
        server.enqueue(MockResponse().setBody("[]"))
        server.enqueue(MockResponse().setBody("[]"))
        setNav()

        compose.onNodeWithText("No active care plan yet").assertIsDisplayed()
        compose.onNodeWithText("View proposals").assertIsDisplayed()
    }

    @Test
    fun acceptSuggestionMarksAccepted() {
        server.enqueue(MockResponse().setBody(recipients()))
        server.enqueue(MockResponse().setBody("[" + plan("Suggested") + "]"))
        server.enqueue(MockResponse().setBody("[" + plan("Suggested") + "]"))
        server.enqueue(MockResponse().setBody(plan("Accepted")))
        server.enqueue(MockResponse().setBody("[" + plan("Accepted") + "]"))
        setNav()

        compose.onNodeWithText("Aisha").performClick()
        assertEquals("plan-proposal/p1", nav.currentDestination?.route)
        compose.onNodeWithText("Accept plan").performClick()

        compose.onNodeWithText("Accepted").assertIsDisplayed()
    }

    @Test
    fun versionsScreenShowsHistory() {
        server.enqueue(MockResponse().setBody(recipients()))
        server.enqueue(MockResponse().setBody("[" + plan("Suggested") + "]"))
        server.enqueue(MockResponse().setBody("[" + plan("Suggested") + "]"))
        server.enqueue(MockResponse().setBody("[" + plan("Suggested") + "]"))
        setNav()

        compose.onNodeWithText("Aisha").performClick()
        compose.onNodeWithText("Version history").performClick()

        assertEquals("plan-versions/p1", nav.currentDestination?.route)
        compose.onNodeWithText("Version 2").assertIsDisplayed()
        compose.onNodeWithText("Version 1").assertIsDisplayed()
    }
}
