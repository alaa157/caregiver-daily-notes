package com.caregiver.mobile.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.LoggedOutException
import com.caregiver.mobile.data.api.BackendApis
import com.caregiver.mobile.data.api.NoteDto
import com.caregiver.mobile.data.api.RecipientDto
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class HistoryEntry(val note: NoteDto, val addendumCount: Int)

data class HistoryContent(val entries: List<HistoryEntry>, val recipients: List<RecipientDto>)

sealed interface HistoryState {
    data object Loading : HistoryState
    data class Content(val content: HistoryContent) : HistoryState
    data object Error : HistoryState
}

/**
 * History (board 9): optional recipient + ISO date-range filters mapped to
 * exact query params. Addendum counts need one detail call per note — fine
 * at this scale, and a single failed count degrades to zero, not an error.
 */
class HistoryViewModel(
    private val apis: BackendApis,
    private val repository: AuthRepository,
    scope: CoroutineScope? = null,
    initialRecipientId: String? = null,
    initialFrom: LocalDate? = null,
    initialTo: LocalDate? = null,
) : ViewModel() {
    private val exec: CoroutineScope = scope ?: viewModelScope

    private val _state = MutableStateFlow<HistoryState>(HistoryState.Loading)
    val state: StateFlow<HistoryState> = _state

    private val _recipientId = MutableStateFlow(initialRecipientId)
    val recipientId: StateFlow<String?> = _recipientId

    private val _from = MutableStateFlow(initialFrom)
    val from: StateFlow<LocalDate?> = _from

    private val _to = MutableStateFlow(initialTo)
    val to: StateFlow<LocalDate?> = _to

    init {
        exec.launch { load() }
    }

    fun setRecipient(id: String?) {
        _recipientId.value = id
        refresh()
    }

    fun setFrom(date: LocalDate?) {
        _from.value = date
        refresh()
    }

    fun setTo(date: LocalDate?) {
        _to.value = date
        refresh()
    }

    fun refresh() {
        _state.value = HistoryState.Loading
        exec.launch { load() }
    }

    private suspend fun load() {
        try {
            val recipients = repository.authorized { apis.recipients().list() }
            val notes = repository.authorized {
                apis.notes().history(
                    _recipientId.value,
                    _from.value?.toString(),
                    _to.value?.toString(),
                )
            }
            val entries = notes.map { note ->
                val count = try {
                    repository.authorized { apis.notes().detail(note.id) }.addenda.size
                } catch (e: IOException) {
                    0
                }
                HistoryEntry(note, count)
            }
            _state.value = HistoryState.Content(HistoryContent(entries, recipients))
        } catch (e: LoggedOutException) {
            // Root nav flips to login; nothing to show here.
        } catch (e: IOException) {
            _state.value = HistoryState.Error
        }
    }
}
