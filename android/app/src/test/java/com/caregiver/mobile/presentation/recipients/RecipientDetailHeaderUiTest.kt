package com.caregiver.mobile.presentation.recipients

import android.content.Context
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.R
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.presentation.common.AppDimens
import com.caregiver.mobile.presentation.common.PersonAvatarSize
import com.caregiver.mobile.presentation.common.PersonAvatarTestTag
import com.caregiver.mobile.presentation.common.StatusChipTestTag
import com.caregiver.mobile.test.WithTestOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The redesigned Recipient Detail header (#899): round 48-dp buttons at the
 * start/end, a 72-dp avatar, the name as the single heading, and today's
 * status chip. Robolectric keeps these in the normal unit-test run.
 */
@RunWith(AndroidJUnit4::class)
class RecipientDetailHeaderUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val detail = RecipientDetail(
        id = "r1",
        name = "Omar عمر",
        active = true,
        lastNote = null,
        todayNoteId = "n1",
    )

    private fun render(
        detail: RecipientDetail = this.detail,
        direction: LayoutDirection = LayoutDirection.Ltr,
        onBack: () -> Unit = {},
        onEdit: () -> Unit = {},
    ) {
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        DetailContent(
                            detail = detail,
                            busy = false,
                            error = null,
                            hasAdmin = false,
                            deleteCounts = null,
                            onBack = onBack,
                            onEdit = onEdit,
                            onAddNote = {},
                            onHistory = {},
                            onSummary = {},
                            onPlan = {},
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
    }

    @Test
    fun headerHasRoundButtonsAvatarHeadingAndDoneChip() {
        render()

        compose.onNodeWithTag("detail_header").assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.action_back))
            .assertIsDisplayed()
        compose.onNodeWithContentDescription(context.getString(R.string.detail_edit_person))
            .assertIsDisplayed()
        compose.onNodeWithTag(PersonAvatarTestTag).assertIsDisplayed()
        compose.onNodeWithTag(StatusChipTestTag).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.redesign_chip_done)).assertIsDisplayed()
        compose.onNodeWithText(Bidi.isolate(detail.name))
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Heading, Unit))
    }

    @Test
    fun headerShowsNotYetChipWithoutTodaysNote() {
        render(detail.copy(todayNoteId = null))

        compose.onNodeWithText(context.getString(R.string.redesign_chip_not_yet)).assertIsDisplayed()
    }

    @Test
    fun headerButtonsCallBackAndEdit() {
        var backs = 0
        var edits = 0
        render(onBack = { backs++ }, onEdit = { edits++ })

        compose.onNodeWithTag("detail_header_back").performClick()
        compose.onNodeWithTag("detail_header_edit").performClick()

        assertEquals(1, backs)
        assertEquals(1, edits)
    }

    @Test
    fun headerBackIsAtStartInBothDirections() {
        render(direction = LayoutDirection.Ltr)
        val ltrBack = compose.onNodeWithTag("detail_header_back").getBoundsInRoot().left
        val ltrEdit = compose.onNodeWithTag("detail_header_edit").getBoundsInRoot().left
        assertTrue("LTR: back must be at the start (left) of edit", ltrBack < ltrEdit)

        render(direction = LayoutDirection.Rtl)
        val rtlBack = compose.onNodeWithTag("detail_header_back").getBoundsInRoot().left
        val rtlEdit = compose.onNodeWithTag("detail_header_edit").getBoundsInRoot().left
        assertTrue("RTL: back must be at the start (right) of edit", rtlBack > rtlEdit)
    }

    @Test
    fun headerAvatarAndButtonsUsePrototypeSizes() {
        assertEquals(72.dp, PersonAvatarSize.Large.dp)
        assertEquals(48.dp, AppDimens.BackSize)
    }
}
