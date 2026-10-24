package com.caregiver.mobile.presentation.recipients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.LoggedOutException
import com.caregiver.mobile.data.api.BackendApis
import com.caregiver.mobile.data.api.CreateRecipientRequest
import com.caregiver.mobile.data.api.RecipientDto
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

/** People list filter: archived rows never mix into the active list. */
enum class PeopleFilter { Active, Archived }

/** A localized-by-the-screen failure from an archive/delete action. */
enum class PeopleActionError { ArchiveFailed, UnarchiveFailed, DeleteFailed }

/** The recipient a delete confirmation is for, plus its authoritative counts. */
data class DeleteCountsState(
    val recipientId: String,
    val counts: RecipientDeletionCounts? = null,
)

sealed interface PeopleState {
    data object Loading : PeopleState

    /**
     * [refreshing] is true while a reload runs on top of already-loaded
     * rows — pull-to-refresh (and error retry) shows the indicator over
     * Content instead of flashing skeletons; Loading is only for the
     * first load.
     */
    data class Content(
        val recipients: List<RecipientDto>,
        val refreshing: Boolean = false,
    ) : PeopleState
    data object Error : PeopleState
}

sealed interface NameError {
    data object Required : NameError
}

data class AddPersonState(
    val name: String = "",
    val nameError: NameError? = null,
    val addedId: String? = null,
    val busy: Boolean = false,
)

/**
 * Recipients list (board 3) plus the add-person form (missing-page spec).
 * [addedId] is set once the server confirms creation; the screen pops the
 * form on it and clears it via [consumeAdded].
 *
 * Archive/delete are local-only and appear only when [admin] is non-null
 * (standalone Room/demo graphs). A server graph keeps the original list and
 * hides the actions instead of faking a successful write.
 */
