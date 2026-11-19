package com.caregiver.mobile.presentation.settings

import android.content.Context
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.ai.AiKeyStore
import com.caregiver.mobile.ai.AiProvider
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.test.WithTestOwner
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DEF-008 regression: a key typed into the API-key dialog must reach the
 * setup check. On cloud-device automation the check resolved to missing_key
 * although the field held the key and the row showed the masked indicator.
 * This pins the real app behavior on an emulator: after entering a key and
 * tapping Check setup, the missing_key recovery copy must NOT appear — the
 * Connection stage must start instead. The probe itself uses a syntactically
 * valid dummy key; its outcome (401/network) is irrelevant to this contract.
 */
@RunWith(AndroidJUnit4::class)
class AiSettingsKeyCommitUiTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun enteredKeyReachesSetupCheck() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val graph = AppGraph(context)
        val store = AiKeyStore(context, graph.activeAccountIdFlow)
        // Pre-grant data-use consent so the check goes straight to the probe.
        runBlocking { store.setConsentGiven(AiProvider.OPEN_ROUTER) }
        val str = { id: Int -> context.getString(id) }

        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    val nav = rememberNavController()
                    NavHost(navController = nav, startDestination = "ai-settings") {
                        composable("ai-settings") { entry ->
                            AiSettingsScreen(graph, nav, entry)
                        }
                    }
                }
            }
        }

        // Select OpenRouter (the configuration from the field report).
        compose.onNodeWithText(str(R.string.settings_ai_provider)).performClick()
        compose.onNodeWithText("OpenRouter").performClick()

        // Enter a syntactically valid key through the real dialog.
        compose.onNodeWithText(str(R.string.settings_ai_api_key)).performClick()
        compose.onNode(hasSetTextAction()).performTextInput(
            "sk-or-v1-def008-regression-0000000000000000000000000000000000000000"
        )
        compose.onNodeWithText(str(R.string.common_ok)).performClick()

        // The row must show the masked-key indicator (apiKey non-blank).
        compose.onNodeWithText("••••••••").assertExists()

        // Run the setup check: the missing_key guard must NOT fire.
        compose.onNodeWithText(str(R.string.settings_ai_check_setup))
            .performScrollTo().performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodesWithText(str(R.string.settings_ai_stage_connection))
                .fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText(str(R.string.ai_recovery_missing_key))
                    .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(str(R.string.ai_recovery_missing_key)).assertDoesNotExist()
        compose.onNodeWithText("••••••••").assertExists()
    }
}
