package com.caregiver.mobile.presentation.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.data.summary.SavedSummaryStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SavedSummariesState {
    data object Loading : SavedSummariesState
    data class Content(
        val items: List<SavedSummaryStore.SavedSummaryItem>,
        val refreshing: Boolean = false,
    ) : SavedSummariesState
    data object Error : SavedSummariesState
}

/**
 * The saved-summaries list for one recipient. Reads and deletes go through
 * [SavedSummaryStore] only; a null store (demo graphs) renders an empty
 * list rather than a fake error. A failed delete keeps the current list —
 * the row is only removed after the store confirms it.
 */
class SavedSummariesViewModel(
    private val recipientId: String,
    private val store: SavedSummaryStore?,
    scope: CoroutineScope? = null,
    /** Test seam: replaces [SavedSummaryStore.delete]; production uses the store. */
    private val deleteRow: (suspend (String) -> Unit)? = null,
) : ViewModel() {
    private val exec: CoroutineScope = scope ?: viewModelScope

    private val _state = MutableStateFlow<SavedSummariesState>(SavedSummariesState.Loading)
    val state: StateFlow<SavedSummariesState> = _state.asStateFlow()

    private val _deleteError = MutableStateFlow(false)

    /** True after a delete failed; the screen shows a transient error line. */
    val deleteError: StateFlow<Boolean> = _deleteError.asStateFlow()

    init {
        exec.launch { load() }
    }

    fun refresh() {
        val current = (_state.value as? SavedSummariesState.Content)?.items
        _state.value = if (current != null) {
            SavedSummariesState.Content(current, refreshing = true)
        } else {
            SavedSummariesState.Loading
        }
        exec.launch { load() }
    }

    fun consumeDeleteError() {
        _deleteError.value = false
    }

    fun delete(id: String) {
        if (deleteRow == null && store == null) return
        exec.launch {
            try {
                if (deleteRow != null) deleteRow(id) else store?.delete(id)
                _deleteError.value = false
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _deleteError.value = true
            }
        }
    }

    private suspend fun load() {
        val store = store
        if (store == null) {
            _state.value = SavedSummariesState.Content(emptyList())
            return
        }
        try {
            _state.value = SavedSummariesState.Content(store.list(recipientId))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _state.value = SavedSummariesState.Error
        }
    }
}
