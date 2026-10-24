package com.caregiver.mobile.presentation.recipients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.LoggedOutException
import com.caregiver.mobile.data.api.BackendApis
import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.data.local.RecipientAdmin
import com.caregiver.mobile.data.local.RecipientDeletionCounts
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

enum class DetailAction { AddNote, History, CarePlan, Summary }

data class RecipientDetail(
    val id: String,
    val name: String,
    val active: Boolean,
    val lastNote: NoteDto?,
    val actions: List<DetailAction> = DetailAction.entries,
    val archived: Boolean = false,
)

sealed interface DetailState {
    data object Loading : DetailState
    data class Content(val detail: RecipientDetail) : DetailState
    data object Error : DetailState
}

enum class DetailActionError { ArchiveFailed, UnarchiveFailed, DeleteFailed, DeleteUnavailable }

/** The detail screen's delete dialog: target plus the authoritative counts. */
data class DetailDeleteCounts(
    val recipientId: String,
    val counts: RecipientDeletionCounts? = null,
)

/**
 * Recipient detail (board 4): header data, latest note, and the four design
 * actions. The screen maps actions to destinations; the ViewModel only
 * declares them with recipient context.
 *
 * Archived recipients are read-only: archive, unarchive and permanent delete
 * are local-only and only offered when [admin] is available (standalone Room
 * or demo graphs). Delete is confirmed against the local row counts.
 */
class RecipientDetailViewModel(
    private val recipientId: String,
    private val apis: BackendApis,
    private val repository: AuthRepository,
    scope: CoroutineScope? = null,
    private val admin: RecipientAdmin? = null,
) : ViewModel() {
    // Production uses viewModelScope; tests inject their TestScope.
    private val execScope: CoroutineScope = scope ?: viewModelScope
    private val _state = MutableStateFlow<DetailState>(DetailState.Loading)
    val state: StateFlow<DetailState> = _state.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _error = MutableStateFlow<DetailActionError?>(null)
    val error: StateFlow<DetailActionError?> = _error.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    private val _deleteCounts = MutableStateFlow<DetailDeleteCounts?>(null)
    val deleteCounts: StateFlow<DetailDeleteCounts?> = _deleteCounts.asStateFlow()

    init {
        execScope.launch { load() }
    }

    fun refresh() {
        _state.value = DetailState.Loading
        execScope.launch { load() }
    }

    fun archive() {
        val admin = admin ?: return
        mutate(DetailActionError.ArchiveFailed) {
            val updated = admin.archive(recipientId)
            updateDetail { it.copy(archived = updated.archivedAt != null) }
        }
    }

    fun unarchive() {
        val admin = admin ?: return
        mutate(DetailActionError.UnarchiveFailed) {
            val updated = admin.unarchive(recipientId)
            updateDetail { it.copy(archived = updated.archivedAt != null) }
        }
    }

    fun counts() {
        val admin = admin ?: return
        _error.value = null
        _deleteCounts.value = DetailDeleteCounts(recipientId)
        execScope.launch {
            try {
                val loaded = admin.counts(recipientId)
                if (_deleteCounts.value?.recipientId == recipientId) {
                    _deleteCounts.value = DetailDeleteCounts(recipientId, loaded)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _deleteCounts.value = null
                _error.value = DetailActionError.DeleteFailed
            }
        }
    }

    fun clearCounts() {
        _deleteCounts.value = null
    }

    fun delete() {
        val admin = admin ?: return
        val detail = (_state.value as? DetailState.Content)?.detail
        if (detail == null || !detail.archived) {
            _error.value = DetailActionError.DeleteUnavailable
            return
        }
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        execScope.launch {
            try {
                admin.deleteArchived(recipientId)
                _deleteCounts.value = null
                _deleted.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = DetailActionError.DeleteFailed
            } finally {
                _busy.value = false
            }
        }
    }

    fun consumeDeleted() {
        _deleted.value = false
    }

    fun consumeError() {
        _error.value = null
    }

    private fun mutate(failure: DetailActionError, block: suspend () -> Unit) {
        if (_busy.value) return
        _busy.value = true
        _error.value = null
        execScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = failure
            } finally {
                _busy.value = false
            }
        }
    }

    private fun updateDetail(transform: (RecipientDetail) -> RecipientDetail) {
        val current = _state.value as? DetailState.Content ?: return
        _state.value = DetailState.Content(transform(current.detail))
    }

    private suspend fun load() {
        try {
            val recipients = repository.authorized { apis.recipients().list() }
            val recipient = recipients.firstOrNull { it.id == recipientId }
            if (recipient == null) {
                _state.value = DetailState.Error
                return
            }
            val notes = repository.authorized { apis.notes().history(recipientId, null, null) }
            _state.value = DetailState.Content(
                RecipientDetail(
                    id = recipient.id,
                    name = recipient.name,
                    active = recipient.active,
                    lastNote = notes.maxByOrNull { it.date },
                    archived = recipient.archivedAt != null,
                ),
            )
        } catch (e: LoggedOutException) {
            // Root nav flips to login; nothing to show here.
        } catch (e: HttpException) {
            _state.value = DetailState.Error
        } catch (e: IOException) {
            _state.value = DetailState.Error
        }
    }
}
