package com.caregiver.mobile.presentation.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.data.demo.DemoNoteDetail
import com.caregiver.mobile.data.demo.DemoNoteRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** Note-detail states: loading, empty (no data), error, success. */
sealed interface NoteDetailDemoState {
    data object Loading : NoteDetailDemoState
    data object Error : NoteDetailDemoState
    data object Empty : NoteDetailDemoState
    data class Content(val detail: DemoNoteDetail) : NoteDetailDemoState
}

class NoteDetailDemoViewModel(
    private val noteId: String,
    private val notes: DemoNoteRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<NoteDetailDemoState>(NoteDetailDemoState.Loading)
    val state: StateFlow<NoteDetailDemoState> = _state.asStateFlow()

    private val _addendumText = MutableStateFlow("")
    val addendumText: StateFlow<String> = _addendumText.asStateFlow()

    private val _addendumBusy = MutableStateFlow(false)
    val addendumBusy: StateFlow<Boolean> = _addendumBusy.asStateFlow()

    private val _addendumFailed = MutableStateFlow(false)
    val addendumFailed: StateFlow<Boolean> = _addendumFailed.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = NoteDetailDemoState.Loading
            try {
                notes.observeNoteDetail(noteId).catch {
                    _state.value = NoteDetailDemoState.Error
                }.collect { detail ->
                    _state.value = if (detail == null) NoteDetailDemoState.Empty
                    else NoteDetailDemoState.Content(detail)
                }
            } catch (e: Exception) {
                _state.value = NoteDetailDemoState.Error
            }
        }
    }

    fun onAddendumText(value: String) {
        _addendumText.value = value
        _addendumFailed.value = false
    }

    /**
     * Corrections append as new entries; the original is never altered.
     * Returns true when appended (caller navigates back).
     */
    fun submitAddendum(onDone: () -> Unit) {
        if (_addendumBusy.value) return
        if (_addendumText.value.isBlank()) {
            _addendumFailed.value = true
            return
        }
        viewModelScope.launch {
            _addendumBusy.value = true
            _addendumFailed.value = false
            try {
                notes.addAddendum(noteId, _addendumText.value)
                _addendumText.value = ""
                _addendumBusy.value = false
                onDone()
            } catch (e: Exception) {
                _addendumBusy.value = false
                _addendumFailed.value = true
            }
        }
    }
}
