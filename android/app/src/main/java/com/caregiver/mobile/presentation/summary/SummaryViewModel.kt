package com.caregiver.mobile.presentation.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.LoggedOutException
import com.caregiver.mobile.data.api.ApiErrors
import com.caregiver.mobile.data.api.BackendApis
import com.caregiver.mobile.data.api.SummaryDto
import com.caregiver.mobile.data.api.SummaryRequest
import com.caregiver.mobile.data.api.SuggestPlanRequest
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface SummaryState {
    data object Loading : SummaryState
    data class Content(val summary: SummaryDto, val refreshing: Boolean = false, val aiGenerated: Boolean = false) : SummaryState
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
    private val aiKeyStore: com.caregiver.mobile.ai.AiKeyStore? = null,
    private val llmClientFactory: com.caregiver.mobile.ai.LlmClientFactory = com.caregiver.mobile.ai.LlmClientFactory(),
) : ViewModel() {
    private val exec: CoroutineScope = scope ?: viewModelScope

    private val _period = MutableStateFlow(initialPeriod)
    val period: StateFlow<Int> = _period

    private val _state = MutableStateFlow<SummaryState>(SummaryState.Loading)
    val state: StateFlow<SummaryState> = _state

    private val _suggesting = MutableStateFlow(false)
    val suggesting: StateFlow<Boolean> = _suggesting

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
        val store = aiKeyStore
        if (store != null && store.hasKey()) {
            try {
                val provider = store.provider() ?: com.caregiver.mobile.ai.AiProvider.OPENAI
                val key = store.apiKey().orEmpty()
                val model = store.model() ?: provider.defaultModel
                val client = when (provider) {
                    com.caregiver.mobile.ai.AiProvider.OPENAI,
                    com.caregiver.mobile.ai.AiProvider.XAI,
                    com.caregiver.mobile.ai.AiProvider.OPEN_ROUTER ->
                        llmClientFactory.createClient(provider.defaultBaseUrl, model, key)
                    com.caregiver.mobile.ai.AiProvider.ANTHROPIC ->
                        llmClientFactory.createAnthropicClient(provider.defaultBaseUrl, model, key)
                    com.caregiver.mobile.ai.AiProvider.GEMINI ->
                        llmClientFactory.createGeminiClient(provider.defaultBaseUrl, model, key)
                }
                val service = com.caregiver.mobile.ai.SummaryAiService(client, store)
                val notes = repository.authorized { apis.notes().history(recipientId, null, null) }
                val signals = repository.authorized { apis.summaries().signals(recipientId, null, null) }
                val summary = service.summarize(recipientId, _period.value, notes, signals)
                _state.value = SummaryState.Content(summary, aiGenerated = true)
                return
            } catch (e: Exception) {
                // Fall through to API path on failure.
            }
        }
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

    fun suggestPlan() {
        val store = aiKeyStore ?: return
        if (!store.hasKey()) return
        val summaryText = (_state.value as? SummaryState.Content)?.summary?.text.orEmpty()
        _suggesting.value = true
        exec.launch {
            try {
                val provider = store.provider() ?: com.caregiver.mobile.ai.AiProvider.OPENAI
                val key = store.apiKey().orEmpty()
                val model = store.model() ?: provider.defaultModel
                val client = when (provider) {
                    com.caregiver.mobile.ai.AiProvider.OPENAI,
                    com.caregiver.mobile.ai.AiProvider.XAI,
                    com.caregiver.mobile.ai.AiProvider.OPEN_ROUTER ->
                        llmClientFactory.createClient(provider.defaultBaseUrl, model, key)
                    com.caregiver.mobile.ai.AiProvider.ANTHROPIC ->
                        llmClientFactory.createAnthropicClient(provider.defaultBaseUrl, model, key)
                    com.caregiver.mobile.ai.AiProvider.GEMINI ->
                        llmClientFactory.createGeminiClient(provider.defaultBaseUrl, model, key)
                }
                val service = com.caregiver.mobile.ai.PlanSuggestionService(client, store)
                val items = service.suggestItems(summaryText)
                if (items.isNotEmpty()) {
                    repository.authorized {
                        apis.plans().suggest(SuggestPlanRequest(recipientId, items))
                    }
                }
            } catch (e: Exception) {
                // best-effort
            } finally {
                _suggesting.value = false
            }
        }
    }

    private fun lastSummary(): SummaryDto? =
        (_state.value as? SummaryState.Content)?.summary

    companion object {
        const val DEFAULT_PERIOD_DAYS = 7
        val PERIODS = listOf(7, 14, 30)
    }
}
