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
    data class Content(val summary: SummaryDto, val refreshing: Boolean = false) : SummaryState
    data class AiUnavailable(val last: SummaryDto? = null) : SummaryState
    data class Rejected(val code: String?, val last: SummaryDto? = null) : SummaryState
}

/**
 * Summary (boards 10-11): 7/14/30-day periods over stored notes. Known flags
 * survive refresh and failure — clearing happens only when a successful
 * response reports none, or when the session ends upstream. Transport
 * failures surface AiUnavailable (notes-safe copy + retry); HTTP failures
 * surface a localized generic error carrying the code, never raw text.
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
        exec.launch { load(preserve = false) }
    }

    fun setPeriod(days: Int) {
        if (days != _period.value) {
            _period.value = days
            refresh()
        }
    }

    fun refresh() {
        val current = (_state.value as? SummaryState.Content)?.summary
        _state.value = if (current != null) {
            SummaryState.Content(current, refreshing = true)
        } else {
            SummaryState.Loading
        }
        exec.launch { load(preserve = current != null) }
    }

    private suspend fun load(preserve: Boolean) {
        try {
            val summary = repository.authorized {
                apis.summaries().summarize(SummaryRequest(recipientId, _period.value))
            }
            _state.value = SummaryState.Content(summary)
        } catch (e: LoggedOutException) {
            if (!preserve) {
                _state.value = SummaryState.Loading
            } else {
                lastSummary()?.let { _state.value = SummaryState.Content(it) }
            }
        } catch (e: HttpException) {
            _state.value = SummaryState.Rejected(ApiErrors.parse(e)?.code, lastSummary())
        } catch (e: IOException) {
            _state.value = SummaryState.AiUnavailable(lastSummary())
        }
    }

    private fun lastSummary(): SummaryDto? =
        (_state.value as? SummaryState.Content)?.summary

    companion object {
        const val DEFAULT_PERIOD_DAYS = 7
        val PERIODS = listOf(7, 14, 30)
    }
}
