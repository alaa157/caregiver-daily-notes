package com.caregiver.mobile.presentation.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.R
import com.caregiver.mobile.ai.AiModelOption
import com.caregiver.mobile.ai.AiProvider
import com.caregiver.mobile.ai.models.ModelSource
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.test.WithTestOwner
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The "Other model" action is pinned outside the scrolling list. These tests
 * keep that contract independent of the picker's list/search tests.
 */
@RunWith(AndroidJUnit4::class)
class AiModelPickerOtherRowTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun render(
        models: List<AiModelOption>,
        selectedModel: String = models.firstOrNull()?.id.orEmpty(),
        provider: AiProvider = AiProvider.OPEN_ROUTER,
        source: ModelSource? = ModelSource.LIVE,
    ) {
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    var currentSelection by remember { mutableStateOf(selectedModel) }
                    AiModelPickerContent(
                        models = models,
                        selectedModel = currentSelection,
                        provider = provider,
                        source = source,
                        fetchedAtEpochMillis = System.currentTimeMillis(),
                        refreshing = false,
                        loadError = false,
                        onSelect = { id -> currentSelection = id },
                        onCustomModelChange = { value -> currentSelection = value },
                        onRefresh = {},
                        onDismiss = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun fakeModels(count: Int): List<AiModelOption> =
        (0 until count).map { AiModelOption("fake/provider-model-$it") }

    /**
     * The field's label merges into its Text semantics, so assertTextEquals
     * would see label + value; the typed value lives in EditableText alone.
     */
    private fun assertEditableText(tag: String, expected: String) {
        compose.onNodeWithTag(tag).assert(
            SemanticsMatcher("EditableText = [$expected]") {
                it.config.getOrNull(SemanticsProperties.EditableText)?.text == expected
            },
        )
    }

    @Test
    fun otherRowIsVisibleWithoutScrollingWithFourHundredModels() {
        val models = fakeModels(400)
        render(models = models, selectedModel = models.first().id)

        compose.onNodeWithTag("model_option_other").assertIsDisplayed()
    }

    @Test
    fun selectingOtherShowsTheCustomFieldWithoutScrolling() {
        val models = fakeModels(400)
        render(models = models, selectedModel = models.first().id)

        compose.onNodeWithTag("model_option_other").performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        compose.onNodeWithTag("model_option_other").assertIsSelected()
        compose.onNodeWithTag("model_custom_field").assertIsDisplayed()
    }

    @Test
    fun otherRowSurvivesANoMatchSearch() {
        val models = fakeModels(4)
        render(models = models, selectedModel = models.first().id)

        compose.onNodeWithTag("ai_model_search").performTextReplacement("no-such-model")
        compose.waitForIdle()

        compose.onNodeWithText(
            context.getString(R.string.settings_ai_models_empty, "no-such-model"),
        ).assertIsDisplayed()
        compose.onNodeWithTag("model_option_other").assertIsDisplayed()
    }

    @Test
    fun absentModelSelectsOtherAndPrefills() {
        render(models = fakeModels(3), selectedModel = "openai/no-longer-served")

        compose.onNodeWithTag("model_option_other").assertIsSelected()
        assertEditableText("model_custom_field", "openai/no-longer-served")
    }

    @Test
    fun selectingARealRowClearsOther() {
        render(models = fakeModels(4), selectedModel = "openai/no-longer-served")

        compose.onNodeWithTag("model_option_other").assertIsSelected()
        compose.onNodeWithTag("model_custom_field").assertIsDisplayed()

        compose.onNodeWithTag("model_option_fake/provider-model-1")
            .performSemanticsAction(SemanticsActions.OnClick)
        compose.waitForIdle()

        compose.onNodeWithTag("model_option_other").assertIsNotSelected()
        compose.onNodeWithTag("model_custom_field").assertDoesNotExist()
    }

    @Test
    fun listStillScrollsToTheLastModel() {
        val models = fakeModels(400)
        render(models = models, selectedModel = models.first().id)

        // The list is lazy: scroll by index, then the trailing static note
        // is composed and can be scrolled to like any other node.
        compose.onNodeWithTag("ai_model_list").performScrollToIndex(399)
        compose.onNodeWithTag("model_option_fake/provider-model-399")
            .assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.settings_ai_model_paid_note))
            .performScrollTo()
            .assertIsDisplayed()
    }
}
