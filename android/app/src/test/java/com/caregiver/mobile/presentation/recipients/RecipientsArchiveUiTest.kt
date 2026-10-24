package com.caregiver.mobile.presentation.recipients

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.R
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.data.api.RecipientDto
import com.caregiver.mobile.data.local.RecipientDeletionCounts
import com.caregiver.mobile.presentation.common.selectableRecipients
import com.caregiver.mobile.test.WithTestOwner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * People list archive/delete contract. Runs on the JVM under Robolectric so
 * the presentation rules execute in CI without a device: archived rows leave
 * the Active list, return through Unarchive/Undo, are deletable only from the
 * Archived filter, and expose no actions without a local admin.
 */
@RunWith(AndroidJUnit4::class)
class RecipientsArchiveUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val aisha = RecipientDto("r1", "Aisha", true)
    private val archivedAisha = RecipientDto("r1", "Aisha", true, 1_000L)

    /** A tiny state holder mirroring what RecipientsViewModel exposes. */
    private class Holder(
        initial: List<RecipientDto>,
        private val deletionCounts: RecipientDeletionCounts = RecipientDeletionCounts(1, 2, 3, 4, 5, 6),
    ) {
        var all = initial
            private set
        var filter by mutableStateOf(PeopleFilter.Active)
        var state by mutableStateOf<PeopleState>(PeopleState.Content(filtered()))
        var busyIds by mutableStateOf(emptySet<String>())
        var pendingUndoId by mutableStateOf<String?>(null)
        var error by mutableStateOf<PeopleActionError?>(null)
        var deleteCounts by mutableStateOf<DeleteCountsState?>(null)

        fun selectFilter(value: PeopleFilter) {
            filter = value
            sync()
        }

        fun archive(id: String) {
            all = all.map {
                if (it.id == id) it.copy(archivedAt = it.archivedAt ?: 1_000L) else it
            }
            pendingUndoId = id
            sync()
        }

        fun unarchive(id: String) {
            all = all.map { if (it.id == id) it.copy(archivedAt = null) else it }
            if (pendingUndoId == id) pendingUndoId = null
            sync()
        }

        fun requestDelete(id: String) {
            deleteCounts = DeleteCountsState(id, deletionCounts)
        }

        fun confirmDelete(id: String) {
            all = all.filterNot { it.id == id }
            deleteCounts = null
            sync()
        }

        fun cancelDelete() {
            deleteCounts = null
        }

        fun consumeUndo(id: String?) {
            if (id == null || pendingUndoId == id) pendingUndoId = null
        }

        fun consumeError() {
            error = null
        }

        private fun filtered(): List<RecipientDto> = when (filter) {
            PeopleFilter.Active -> all.filter { it.archivedAt == null }
            PeopleFilter.Archived -> all.filter { it.archivedAt != null }
        }

        private fun sync() {
            state = PeopleState.Content(filtered())
        }
    }

    private fun render(holder: Holder, hasAdmin: Boolean = true) {
        compose.setContent {
            WithTestOwner {
                CaregiverTheme {
                    RecipientsContent(
                        state = holder.state,
                        filter = holder.filter,
                        busyIds = holder.busyIds,
                        pendingUndoId = holder.pendingUndoId,
                        error = holder.error,
                        deleteCounts = holder.deleteCounts,
                        hasAdmin = hasAdmin,
                        onRefresh = {},
                        onFilter = holder::selectFilter,
                        onOpen = {},
                        onAdd = {},
                        onArchive = holder::archive,
                        onUnarchive = holder::unarchive,
                        onRequestDelete = holder::requestDelete,
                        onConfirmDelete = holder::confirmDelete,
                        onCancelDelete = holder::cancelDelete,
                        onUndo = holder::unarchive,
                        onConsumeUndo = holder::consumeUndo,
                        onErrorShown = holder::consumeError,
                    )
                }
            }
        }
    }

    private fun openMenu(id: String) {
        compose.onNodeWithTag("people_menu_$id").performClick()
    }

    @Test
    fun archiveFromActiveListMovesRowToArchived() {
        val holder = Holder(listOf(aisha))
        render(holder)
        compose.onNodeWithText(Bidi.isolate("Aisha")).assertIsDisplayed()

        openMenu("r1")
        compose.onNodeWithTag("people_archive_r1").performClick()
        compose.waitForIdle()

        compose.onNodeWithText(Bidi.isolate("Aisha")).assertDoesNotExist()
        assertNotNull(holder.all.single().archivedAt)

        compose.onNodeWithTag("people_filter_archived").performClick()
        compose.onNodeWithText(Bidi.isolate("Aisha")).assertIsDisplayed()
        openMenu("r1")
        compose.onNodeWithTag("people_unarchive_r1").assertIsDisplayed()
    }

    @Test
    fun undoArchiveRestoresRow() {
        val holder = Holder(listOf(aisha))
        render(holder)

        openMenu("r1")
        compose.onNodeWithTag("people_archive_r1").performClick()
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(context.getString(R.string.people_archive_undo))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(context.getString(R.string.people_archive_undo)).performClick()
        compose.waitForIdle()

        compose.onNodeWithText(Bidi.isolate("Aisha")).assertIsDisplayed()
        assertEquals(null, holder.all.single().archivedAt)
    }

    @Test
    fun archivedFilterShowsArchivedRecipient() {
        val holder = Holder(listOf(archivedAisha))
        render(holder)
        compose.onNodeWithText(context.getString(R.string.people_empty)).assertIsDisplayed()

        compose.onNodeWithTag("people_filter_archived").performClick()
        compose.onNodeWithText(Bidi.isolate("Aisha")).assertIsDisplayed()
        openMenu("r1")
        compose.onNodeWithTag("people_delete_r1").assertIsDisplayed()
        compose.onNodeWithTag("people_unarchive_r1").assertIsDisplayed()
    }

    @Test
    fun deleteFromArchivedListRemovesRow() {
        val holder = Holder(listOf(archivedAisha))
        holder.selectFilter(PeopleFilter.Archived)
        render(holder)

        openMenu("r1")
        compose.onNodeWithTag("people_delete_r1").performClick()
        compose.onNodeWithTag("people_delete_confirm").performClick()
        compose.waitForIdle()

        compose.onNodeWithText(Bidi.isolate("Aisha")).assertDoesNotExist()
        assertTrue(holder.all.isEmpty())
        compose.onNodeWithText(context.getString(R.string.people_empty_archived)).assertIsDisplayed()
    }

    @Test
    fun deleteConfirmationShowsCountsAndBackupCaveat() {
        val counts = RecipientDeletionCounts(2, 1, 3, 4, 5, 6)
        val holder = Holder(listOf(archivedAisha), counts)
        holder.selectFilter(PeopleFilter.Archived)
        render(holder)

        openMenu("r1")
        compose.onNodeWithTag("people_delete_r1").performClick()

        compose.onNodeWithText(
            context.getString(R.string.people_delete_title, Bidi.isolate("Aisha")),
        ).assertIsDisplayed()
        compose.onNodeWithText("2 notes", substring = true).assertIsDisplayed()
        compose.onNodeWithText("6 pending sync items", substring = true).assertIsDisplayed()
        compose.onNodeWithText(
            context.getString(R.string.people_delete_backup_caveat),
        ).assertIsDisplayed()
    }

    @Test
    fun archivedRowHasNoAddNoteAction() {
        val holder = Holder(listOf(archivedAisha))
        holder.selectFilter(PeopleFilter.Archived)
        render(holder)

        compose.onNodeWithText(context.getString(R.string.detail_add_note)).assertDoesNotExist()
    }

    @Test
    fun archiveActionsHiddenWithoutLocalAdmin() {
        val holder = Holder(listOf(aisha))
        render(holder, hasAdmin = false)

        compose.onNodeWithText(Bidi.isolate("Aisha")).assertIsDisplayed()
        compose.onNodeWithTag("people_menu_r1").assertDoesNotExist()
        compose.onNodeWithTag("people_archive_r1").assertDoesNotExist()
    }

    @Test
    fun archivedRecipientIsNotSelectableForNewNote() {
        // The picker guard is a shared pure filter; this assertion documents
        // that the People screen is not the enforcement point.
        val selectable = selectableRecipients(listOf(aisha, archivedAisha))
        assertEquals(listOf("r1"), selectable.map { it.id })
        assertTrue(selectable.single().archivedAt == null)
    }
}
