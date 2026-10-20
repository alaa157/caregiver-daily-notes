package com.caregiver.mobile.presentation.notes

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.MainScaffold
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.data.api.UpdateRecipientRequest
import com.caregiver.mobile.test.WithTestOwner
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task 3 round-FAB flow on the offline demo backend: with two active demo
 * people (ليلى حسن r1, John Carter r2 — سميرة علي r3 is inactive) the FAB
 * opens the picker and a row tap reaches the editor; with a single active
 * person (r2 deactivated via the demo update API) it skips the sheet.
 *
 * Instrumentation-only: these need a device or emulator and never run under
 * `testDebugUnitTest`.
 */
@RunWith(AndroidJUnit4::class)
class RecipientPickerUiTest {

    @get:Rule
    val compose = createComposeRule()

    private fun localizedContext(): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale("en"))
        return base.createConfigurationContext(config)
    }

    private fun setApp(graph: AppGraph, localized: Context) {
        compose.setContent {
            CompositionLocalProvider(LocalContext provides localized) {
                WithTestOwner {
                    CaregiverTheme {
                        MainScaffold(graph)
                    }
                }
            }
        }
    }

    @Test
    fun twoActiveRecipientsShowSheetAndPickNavigatesToEditor() {
        val localized = localizedContext()
        setApp(AppGraph(localized), localized)

        compose.onNodeWithTag("fab_add_note").performClick()
        compose.onNodeWithText("Who is this note for?").assertIsDisplayed()
        // Wait for the demo list to land in the sheet, then pick John Carter.
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithTag("picker_recipient_r2").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("picker_recipient_r1").assertIsDisplayed()
        // The inactive demo person (سميرة علي, r3) is filtered out.
        compose.onNodeWithTag("picker_recipient_r3").assertDoesNotExist()

        compose.onNodeWithTag("picker_recipient_r2").performClick()
        // The editor's top bar proves the navigation happened.
        compose.onNodeWithText("Today's note").assertIsDisplayed()
    }

    @Test
    fun singleActiveRecipientSkipsSheetAndOpensEditor() {
        val localized = localizedContext()
        val graph = AppGraph(localized)
        runBlocking {
            graph.apis.recipients().update("r2", UpdateRecipientRequest(active = false))
        }
        setApp(graph, localized)

        // Prove the one-active list is loaded (People tab shows ليلى حسن),
        // so the FAB tap below resolves against settled data, not skeletons.
        compose.onNodeWithTag("tab_people").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText("ليلى حسن").fetchSemanticsNodes().isNotEmpty()
        }
        // The People tab's FAB adds a person; the note FAB lives on the other
        // tabs, so go back to Home before tapping it.
        compose.onNodeWithTag("tab_home").performClick()

        compose.onNodeWithTag("fab_add_note").performClick()
        compose.onNodeWithText("Today's note").assertIsDisplayed()
        compose.onNodeWithText("Who is this note for?").assertDoesNotExist()
    }
}
