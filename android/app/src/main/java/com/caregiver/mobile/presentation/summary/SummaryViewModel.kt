package com.caregiver.mobile.presentation.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.ai.SummaryWindow
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.LoggedOutException
import com.caregiver.mobile.data.api.ApiErrors
import com.caregiver.mobile.data.api.BackendApis
import com.caregiver.mobile.data.api.SummaryDto
import com.caregiver.mobile.data.api.SummaryRequest
import com.caregiver.mobile.data.api.SuggestPlanRequest
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface SummaryState {
    data object Loading : SummaryState
    data object NeedsConsent : SummaryState
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

    private val _suggestedForCurrent = MutableStateFlow(false)
    val suggestedForCurrent: StateFlow<Boolean> = _suggestedForCurrent

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
        _suggestedForCurrent.value = false
        _suggestStatus.value = null
        _state.value = if (current != null) {
            SummaryState.Content(current, refreshing = true)
        } else {
            SummaryState.Loading
        }
        exec.launch { load(preserve = current != null) }
    }

    fun grantConsent() {
        aiKeyStore?.setConsentGiven()
        _state.value = SummaryState.Loading
        exec.launch { load(preserve = false) }
    }

    private suspend fun load(preserve: Boolean) {
        val store = aiKeyStore
        if (store != null && store.hasKey()) {
            if (!store.consentGiven()) {
                _state.value = SummaryState.NeedsConsent
                return
            }
            try {
                val provider = store.provider() ?: com.caregiver.mobile.ai.AiProvider.OPENAI
                val key = store.apiKey().orEmpty()
                val model = store.model() ?: provider.defaultModel
                val baseUrl = store.baseUrlOrDefault(provider)
                val client = when (provider) {
                    com.caregiver.mobile.ai.AiProvider.OPENAI,
                    com.caregiver.mobile.ai.AiProvider.XAI,
                    com.caregiver.mobile.ai.AiProvider.OPEN_ROUTER ->
                        llmClientFactory.createClient(baseUrl, model, key)
                    com.caregiver.mobile.ai.AiProvider.ANTHROPIC ->
                        llmClientFactory.createAnthropicClient(baseUrl, model, key)
                    com.caregiver.mobile.ai.AiProvider.GEMINI ->
                        llmClientFactory.createGeminiClient(baseUrl, model, key)
                }
                val service = com.caregiver.mobile.ai.SummaryAiService(client, store)
                // Explicit inclusive window (Finding 3): today plus the
                // preceding periodDays - 1 days, the same rule the backend
                // applies. Both fetches are narrowed to it so notes and
                // signals outside the selected period never reach the AI
                // provider, and the same window is passed to the service.
                val today = LocalDate.now()
                val from = SummaryWindow.start(today, _period.value).toString()
                val to = today.toString()
                val notes = repository.authorized { apis.notes().history(recipientId, from, to) }
                val signals = repository.authorized { apis.summaries().signals(recipientId, from, to) }
                when (val result = service.summarize(recipientId, _period.value, notes, signals, today)) {
                    is com.caregiver.mobile.ai.SummaryAiResult.Success -> {
                        _state.value = SummaryState.Content(result.summary, aiGenerated = true)
                        _suggestedForCurrent.value = false
                        _suggestStatus.value = null
                        return
                    }
                    is com.caregiver.mobile.ai.SummaryAiResult.Failure -> {
                        _state.value = SummaryState.AiUnavailable(lastSummary())
                        return
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = SummaryState.AiUnavailable(lastSummary())
                return
            }
        }
        try {
            val summary = repository.authorized {
                apis.summaries().summarize(SummaryRequest(recipientId, _period.value))
            }
            _state.value = SummaryState.Content(summary)
            _suggestedForCurrent.value = false
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

    private val _suggestStatus = MutableStateFlow<String?>(null)
    val suggestStatus: StateFlow<String?> = _suggestStatus

    fun suggestPlan() {
        val store = aiKeyStore ?: return
        if (!store.hasKey()) return
        if (!store.consentGiven()) return
        if (_suggesting.value || _suggestedForCurrent.value) return
        val summaryText = (_state.value as? SummaryState.Content)?.summary?.text.orEmpty()
        _suggesting.value = true
        _suggestStatus.value = null
        exec.launch {
            try {
                val provider = store.provider() ?: com.caregiver.mobile.ai.AiProvider.OPENAI
                val key = store.apiKey().orEmpty()
                val model = store.model() ?: provider.defaultModel
                val baseUrl = store.baseUrlOrDefault(provider)
                val client = when (provider) {
                    com.caregiver.mobile.ai.AiProvider.OPENAI,
                    com.caregiver.mobile.ai.AiProvider.XAI,
                    com.caregiver.mobile.ai.AiProvider.OPEN_ROUTER ->
                        llmClientFactory.createClient(baseUrl, model, key)
                    com.caregiver.mobile.ai.AiProvider.ANTHROPIC ->
                        llmClientFactory.createAnthropicClient(baseUrl, model, key)
                    com.caregiver.mobile.ai.AiProvider.GEMINI ->
                        llmClientFactory.createGeminiClient(baseUrl, model, key)
                }
                val summaryIds = (_state.value as? SummaryState.Content)?.summary?.evidence?.map { it.noteId }?.toSet().orEmpty()
                val noteIds = if (summaryIds.isNotEmpty()) {
                    summaryIds
                } else {
                    val today = java.time.LocalDate.now()
                    val from = com.caregiver.mobile.ai.SummaryWindow.start(today, _period.value).toString()
                    val to = today.toString()
                    repository.authorized { apis.notes().history(recipientId, from, to) }.map { it.id }.toSet()
                }
                val service = com.caregiver.mobile.ai.PlanSuggestionService(client, store)
                val result = service.suggest(summaryText, noteIds)
                when (result) {
                    is com.caregiver.mobile.ai.PlanSuggestionResult.Success -> {
                        val items = result.suggestion.changes.mapNotNull { change ->
                            val reason = change.reason.trim()
                            if (reason.isBlank()) return@mapNotNull null
                            buildString {
                                append("[").append(change.actionId).append("] ")
                                append(reason)
                                if (change.evidenceNoteIds.isNotEmpty()) append(" (evidence: ").append(change.evidenceNoteIds.joinToString(",")).append(")")
                                if (change.params.isNotEmpty()) append(" params=").append(change.params.entries.joinToString(",") { "${it.key}=${it.value}" })
                            }
                        }
                        if (items.isEmpty()) {
                            _suggestStatus.value = "empty_after_filter"
                            return@launch
                        }
                        try {
                            repository.authorized {
                                apis.plans().suggest(SuggestPlanRequest(recipientId, items))
                            }
                            _suggestStatus.value = "success"
                            _suggestedForCurrent.value = true
                        } catch (e: Exception) {
                            _suggestStatus.value = "error"
                        }
                    }
                    is com.caregiver.mobile.ai.PlanSuggestionResult.Failure -> {
                        _suggestStatus.value = result.reasonCode
                    }
                }
            } catch (e: Exception) {
                _suggestStatus.value = "error"
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
