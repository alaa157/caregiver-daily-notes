package com.caregiver.mobile.presentation.settings

import android.content.Context
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.R
import com.caregiver.mobile.ai.AiModelCatalog
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
 * The picker is deliberately repository-free so it can be exercised with
 * fake lists on the JVM. `onSelect` stands in for the screen's commit path;
 * nothing here needs an AppGraph, an API key, or the network.
 */
@RunWith(AndroidJUnit4::class)
class AiModelPickerTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun render(
        models: List<AiModelOption> = AiModelCatalog.options(AiProvider.OPEN_ROUTER),
        selectedModel: String = models.firstOrNull()?.id.orEmpty(),
        provider: AiProvider = AiProvider.OPEN_ROUTER,
        source: ModelSource? = ModelSource.LIVE,
        fetchedAtEpochMillis: Long? = System.currentTimeMillis(),
        refreshing: Boolean = false,
        loadError: Boolean = false,
        onSelect: (String) -> Unit = {},
        onCustomModelChange: (String) -> Unit = {},
        onRefresh: () -> Unit = {},
        onDismiss: () -> Unit = {},
    ) {
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    AiModelPickerContent(
                        models = models,
                        selectedModel = selectedModel,
                        provider = provider,
                        source = source,
                        fetchedAtEpochMillis = fetchedAtEpochMillis,
                        refreshing = refreshing,
                        loadError = loadError,
                        onSelect = onSelect,
                        onCustomModelChange = onCustomModelChange,
                        onRefresh = onRefresh,
                        onDismiss = onDismiss,
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun fakeModels(count: Int): List<AiModelOption> =
        (0 until count).map { AiModelOption("fake/provider-model-$it") }

    @Test
    fun fourHundredModelsScrollToTheLastRow() {
        val models = fakeModels(400)
        render(models = models, selectedModel = models.first().id)

        compose.onNodeWithTag("ai_model_count")
            .assertTextEquals(context.getString(R.string.settings_ai_models_count, 400))
        compose.onNodeWithTag("model_option_fake/provider-model-399")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun searchFiltersByIdAndClearRestores() {
        val models = listOf(
            AiModelOption("anthropic/claude-sonnet-5"),
            AiModelOption("openai/gpt-6.2-sol"),
        ) + fakeModels(8)
        render(models = models, selectedModel = models.first().id)

        compose.onNodeWithTag("ai_model_search").performTextReplacement("claude")
        compose.waitForIdle()
        compose.onNodeWithTag("model_option_openai/gpt-6.2-sol").assertDoesNotExist()
        compose.onNodeWithTag("model_option_anthropic/claude-sonnet-5").assertIsDisplayed()
        compose.onNodeWithTag("ai_model_count").assertTextEquals(
            context.getString(R.string.settings_ai_models_count_filtered, 1, models.size),
        )

        // The trailing clear icon restores the full list.
        compose.onNodeWithTag("ai_model_search_clear").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()
        compose.onNodeWithTag("model_option_openai/gpt-6.2-sol").assertIsDisplayed()
        compose.onNodeWithTag("ai_model_count").assertTextEquals(
            context.getString(R.string.settings_ai_models_count, models.size),
        )
    }

    @Test
    fun noMatchesShowsEmptyStateAndClearAction() {
        val models = fakeModels(4)
        render(models = models, selectedModel = models.first().id)

        compose.onNodeWithTag("ai_model_search").performTextReplacement("no-such-model")
        compose.waitForIdle()
        compose.onNodeWithText(
            context.getString(R.string.settings_ai_models_empty, "no-such-model"),
        ).assertIsDisplayed()
        // "Other model" stays reachable even with an empty list.
        compose.onNodeWithTag("model_option_other").assertIsDisplayed()

        compose.onNodeWithText(context.getString(R.string.settings_ai_models_clear_search))
            .performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("ai_model_search").assertTextEquals("")
        compose.onNodeWithTag("model_option_fake/provider-model-0").assertIsDisplayed()
    }

    @Test
    fun selectingARowCallsOnSelect() {
        val models = fakeModels(3)
        val selected = mutableListOf<String>()
        render(models = models, selectedModel = models.first().id, onSelect = { selected.add(it) })

        compose.onNodeWithTag("model_option_fake/provider-model-2")
            .performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf("fake/provider-model-2"), selected)
    }

    @Test
    fun selectedLiveModelIsNotMisclassifiedAsOther() {
        val models = listOf(AiModelOption("openai/live-only-model"))
        render(models = models, selectedModel = "openai/live-only-model")

        compose.onNodeWithTag("model_option_openai/live-only-model").assertIsSelected()
        compose.onNodeWithTag("model_option_other").assertIsNotSelected()
        compose.onNodeWithTag("model_custom_field").assertDoesNotExist()
    }

    @Test
    fun modelAbsentFromListSelectsOtherAndPrefills() {
        render(models = fakeModels(3), selectedModel = "openai/no-longer-served")

        compose.onNodeWithTag("model_option_other").assertIsSelected()
        compose.onNodeWithTag("model_custom_field")
            .assertIsDisplayed()
            .assertTextEquals("openai/no-longer-served")
    }

    @Test
    fun cachedSourceShowsCachedStatus() {
        render(
            models = fakeModels(2),
            selectedModel = "fake/provider-model-0",
            source = ModelSource.CACHED,
            fetchedAtEpochMillis = System.currentTimeMillis(),
        )

        compose.onNodeWithTag("model_list_status").assertTextEquals(
            context.getString(R.string.settings_ai_models_cached, "0 m"),
        )
        compose.onNodeWithText(context.getString(R.string.settings_ai_models_bundled)).assertDoesNotExist()
    }

    @Test
    fun liveAndBundledSourcesShowTheirOwnStatus() {
        render(
            models = fakeModels(2),
            selectedModel = "fake/provider-model-0",
            source = ModelSource.LIVE,
            fetchedAtEpochMillis = System.currentTimeMillis(),
        )
        compose.onNodeWithTag("model_list_status").assertTextEquals(
            context.getString(
                R.string.settings_ai_models_live,
                AiProvider.OPEN_ROUTER.displayName,
                "0 m",
            ),
        )

        render(
            models = fakeModels(2),
            selectedModel = "fake/provider-model-0",
            source = ModelSource.BUNDLED,
        )
        compose.onNodeWithTag("model_list_status").assertTextEquals(
            context.getString(R.string.settings_ai_models_bundled),
        )
        compose.onNodeWithText(context.getString(R.string.settings_ai_models_bundled_privacy))
            .assertIsDisplayed()
    }

    @Test
    fun otherModelValidationStillWorks() {
        render(models = fakeModels(2), selectedModel = "bad id!")

        compose.onNodeWithTag("model_custom_field").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.settings_ai_invalid_model)).assertIsDisplayed()
    }
}
