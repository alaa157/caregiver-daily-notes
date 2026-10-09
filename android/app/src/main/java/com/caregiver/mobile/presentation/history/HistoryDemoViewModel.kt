package com.caregiver.mobile.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.data.demo.DemoNoteRepository
import com.caregiver.mobile.data.demo.DemoRecipientRepository
import com.caregiver.mobile.data.demo.NoteEntity
import com.caregiver.mobile.data.demo.RecipientEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** One day group in the History timeline. */
data class DemoHistoryDay(val date: String, val notes: List<DemoHistoryEntry>)

data class DemoHistoryEntry(val note: NoteEntity, val recipientName: String)

/** History states: loading, empty, error, success. */
sealed interface HistoryDemoState {
    data object Loading : HistoryDemoState
    data object Error : HistoryDemoState
    data class Content(
        val recipients: List<RecipientEntity>,
        val selectedId: String?,
        val periodDays: Int,
        val days: List<DemoHistoryDay>,
    ) : HistoryDemoState
}

class HistoryDemoViewModel(
    private val recipients: DemoRecipientRepository,
    private val notes: DemoNoteRepository,
    initialRecipientId: String? = null,
    initialPeriodDays: Int = 30,
) : ViewModel() {
    private val _state = MutableStateFlow<HistoryDemoState>(HistoryDemoState.Loading)
    val state: StateFlow<HistoryDemoState> = _state.asStateFlow()

    private val selectedId = MutableStateFlow<String?>(initialRecipientId)
    private val periodDays = MutableStateFlow(initialPeriodDays)

    init {
        refresh()
    }

    fun setRecipient(id: String?) {
        selectedId.value = id
    }

    fun setAll() {
        selectedId.value = null
    }

    fun setPeriod(days: Int) {
        periodDays.value = days
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = HistoryDemoState.Loading
            try {
                combine(
                    recipients.observeRecipients(),
                    notes.observeNotes(null, null, null),
                    selectedId,
                    periodDays,
                ) { people: List<RecipientEntity>, all: List<NoteEntity>, sel: String?, days: Int ->
                    buildContent(people, all, sel, days)
                }.catch { _state.value = HistoryDemoState.Error }
                    .collect { _state.value = it }
            } catch (e: Exception) {
                _state.value = HistoryDemoState.Error
            }
        }
    }

    private fun buildContent(
        people: List<RecipientEntity>,
        all: List<NoteEntity>,
        sel: String?,
        days: Int,
    ): HistoryDemoState {
        val cutoff = LocalDate.now().minusDays(days.toLong()).toString()
        val filtered = all.filter { note ->
            note.date >= cutoff && (sel == null || note.recipientId == sel)
        }.sortedByDescending { it.date }
        val grouped = filtered.groupBy { it.date }.toSortedMap(compareByDescending { it })
            .map { (date, list) ->
                DemoHistoryDay(
                    date = date,
                    notes = list.map { note ->
                        val name = people.firstOrNull { it.id == note.recipientId }?.name ?: ""
                        DemoHistoryEntry(note, name)
                    },
                )
            }
        return HistoryDemoState.Content(people, sel, days, grouped)
    }
}
