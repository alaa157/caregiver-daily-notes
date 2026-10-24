package com.caregiver.mobile.presentation.recipients

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.R
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.data.local.RecipientDeletionCounts
import com.caregiver.mobile.test.WithTestOwner
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Recipient detail read-only contract for archived people. Robolectric keeps
 * these assertions in the normal unit-test run while still exercising the
 * real Compose screen content.
 */
@RunWith(AndroidJUnit4::class)
class RecipientDetailArchiveUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val activeDetail = RecipientDetail(
        id = "r1",
        name = "Aisha",
        active = true,
        lastNote = null,
        archived = false,
    )

    private val archivedDetail = activeDetail.copy(archived = true)

    private fun render(
        detail: RecipientDetail,
        counts: RecipientDeletionCounts? = null,
        hasAdmin: Boolean = true,
        onConfirmDelete: () -> Unit = {},
    ) {
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    DetailContent(
                        detail = detail,
                        busy = false,
                        error = null,
                        hasAdmin = hasAdmin,
                        deleteCounts = counts,
                        onBack = {},
                        onAddNote = {},
                        onHistory = {},
                        onSummary = {},
                        onPlan = {},
                        onArchive = {},
                        onUnarchive = {},
                        onRequestDelete = {},
                        onConfirmDelete = onConfirmDelete,
                        onCancelDelete = {},
                    )
                }
            }
        }
    }

    @Test
    fun archivedDetailHidesAddNote() {
        render(archivedDetail)

        compose.onNodeWithText(context.getString(R.string.detail_add_note)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.people_archived_readonly))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun archivedDetailHidesPlanAction() {
        render(archivedDetail)

        compose.onNodeWithText(context.getString(R.string.detail_plan)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.detail_history))
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.detail_summary))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun archivedDetailShowsUnarchiveAndDelete() {
        render(archivedDetail)

        compose.onNodeWithTag("detail_unarchive").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("detail_delete").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun archivedDetailDeleteConfirmationShowsCaveat() {
        render(
            archivedDetail,
            counts = RecipientDeletionCounts(2, 1, 3, 4, 5, 6),
        )

        compose.onNodeWithTag("detail_delete").performScrollTo().performClick()

        compose.onNodeWithText(
            context.getString(R.string.people_delete_title, Bidi.isolate("Aisha")),
        ).assertIsDisplayed()
        compose.onNodeWithText("2 notes", substring = true).assertIsDisplayed()
        compose.onNodeWithText(
            context.getString(R.string.people_delete_backup_caveat),
        ).assertIsDisplayed()
    }

    @Test
    fun archivedDetailDeleteNavigatesBack() {
        val deleted = AtomicBoolean(false)
        render(
            archivedDetail,
            counts = RecipientDeletionCounts(1, 0, 0, 0, 0, 0),
            onConfirmDelete = { deleted.set(true) },
        )

        compose.onNodeWithTag("detail_delete").performScrollTo().performClick()
        compose.onNodeWithTag("people_delete_confirm").performClick()

        compose.waitForIdle()
        assertTrue(deleted.get())
    }

    @Test
    fun unarchivedDetailShowsArchive() {
        render(activeDetail)

        compose.onNodeWithText(context.getString(R.string.detail_add_note)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.detail_plan))
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithTag("detail_archive").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("detail_unarchive").assertDoesNotExist()
        compose.onNodeWithTag("detail_delete").assertDoesNotExist()
    }

    @Test
    fun archiveActionsHiddenWhenNoAdmin() {
        render(archivedDetail, hasAdmin = false)

        compose.onNodeWithTag("detail_unarchive").assertDoesNotExist()
        compose.onNodeWithTag("detail_delete").assertDoesNotExist()
        compose.onNodeWithTag("detail_archive").assertDoesNotExist()
    }

    @Test
    fun archivedDetailStillShowsLatestNote() {
        val note = NoteDto(
            id = "n1",
            recipientId = "r1",
            date = "2026-10-07",
            mood = "good",
            appetite = "good",
            sleep = "good",
            mobility = "walks",
            medicationTaken = "taken",
            pain = 2,
            fall = false,
            text = "Steady day.",
        )
        render(archivedDetail.copy(lastNote = note))

        compose.onNodeWithText(context.getString(R.string.person_last_note))
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.people_archived))
            .performScrollTo()
            .assertIsDisplayed()
    }
}
