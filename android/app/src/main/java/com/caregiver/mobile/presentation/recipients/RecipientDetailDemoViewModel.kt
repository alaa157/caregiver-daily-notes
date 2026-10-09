package com.caregiver.mobile.presentation.recipients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.data.demo.DemoNoteDetail
import com.caregiver.mobile.data.demo.DemoNoteRepository
import com.caregiver.mobile.data.demo.DemoRecipientRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/** Recipient-detail states: loading, empty (no recent notes), error, success. */
sealed interface DetailDemoState {
    data object Loading : DetailDemoState
    data object Error : DetailDemoState
    data class Content(
        val id: String,
        val name: String,
        val lastDate: String?,
        val recent: List<DemoNoteDetail>,
    ) : DetailDemoState
}

class RecipientDetailDemoViewModel(
    private val recipientId: String,
    private val recipients: DemoRecipientRepository,
    private val notes: DemoNoteRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<DetailDemoState>(DetailDemoState.Loading)
    val state: StateFlow<DetailDemoState> = _state.asStateFlow()

    init {
        recipients.selectedId.value = recipientId
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = DetailDemoState.Loading
            try {
                notes.observeNotes(recipientId, null, null).catch {
                    _state.value = DetailDemoState.Error
                }.collect { list ->
                    val person = recipients.getRecipient(recipientId)
                    if (person == null) {
                        _state.value = DetailDemoState.Error
                    } else {
                        _state.value = DetailDemoState.Content(
                            id = person.id,
                            name = person.name,
                            lastDate = list.maxByOrNull { it.date }?.date,
                            recent = list.map { DemoNoteDetail(it, emptyList()) },
                        )
                    }
                }
            } catch (e: Exception) {
                _state.value = DetailDemoState.Error
            }
        }
    }
}
