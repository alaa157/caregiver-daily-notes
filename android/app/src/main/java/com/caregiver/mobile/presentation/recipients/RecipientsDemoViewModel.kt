package com.caregiver.mobile.presentation.recipients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.core.time.DateFormats
import com.caregiver.mobile.data.demo.DemoNoteRepository
import com.caregiver.mobile.data.demo.DemoRecipientRepository
import com.caregiver.mobile.data.demo.RecipientEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Recipient row: initials avatar, name, last note date (data, verbatim). */
data class DemoRecipientRow(
    val id: String,
    val name: String,
    val lastNoteDate: String?,
)

/** Care-recipients states: loading, empty, error, success. */
sealed interface DemoRecipientsState {
    data object Loading : DemoRecipientsState
    data object Error : DemoRecipientsState
    data class Content(val rows: List<DemoRecipientRow>) : DemoRecipientsState
}

class RecipientsDemoViewModel(
    private val recipients: DemoRecipientRepository,
    private val notes: DemoNoteRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<DemoRecipientsState>(DemoRecipientsState.Loading)
    val state: StateFlow<DemoRecipientsState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = DemoRecipientsState.Loading
            try {
                combine(
                    recipients.observeRecipients(),
                    notes.observeNotes(),
                ) { people: List<RecipientEntity>, all ->
                    val byRecipient = all.groupBy { it.recipientId }
                    people.map { person ->
                        val last = byRecipient[person.id]?.maxByOrNull { it.date }?.date
                        DemoRecipientRow(person.id, person.name, last)
                    }
                }.catch { _state.value = DemoRecipientsState.Error }
                    .collect { _state.value = DemoRecipientsState.Content(it) }
            } catch (e: Exception) {
                _state.value = DemoRecipientsState.Error
            }
        }
    }
}

/** Add-recipient form state (extra screen, reached from the spec FAB). */
data class DemoAddRecipientState(
    val name: String = "",
    val busy: Boolean = false,
    val failed: Boolean = false,
)

class AddRecipientDemoViewModel(
    private val recipients: DemoRecipientRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(DemoAddRecipientState())
    val state: StateFlow<DemoAddRecipientState> = _state.asStateFlow()

    fun onName(value: String) {
        _state.value = _state.value.copy(name = value, failed = false)
    }

    /** Returns the new id on success, null when the name is blank/fails. */
    suspend fun save(): String? {
        if (_state.value.name.isBlank()) {
            _state.value = _state.value.copy(failed = true)
            return null
        }
        _state.value = _state.value.copy(busy = true, failed = false)
        return try {
            val id = recipients.addRecipient(_state.value.name)
            _state.value = _state.value.copy(busy = false)
            id
        } catch (e: Exception) {
            _state.value = _state.value.copy(busy = false, failed = true)
            null
        }
    }

    companion object {
        /** ISO today for display fallbacks. */
        fun todayIso(): String = LocalDate.now().toString()
    }
}

/** Display helper shared by detail rows (Latin digits both locales). */
fun lastNoteCaption(isoDate: String?, arabic: Boolean): String? =
    isoDate?.let { DateFormats.historyDay(it, arabic) }
