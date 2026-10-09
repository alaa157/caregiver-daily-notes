package com.caregiver.mobile.presentation.saved

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.data.demo.DemoNoteRepository
import com.caregiver.mobile.data.demo.DemoRecipientRepository
import com.caregiver.mobile.data.demo.NoteEntity
import com.caregiver.mobile.data.demo.RecipientEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Saved list entry: immutable saved note with its owner name. */
data class SavedEntry(val note: NoteEntity, val recipientName: String)

/** Saved states: loading, empty, error, success. */
sealed interface SavedDemoState {
    data object Loading : SavedDemoState
    data object Error : SavedDemoState
    data class Content(val entries: List<SavedEntry>) : SavedDemoState
}

class SavedDemoViewModel(
    private val recipients: DemoRecipientRepository,
    private val notes: DemoNoteRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<SavedDemoState>(SavedDemoState.Loading)
    val state: StateFlow<SavedDemoState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = SavedDemoState.Loading
            try {
                combine(
                    recipients.observeRecipients(),
                    notes.observeNotes(null, null, null),
                ) { people: List<RecipientEntity>, all: List<NoteEntity> ->
                    all.sortedByDescending { it.date }.map { note ->
                        val name = people.firstOrNull { it.id == note.recipientId }?.name ?: ""
                        SavedEntry(note, name)
                    }
                }.catch { _state.value = SavedDemoState.Error }
                    .collect { _state.value = SavedDemoState.Content(it) }
            } catch (e: Exception) {
                _state.value = SavedDemoState.Error
            }
        }
    }
}