class RecipientsViewModel(
    private val apis: BackendApis,
    private val repository: AuthRepository,
    scope: CoroutineScope? = null,
    private val admin: RecipientAdmin? = null,
) : ViewModel() {
    private val exec: CoroutineScope? = scope

    private val _state = MutableStateFlow<PeopleState>(PeopleState.Loading)
    val state: StateFlow<PeopleState> = _state.asStateFlow()

    private val _addState = MutableStateFlow(AddPersonState())
    val addState: StateFlow<AddPersonState> = _addState.asStateFlow()

    private val _filter = MutableStateFlow(PeopleFilter.Active)
    val filter: StateFlow<PeopleFilter> = _filter.asStateFlow()

    private val _busyIds = MutableStateFlow<Set<String>>(emptySet())
    val busyIds: StateFlow<Set<String>> = _busyIds.asStateFlow()

    private val _error = MutableStateFlow<PeopleActionError?>(null)
    val error: StateFlow<PeopleActionError?> = _error.asStateFlow()

    /** One archive at a time gets an Undo action; a newer archive replaces it. */
    private val _pendingUndoId = MutableStateFlow<String?>(null)
    val pendingUndoId: StateFlow<String?> = _pendingUndoId.asStateFlow()

    private val _deleteCounts = MutableStateFlow<DeleteCountsState?>(null)
    val deleteCounts: StateFlow<DeleteCountsState?> = _deleteCounts.asStateFlow()

    /** The unfiltered snapshot, so switching filters never refetches. */
    private var allRecipients: List<RecipientDto> = emptyList()

    init {
        launch { refresh() }
    }

    fun refresh() {
        // Keep already-loaded rows composed while reloading — the
        // pull-to-refresh spinner rides on Content (same contract as
        // HistoryViewModel.refresh()); Loading is only for the first load.
        _state.value = when (val current = _state.value) {
            is PeopleState.Content -> current.copy(refreshing = true)
            else -> PeopleState.Loading
        }
        launch { load() }
    }

    fun setFilter(value: PeopleFilter) {
        if (_filter.value == value) return
        _filter.value = value
        val current = _state.value as? PeopleState.Content ?: return
        _state.value = current.copy(recipients = filteredRecipients())
    }

    fun onName(value: String) {
        _addState.value = _addState.value.copy(name = value, nameError = null)
    }

    fun add() {
        val name = _addState.value.name.trim()
        if (name.isEmpty()) {
            _addState.value = _addState.value.copy(nameError = NameError.Required)
            return
        }
        launch {
            _addState.value = _addState.value.copy(busy = true)
            try {
                val created = repository.authorized {
                    apis.recipients().create(CreateRecipientRequest(name))
                }
                _addState.value = _addState.value.copy(busy = false, addedId = created.id)
                load()
            } catch (e: LoggedOutException) {
                _addState.value = _addState.value.copy(busy = false)
            } catch (e: HttpException) {
                _addState.value = _addState.value.copy(busy = false)
                _state.value = PeopleState.Error
            } catch (e: IOException) {
                _addState.value = _addState.value.copy(busy = false, nameError = null)
                _state.value = PeopleState.Error
            } catch (e: CancellationException) {
                _addState.value = _addState.value.copy(busy = false)
                throw e
            }
        }
    }

    fun consumeAdded() {
        _addState.value = _addState.value.copy(addedId = null, name = "")
    }

    fun archive(id: String) {
        val admin = admin ?: return
        runAction(id, PeopleActionError.ArchiveFailed) {
            admin.archive(id)
            _pendingUndoId.value = id
        }
    }

    /** Undo the most recent archive (same operation as unarchive). */
    fun undoArchive(id: String) {
        unarchive(id)
    }

    fun unarchive(id: String) {
        val admin = admin ?: return
        runAction(id, PeopleActionError.UnarchiveFailed) {
            admin.unarchive(id)
            if (_pendingUndoId.value == id) _pendingUndoId.value = null
        }
    }

    /**
     * Load the counts for one delete confirmation. The dialog only opens on
     * the authoritative numbers the same local store will remove.
     */
    fun counts(id: String) {
        val admin = admin ?: return
        _error.value = null
        _deleteCounts.value = DeleteCountsState(id)
        launch {
            try {
                val loaded = admin.counts(id)
                if (_deleteCounts.value?.recipientId == id) {
                    _deleteCounts.value = DeleteCountsState(id, loaded)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (_deleteCounts.value?.recipientId == id) {
                    _deleteCounts.value = null
                }
                _error.value = PeopleActionError.DeleteFailed
            }
        }
    }

    fun clearCounts() {
        _deleteCounts.value = null
    }

    /**
     * Permanently delete an archived recipient. A stale action for an
     * unarchived row is rejected before touching the store.
     */
    fun delete(id: String) {
        val admin = admin ?: return
        val recipient = allRecipients.firstOrNull { it.id == id }
        if (recipient == null || recipient.archivedAt == null) {
            _error.value = PeopleActionError.DeleteFailed
            return
        }
        runAction(id, PeopleActionError.DeleteFailed) {
            admin.deleteArchived(id)
            if (_pendingUndoId.value == id) _pendingUndoId.value = null
            if (_deleteCounts.value?.recipientId == id) _deleteCounts.value = null
        }
    }

    fun consumeUndo(id: String? = null) {
        if (id == null || _pendingUndoId.value == id) {
            _pendingUndoId.value = null
        }
    }

    fun consumeError() {
        _error.value = null
    }

    private fun runAction(
        id: String,
        failure: PeopleActionError,
        block: suspend () -> Unit,
    ) {
        if (id in _busyIds.value) return
        _busyIds.value = _busyIds.value + id
        _error.value = null
        launch {
            try {
                block()
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = failure
            } finally {
                _busyIds.value = _busyIds.value - id
            }
        }
    }

    private fun filteredRecipients(): List<RecipientDto> = when (_filter.value) {
        PeopleFilter.Active -> allRecipients.filter { it.archivedAt == null }
        PeopleFilter.Archived -> allRecipients.filter { it.archivedAt != null }
    }

    private suspend fun load() {
        try {
            val recipients = repository.authorized { apis.recipients().list() }
            allRecipients = recipients
            _state.value = PeopleState.Content(filteredRecipients())
        } catch (e: LoggedOutException) {
            // Root nav flips to login; nothing to show here.
        } catch (e: HttpException) {
            _state.value = PeopleState.Error
        } catch (e: IOException) {
            _state.value = PeopleState.Error
        }
    }

    private fun launch(block: suspend CoroutineScope.() -> Unit) {
        (exec ?: viewModelScope).launch(block = block)
    }
}
