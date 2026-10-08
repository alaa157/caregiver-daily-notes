package com.caregiver.mobile.presentation.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.LoggedOutException
import com.caregiver.mobile.data.api.AddendumDto
import com.caregiver.mobile.data.api.BackendApis
import com.caregiver.mobile.data.api.CreateAddendumRequest
import com.caregiver.mobile.data.api.NoteDto
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class NoteContent(val note: NoteDto, val addenda: List<AddendumDto>)

sealed interface NoteDetailState {
    data object Loading : NoteDetailState
    data class Content(val content: NoteContent) : NoteDetailState
    data object Error : NoteDetailState
}

data class AddendumState(
    val text: String = "",
    val addendumError: Boolean = false,
    val busy: Boolean = false,
    val sendFailed: Boolean = false,
    val appended: Boolean = false,
)

/**
 * Note detail (board 7) + addendum form (board 8). The original note has no
 * edit path anywhere — corrections only append. A failed append keeps the
 * typed text so nothing is lost.
 */
class NoteDetailViewModel(
    private val noteId: String,
    private val apis: BackendApis,
    private val repository: AuthRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val exec: CoroutineScope = scope ?: viewModelScope

    private val _state = MutableStateFlow<NoteDetailState>(NoteDetailState.Loading)
    val state: StateFlow<NoteDetailState> = _state

    private val _addendum = MutableStateFlow(AddendumState())
    val addendum: StateFlow<AddendumState> = _addendum

    init {
        exec.launch { load() }
    }

    fun refresh() {
        _state.value = NoteDetailState.Loading
        exec.launch { load() }
    }

    fun onAddendumText(value: String) {
        _addendum.value = _addendum.value.copy(
            text = value,
            addendumError = false,
            sendFailed = false,
        )
    }

    fun submitAddendum() {
        if (_addendum.value.text.isBlank()) {
            _addendum.value = _addendum.value.copy(addendumError = true)
            return
        }
        val text = _addendum.value.text
        _addendum.value = _addendum.value.copy(busy = true, sendFailed = false)
        exec.launch {
            try {
                repository.authorized {
                    apis.notes().append(noteId, CreateAddendumRequest(text))
                }
                _addendum.value = AddendumState(appended = true)
                load()
            } catch (e: LoggedOutException) {
                _addendum.value = _addendum.value.copy(busy = false)
            } catch (e: HttpException) {
                // Text is kept for retry; the form shows localized copy.
                _addendum.value = _addendum.value.copy(busy = false, sendFailed = true)
            } catch (e: IOException) {
                _addendum.value = _addendum.value.copy(busy = false, sendFailed = true)
            } catch (e: CancellationException) {
                _addendum.value = _addendum.value.copy(busy = false)
                throw e
            }
        }
    }

    private suspend fun load() {
        try {
            val detail = repository.authorized { apis.notes().detail(noteId) }
            _state.value = NoteDetailState.Content(NoteContent(detail.toNote(), detail.addenda))
        } catch (e: LoggedOutException) {
            // Root nav flips to login; nothing to show here.
        } catch (e: HttpException) {
            _state.value = NoteDetailState.Error
        } catch (e: IOException) {
            _state.value = NoteDetailState.Error
        }
    }

    private fun com.caregiver.mobile.data.api.NoteDetailDto.toNote() = NoteDto(
        id = id, recipientId = recipientId, date = date, mood = mood,
        appetite = appetite, sleep = sleep, mobility = mobility,
        medicationTaken = medicationTaken, pain = pain, fall = fall, text = text,
    )
}
