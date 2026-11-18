package com.caregiver.mobile.presentation.settings

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.test.WithTestOwner
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Task 8 settings flows: server-URL validation/save through the existing
 * settings screen, language-chip selection reflecting the store, and
 * sign-out clearing the session. Language switching itself recreates the
 * activity (untestable headless), so chip clicks are not exercised here —
 * persistence across language changes is pinned at the store layer in
 * `SettingsStoreTest`. Instrumentation-only.
 */
@RunWith(AndroidJUnit4::class)
class SettingsFlowUiTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var graph: AppGraph

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        graph = AppGraph(context)
        resetState()
    }

    @After
    fun tearDown() {
        resetState()
    }

    private fun resetState() = runBlocking {
        graph.auth.signOut()
        graph.settings.resetBaseUrl()
        graph.settings.setLanguage(SettingsStore.DEFAULT_LANGUAGE)
    }

    @Test
    fun serverUrlSavePersistsAddress() {
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    ServerUrlScreen(graph, rememberNavController())
                }
            }
        }

        compose.onNode(hasSetTextAction()).performTextInput("https://care.example:8443")
        compose.onNodeWithText("Save").performClick()

        compose.onNodeWithText("Address saved.").assertIsDisplayed()
        assertEquals(
            "https://care.example:8443",
            runBlocking { graph.settings.baseUrl.first() },
        )
    }

    @Test
    fun invalidUrlShowsInlineErrorAndKeepsAddress() {
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    ServerUrlScreen(graph, rememberNavController())
                }
            }
        }

        compose.onNode(hasSetTextAction()).performTextInput("not a url")
        compose.onNodeWithText("Save").performClick()

        compose.onNodeWithText("Invalid address. Example: https://example.com")
            .assertIsDisplayed()
        assertEquals(
            SettingsStore.DEFAULT_BASE_URL,
            runBlocking { graph.settings.baseUrl.first() },
        )
    }

    @Test
    fun signOutClearsSession() {
        runBlocking {
            graph.settings.setToken("tok")
            graph.settings.setEmail("a@b.c")
        }
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    SettingsScreen(graph, rememberNavController())
                }
            }
        }

        compose.onNodeWithText("Sign out").performClick()

        compose.waitForIdle()
        assertNull(runBlocking { graph.settings.token.first() })
    }

    @Test
    fun languageSelectionReflectsStore() {
        runBlocking { graph.settings.setLanguage("en") }
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    SettingsScreen(graph, rememberNavController())
                }
            }
        }

        compose.onNodeWithText("English").assertIsSelected()
        compose.onNodeWithText("العربية").assertIsNotSelected()
    }
}
