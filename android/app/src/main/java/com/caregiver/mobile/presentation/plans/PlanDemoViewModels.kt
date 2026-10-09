package com.caregiver.mobile.presentation.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.data.demo.DemoPlanItem
import com.caregiver.mobile.data.demo.DemoPlanStub
import com.caregiver.mobile.data.demo.DemoPlanVersion
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Plan-proposal states: loading, empty, error, success. */
sealed interface PlanProposalDemoState {
    data object Loading : PlanProposalDemoState
    data object Error : PlanProposalDemoState
    data class Content(val items: List<DemoPlanItem>, val status: String) : PlanProposalDemoState
}

class PlanProposalDemoViewModel(private val stub: DemoPlanStub) : ViewModel() {
    private val _state = MutableStateFlow<PlanProposalDemoState>(PlanProposalDemoState.Loading)
    val state: StateFlow<PlanProposalDemoState> = _state.asStateFlow()

    val arabic: Boolean get() = Locale.getDefault().language == "ar"

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = PlanProposalDemoState.Loading
            try {
                val items = stub.proposal(arabic)
                val status = stub.versions(arabic).firstOrNull()?.status ?: "Suggested"
                _state.value = PlanProposalDemoState.Content(items, status)
            } catch (e: Exception) {
                _state.value = PlanProposalDemoState.Error
            }
        }
    }

    fun accept() {
        viewModelScope.launch {
            try {
                stub.accept()
                refresh()
            } catch (e: Exception) {
                _state.value = PlanProposalDemoState.Error
            }
        }
    }

    fun dismiss() {
        viewModelScope.launch {
            try {
                stub.dismiss()
                refresh()
            } catch (e: Exception) {
                _state.value = PlanProposalDemoState.Error
            }
        }
    }
}

/** Plan-history states: loading, empty, error, success. */
sealed interface PlanHistoryDemoState {
    data object Loading : PlanHistoryDemoState
    data object Error : PlanHistoryDemoState
    data class Content(val versions: List<DemoPlanVersion>) : PlanHistoryDemoState
}

class PlanHistoryDemoViewModel(private val stub: DemoPlanStub) : ViewModel() {
    private val _state = MutableStateFlow<PlanHistoryDemoState>(PlanHistoryDemoState.Loading)
    val state: StateFlow<PlanHistoryDemoState> = _state.asStateFlow()

    val arabic: Boolean get() = Locale.getDefault().language == "ar"

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = PlanHistoryDemoState.Loading
            try {
                _state.value = PlanHistoryDemoState.Content(stub.versions(arabic))
            } catch (e: Exception) {
                _state.value = PlanHistoryDemoState.Error
            }
        }
    }
}
