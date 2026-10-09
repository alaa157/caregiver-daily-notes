package com.caregiver.mobile.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.data.demo.DemoNoteRepository
import com.caregiver.mobile.data.demo.DemoRecipientRepository
import com.caregiver.mobile.data.demo.DemoSafetyRule
import com.caregiver.mobile.data.demo.NoteEntity
import com.caregiver.mobile.data.demo.RecipientEntity
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Recent note with its recipient for the Home timeline. */
data class HomeRecentNote(val note: NoteEntity, val recipientName: String)

/** Home content (screens.md screen 2). */
data class DemoHomeContent(
    val doneToday: Int,
    val totalRecipients: Int,
    val recent: List<HomeRecentNote>,
    /** Recipient ids with a recorded fall in the window (demo rule). */
    val fallRecipients: List<String>,
    /** Editor/summary target: selected, else first missing today, else first. */
    val targetRecipientId: String?,
)

/** Home states: loading, empty (no recipients), error, success. */
sealed interface DemoHomeState {
    data object Loading : DemoHomeState
    data object Error : DemoHomeState
    data class Content(val content: DemoHomeContent, val empty: Boolean) : DemoHomeState
}

class HomeDemoViewModel(
    private val recipients: DemoRecipientRepository,
    private val notes: DemoNoteRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<DemoHomeState>(DemoHomeState.Loading)
    val state: StateFlow<DemoHomeState> = _state.asStateFlow()
    private val today: String = LocalDate.now().toString()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = DemoHomeState.Loading
            try {
                combine(
                    recipients.observeRecipients(),
                    notes.observeNotes(),
                ) { people: List<RecipientEntity>, all: List<NoteEntity> ->
                    buildContent(people, all)
                }.catch { _state.value = DemoHomeState.Error }
                    .collect { _state.value = it }
            } catch (e: Exception) {
                _state.value = DemoHomeState.Error
            }
        }
    }

    private fun buildContent(
        people: List<RecipientEntity>,
        all: List<NoteEntity>,
    ): DemoHomeState {
        if (people.isEmpty()) {
            return DemoHomeState.Content(
                DemoHomeContent(0, 0, emptyList(), emptyList(), null),
                empty = true,
            )
        }
        val byRecipient = all.groupBy { it.recipientId }
        val doneToday = people.count { person ->
            byRecipient[person.id]?.any { it.date == today } == true
        }
        val recent = all.sortedByDescending { it.date }.take(5).map { note ->
            val name = people.firstOrNull { it.id == note.recipientId }?.name ?: ""
            HomeRecentNote(note, name)
        }
        val window = todayMinus(14)
        val fallRecipients = people.filter { person ->
            (byRecipient[person.id] ?: emptyList())
                .any { it.date >= window && DemoSafetyRule.hasFallFlag(listOf(it)) }
        }.map { it.name }
        val selected = recipients.selectedId.value?.takeIf { id -> people.any { it.id == id } }
        val target = selected
            ?: people.firstOrNull { person ->
                byRecipient[person.id]?.none { it.date == today } == true
            }?.id
            ?: people.first().id
        return DemoHomeState.Content(
            DemoHomeContent(doneToday, people.size, recent, fallRecipients, target),
            empty = false,
        )
    }

    private fun todayMinus(days: Long): String = LocalDate.now().minusDays(days).toString()
}
