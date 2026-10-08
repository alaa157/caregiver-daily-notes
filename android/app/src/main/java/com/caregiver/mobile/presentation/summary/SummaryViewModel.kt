package com.caregiver.mobile.presentation.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.LoggedOutException
import com.caregiver.mobile.data.api.ApiErrors
import com.caregiver.mobile.data.api.BackendApis
import com.caregiver.mobile.data.api.SummaryDto
import com.caregiver.mobile.data.api.SummaryRequest
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface SummaryState {
    data object Loading : SummaryState
    data class Content(val summary: SummaryDto) : SummaryState
    data object AiUnavailable : SummaryState
    data class Rejected(val message: String) : SummaryState
}

/**
 * Summary (boards 10-11): 7/14/30-day periods over stored notes. Any
 * transport failure surfaces AiUnavailable — the notes themselves were
 * never at risk, and the screen says so with a retry.
 */
class SummaryViewModel(
    private val recipientId: String,
    private val apis: BackendApis,
    private val repository: AuthRepository,
    scope: CoroutineScope? = null,
    initialPeriod: Int = DEFAULT_PERIOD_DAYS,
) : ViewModel() {
    private val exec: CoroutineScope = scope ?: viewModelScope

    private val _period = MutableStateFlow(initialPeriod)
    val period: StateFlow<Int> = _period

    private val _state = MutableStateFlow<SummaryState>(SummaryState.Loading)
    val state: StateFlow<SummaryState> = _state

    init {
        exec.launch { load() }
    }

    fun setPeriod(days: Int) {
        if (days != _period.value) {
            _period.value = days
            refresh()
        }
    }

    fun refresh() {
        _state.value = SummaryState.Loading
        exec.launch { load() }
    }

    private suspend fun load() {
        try {
            val summary = repository.authorized {
                apis.summaries().summarize(SummaryRequest(recipientId, _period.value))
            }
            _state.value = SummaryState.Content(summary)
        } catch (e: LoggedOutException) {
            // Root nav flips to login; nothing to show here.
        } catch (e: HttpException) {
            val message = ApiErrors.parse(e)?.message ?: "Request failed (${e.code()})."
            _state.value = SummaryState.Rejected(message)
        } catch (e: IOException) {
            _state.value = SummaryState.AiUnavailable
        }
    }

    companion object {
        const val DEFAULT_PERIOD_DAYS = 7
        val PERIODS = listOf(7, 14, 30)
    }
}
