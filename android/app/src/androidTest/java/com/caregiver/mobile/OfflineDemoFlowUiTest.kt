package com.caregiver.mobile

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.data.SettingsStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.caregiver.mobile.test.WithTestOwner

/**
 * Plan Task 1: the real app flow against the offline demo backend — no
 * MockWebServer, no configured server URL. Starts at [CaregiverRoot],
 * enters the debug demo, walks recipients → detail → editor → saved →
 * detail, and proves the saved note is immediately visible while the
 * backend URL stays at its untouched default. Instrumentation-only.
 */
@RunWith(AndroidJUnit4::class)
class OfflineDemoFlowUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var graph: AppGraph

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Debug test builds select DemoBackendApis; forceProduction stays
        // false so this exercises the real provider-selection path.
        graph = AppGraph(context)
        runBlocking {
            // DataStore survives across tests in one instrumentation run:
            // reset the shared auth/server state this flow depends on.
            graph.auth.signOut()
            graph.settings.resetBaseUrl()
        }
    }

    @Test
    fun demoFlowNeedsNoServerAndNoteAppearsImmediately() {
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    CaregiverRoot(graph)
                }
            }
        }

        // No token → auth gate with the debug-only demo entry.
        compose.onNodeWithTag("auth_demo_entry").assertIsDisplayed()
        compose.onNodeWithTag("auth_demo_entry").performClick()

        // Demo sign-in lands on the tabbed app with fictional demo data.
        compose.onNodeWithTag("tab_home").assertIsDisplayed()
        runBlocking {
            assertNotNull(graph.auth.token.first())
            assertEquals(SettingsStore.DEFAULT_BASE_URL, graph.settings.baseUrl.first())
        }

        // Recipients: the demo roster loads with no server behind it.
        compose.onNodeWithTag("tab_people").performClick()
        compose.onNodeWithText("ليلى حسن").assertIsDisplayed()
        compose.onNodeWithText("John Carter").assertIsDisplayed()

        // Detail → Add note: the existing note-creation flow.
        compose.onNodeWithText("ليلى حسن").performClick()
        compose.onNodeWithText("Add note").assertIsDisplayed()
        compose.onNodeWithText("Add note").performClick()

        // Valid note: mood + free text, then save.
        val text = "Offline demo E2E note"
        compose.onAllNodesWithText("Good")[0].performClick()
        compose.onNodeWithText("Write any important notes about today…")
            .performTextInput(text)
        compose.onNodeWithText("Save note").performClick()

        // Saved confirmation → detail shows the note immediately.
        compose.onNodeWithText("Saved").assertIsDisplayed()
        compose.onNodeWithText("View note").performClick()
        compose.onNodeWithText(text).assertIsDisplayed()

        // The whole flow never configured or needed a server.
        runBlocking {
            assertEquals(SettingsStore.DEFAULT_BASE_URL, graph.settings.baseUrl.first())
        }
    }
}
