package com.caregiver.mobile.presentation.recipients

import android.content.Context
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.R
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.presentation.auth.AuthPrimaryButtonTestTag
import com.caregiver.mobile.test.WithTestOwner
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Recipient Detail main action and shortcut grid (#908). Robolectric keeps
 * these in the normal unit-test run.
 */
@RunWith(AndroidJUnit4::class)
class RecipientDetailActionsUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val detail = RecipientDetail(
        id = "r1",
        name = "Omar عمر",
        active = true,
        lastNote = null,
        todayNoteId = null,
    )

    private fun render(
        detail: RecipientDetail = this.detail,
        onAddNote: () -> Unit = {},
        onHistory: () -> Unit = {},
        onPlan: () -> Unit = {},
        onSummary: () -> Unit = {},
        onCareDetails: () -> Unit = {},
    ) {
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    DetailContent(
                        detail = detail,
                        busy = false,
                        error = null,
                        hasAdmin = false,
                        deleteCounts = null,
                        onBack = {},
                        onEdit = {},
                        onAddNote = onAddNote,
                        onHistory = onHistory,
                        onSummary = onSummary,
                        onPlan = onPlan,
                        onCareDetails = onCareDetails,
                        onArchive = {},
                        onUnarchive = {},
                        onRequestDelete = {},
                        onConfirmDelete = {},
                        onCancelDelete = {},
                    )
                }
            }
        }
    }

    @Test
    fun writeButtonOpensEditorForThisPersonWhenNoNoteToday() {
        var adds = 0
        render(onAddNote = { adds++ })

        compose.onNodeWithText(context.getString(R.string.detail_write_today_note))
            .assertIsDisplayed()
        compose.onNodeWithTag(AuthPrimaryButtonTestTag).performClick()

        assertEquals(1, adds)
    }

    @Test
    fun writeButtonBecomesEditWhenNoteExistsToday() {
        render(detail.copy(todayNoteId = "n1"))

        compose.onNodeWithText(context.getString(R.string.detail_edit_today_note))
            .assertIsDisplayed()
    }

    @Test
    fun writeButtonIs56dpAndAbsentForArchivedPeople() {
        render()
        val density = context.resources.displayMetrics.density
        val height = compose.onNodeWithTag(AuthPrimaryButtonTestTag)
            .fetchSemanticsNode().boundsInRoot.height
        assertTrue("main action height $height is below 56dp", height >= 56f * density - 0.5f)

        render(detail.copy(archived = true))
        compose.onNodeWithTag(AuthPrimaryButtonTestTag).assertDoesNotExist()
    }

    @Test
    fun shortcutGridOpensEachScreenForThisPerson() {
        var history = 0
        var plan = 0
        var summary = 0
        var care = 0
        render(
            onHistory = { history++ },
            onPlan = { plan++ },
            onSummary = { summary++ },
            onCareDetails = { care++ },
        )

        compose.onNodeWithText(context.getString(R.string.detail_history)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.detail_plan)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.detail_summary)).assertIsDisplayed()
        // "Care details" also names the section below, so assert the grid
        // card by its tag rather than by ambiguous text.

        compose.onNodeWithTag("detail_shortcut_history").performClick()
        compose.onNodeWithTag("detail_shortcut_plan").performClick()
        compose.onNodeWithTag("detail_shortcut_summary").performClick()
        compose.onNodeWithTag("detail_shortcut_care_details").performClick()

        assertEquals(1, history)
        assertEquals(1, plan)
        assertEquals(1, summary)
        assertEquals(1, care)
    }

    @Test
    fun shortcutGridIsTwoByTwoWith48dpTargets() {
        render()
        val density = context.resources.displayMetrics.density
        val tags = listOf(
            "detail_shortcut_history",
            "detail_shortcut_plan",
            "detail_shortcut_summary",
            "detail_shortcut_care_details",
        )
        val bounds = tags.map {
            compose.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot
        }
        bounds.forEach {
            assertTrue("shortcut height ${it.height} is below 48dp", it.height >= 48f * density - 0.5f)
        }
        // First row shares a top edge; second row is below it and shares its
        // own top edge — a 2x2 grid, not a single column or a 4-wide row.
        assertTrue(abs(bounds[0].top - bounds[1].top) < 1f)
        assertTrue(abs(bounds[2].top - bounds[3].top) < 1f)
        assertTrue(bounds[2].top > bounds[0].top)
    }
}
