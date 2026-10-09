package com.caregiver.mobile.presentation.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.data.demo.DemoNoteRepository
import com.caregiver.mobile.data.demo.DemoSummary
import com.caregiver.mobile.data.demo.DemoSummaryStub
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Summary states (screens.md screen 8): idle, generating (Skeleton, button
 * label changes), unavailable (notes still accessible), success; errors use
 * the shared error state.
 */
sealed interface SummaryDemoState {
    data object Idle : SummaryDemoState
    data object Generating : SummaryDemoState
    data object Unavailable : SummaryDemoState
    data object Error : SummaryDemoState
    data class Content(val summary: DemoSummary) : SummaryDemoState
}

class SummaryDemoViewModel(
    private val recipientId: String,
    private val notes: DemoNoteRepository,
    private val stub: DemoSummaryStub,
    initialPeriodDays: Int = 14,
) : ViewModel() {
    private val _period = MutableStateFlow(initialPeriodDays)
    val period: StateFlow<Int> = _period.asStateFlow()

    private val _state = MutableStateFlow<SummaryDemoState>(SummaryDemoState.Idle)
    val state: StateFlow<SummaryDemoState> = _state.asStateFlow()

    val arabic: Boolean get() = Locale.getDefault().language == "ar"

    fun setPeriod(days: Int) {
        _period.value = days
        if (_state.value !is SummaryDemoState.Idle) {
            _state.value = SummaryDemoState.Idle
        }
    }

    fun generate() {
        if (_state.value is SummaryDemoState.Generating) return
        viewModelScope.launch {
            _state.value = SummaryDemoState.Generating
            try {
                // Exhibit the generating state; the stub itself is instant.
                delay(700)
                val arabicNow = arabic
                val today = java.time.LocalDate.now()
                val from = today.minusDays(_period.value.toLong()).toString()
                val inScope = notes.observeNotes(recipientId, from, today.toString()).first()
                val summary = stub.summarize(inScope, _period.value, arabicNow)
                _state.value = if (summary.evidence.isNotEmpty()) {
                    SummaryDemoState.Content(summary)
                } else {
                    // Nothing to summarize: AI unavailable path, notes stay
                    // reachable via History (the screen keeps its controls).
                    SummaryDemoState.Unavailable
                }
            } catch (e: Exception) {
                _state.value = SummaryDemoState.Error
            }
        }
    }

    fun refresh() {
        _state.value = SummaryDemoState.Idle
    }
}
