package com.caregiver.mobile.presentation.settings

import android.content.Context
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.R
import com.caregiver.mobile.ai.AiModelOption
import com.caregiver.mobile.ai.AiProvider
import com.caregiver.mobile.ai.models.ModelSource
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.test.WithTestOwner
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Refresh gate must allow a retry after a failed initial load, when there
 * is no snapshot yet but the screen already knows the load failed.
 */
@RunWith(AndroidJUnit4::class)
class AiModelPickerRetryTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun render(
        source: ModelSource?,
        refreshing: Boolean = false,
        loadError: Boolean = false,
        onRefresh: () -> Unit = {},
    ) {
        val models = listOf(AiModelOption("openai/gpt-6.2-sol"))
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    AiModelPickerContent(
                        models = models,
                        selectedModel = models.first().id,
                        provider = AiProvider.OPEN_ROUTER,
                        source = source,
                        fetchedAtEpochMillis = System.currentTimeMillis(),
                        refreshing = refreshing,
                        loadError = loadError,
                        onSelect = {},
                        onCustomModelChange = {},
                        onRefresh = onRefresh,
                        onDismiss = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun refreshIsEnabledAfterInitialLoadFailure() {
        var refreshes = 0
        render(source = null, loadError = true, onRefresh = { refreshes++ })

        compose.onNodeWithTag("ai_model_refresh").assertIsEnabled()
        compose.onNodeWithTag("ai_model_refresh").performClick()

        assertEquals(1, refreshes)
    }

    @Test
    fun refreshIsDisabledWhileInitialLoadIsInFlight() {
        render(source = null, refreshing = true)

        compose.onNodeWithTag("ai_model_refresh").assertIsDisabled()
    }

    @Test
    fun refreshIsEnabledAfterBundledFallback() {
        render(source = ModelSource.BUNDLED)

        compose.onNodeWithTag("ai_model_refresh").assertIsEnabled()
    }

    @Test
    fun refreshIsEnabledForLiveAndCached() {
        render(source = ModelSource.LIVE)
        compose.onNodeWithTag("ai_model_refresh").assertIsEnabled()

        render(source = ModelSource.CACHED)
        compose.onNodeWithTag("ai_model_refresh").assertIsEnabled()
    }

    @Test
    fun refreshIsDisabledWhileRefreshing() {
        render(source = ModelSource.LIVE, refreshing = true)

        compose.onNodeWithTag("ai_model_refresh").assertIsDisabled()
    }

    @Test
    fun failedStateShowsNoSpinner() {
        render(source = null, loadError = true)

        compose.onNodeWithContentDescription(
            context.getString(R.string.settings_ai_models_loading),
        ).assertDoesNotExist()
    }
}
