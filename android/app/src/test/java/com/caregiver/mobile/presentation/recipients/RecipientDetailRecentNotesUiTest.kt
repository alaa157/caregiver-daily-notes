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
import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.test.WithTestOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Recent notes section on Recipient Detail (#909). Not executed by the agent. */
@RunWith(AndroidJUnit4::class)
class RecipientDetailRecentNotesUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val detail = RecipientDetail(
        id = "r1",
        name = "Omar",
        active = true,
        lastNote = null,
    )

    private fun note(id: String, date: String, text: String) = NoteDto(
        id = id,
        recipientId = "r1",
        date = date,
        mood = "good",
        appetite = "good",
        sleep = "good",
        mobility = "walks",
        medicationTaken = "taken",
        pain = 2,
        fall = false,
        text = text,
    )

    private fun render(
        notes: List<NoteDto> = emptyList(),
        onOpenNote: (String) -> Unit = {},
        onSeeAll: () -> Unit = {},
    ) {
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    DetailContent(
                        detail = detail.copy(recentNotes = notes),
                        busy = false,
                        error = null,
                        hasAdmin = false,
                        deleteCounts = null,
                        onBack = {},
                        onEdit = {},
                        onAddNote = {},
                        onHistory = onSeeAll,
                        onSummary = {},
                        onPlan = {},
                        onArchive = {},
                        onUnarchive = {},
                        onRequestDelete = {},
                        onConfirmDelete = {},
                        onCancelDelete = {},
                        onOpenNote = onOpenNote,
                    )
                }
            }
        }
    }

    @Test
    fun showsOnlyThreeNewestNotesWithTheirPreviews() {
        render(
            listOf(
                note("n1", "2026-10-08", "First preview"),
                note("n2", "2026-10-07", "Second preview"),
                note("n3", "2026-10-06", "Third preview"),
                note("n4", "2026-10-05", "Oldest preview"),
            ),
        )

        compose.onNodeWithText(context.getString(R.string.detail_recent_notes)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.detail_see_all)).assertIsDisplayed()
        listOf("n1", "n2", "n3").forEach {
            compose.onNodeWithTag("detail_recent_note_$it").assertIsDisplayed()
        }
        compose.onNodeWithTag("detail_recent_note_n4").assertDoesNotExist()
        compose.onNodeWithText("First preview").assertIsDisplayed()
        compose.onNodeWithText("Second preview").assertIsDisplayed()
        compose.onNodeWithText("Third preview").assertIsDisplayed()
    }

    @Test
    fun tappingCardOpensThatNoteAndSeeAllOpensHistory() {
        var opened: String? = null
        var seeAll = 0
        render(
            listOf(note("n1", "2026-10-08", "First preview")),
            onOpenNote = { opened = it },
            onSeeAll = { seeAll++ },
        )

        compose.onNodeWithTag("detail_recent_note_n1").performClick()
        compose.onNodeWithText(context.getString(R.string.detail_see_all)).performClick()

        assertEquals("n1", opened)
        assertEquals(1, seeAll)
    }

    @Test
    fun emptyStatePointsToTheMainAction() {
        render()

        compose.onNodeWithText(context.getString(R.string.detail_recent_empty_title))
            .assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.detail_recent_empty_body))
            .assertIsDisplayed()
        compose.onNodeWithTag("detail_recent_note_n1").assertDoesNotExist()
    }

    @Test
    fun longPreviewCannotBreakTheCard() {
        val longText = "A".repeat(400)
        render(listOf(note("n1", "2026-10-08", longText)))

        val bounds = compose.onNodeWithTag("detail_recent_note_n1")
            .fetchSemanticsNode().boundsInRoot
        val density = context.resources.displayMetrics.density
        assertTrue("card height ${bounds.height} grew with a 400-char preview", bounds.height < 160f * density)
        assertTrue("card height ${bounds.height} is below 48dp", bounds.height >= 48f * density - 0.5f)
    }
}
