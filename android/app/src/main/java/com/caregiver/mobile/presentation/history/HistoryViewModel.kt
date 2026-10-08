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
import retrofit2.HttpException

data class HistoryEntry(val note: NoteDto)

data class HistoryContent(val entries: List<HistoryEntry>, val recipients: List<RecipientDto>)

sealed interface HistoryState {
    data object Loading : HistoryState
    data class Content(val content: HistoryContent) : HistoryState
    data object Error : HistoryState
}

/**
 * History (board 9): optional recipient + ISO date-range filters mapped to
 * exact query params. Exactly two bounded calls per load (recipients +
 * history) — correction counts are intentionally omitted because the
 * history response carries none and per-note detail calls would be
 * unbounded fan-out (see docs/api/android-gaps.md). Unfiltered loads rely
 * on the server's full-history response; narrow it with the filters.
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
            val entries = notes.map { HistoryEntry(it) }
            _state.value = HistoryState.Content(HistoryContent(entries, recipients))
        } catch (e: LoggedOutException) {
            // Root nav flips to login; nothing to show here.
        } catch (e: HttpException) {
            _state.value = HistoryState.Error
        } catch (e: IOException) {
            _state.value = HistoryState.Error
        }
    }
}
