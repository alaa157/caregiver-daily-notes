package com.caregiver.mobile.presentation.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.ai.AiKeyStore
import com.caregiver.mobile.ai.AiProvider
import com.caregiver.mobile.ai.LlmClientFactory
import com.caregiver.mobile.ai.PlanSuggestionResult
import com.caregiver.mobile.ai.PlanSuggestionService
import com.caregiver.mobile.ai.SafetySignalEvaluator
import com.caregiver.mobile.ai.SummaryAiResult
import com.caregiver.mobile.ai.SummaryAiService
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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface SummaryState {
    data object Loading : SummaryState
    data object NeedsConsent : SummaryState
    data class Content(val summary: SummaryDto, val refreshing: Boolean = false, val aiGenerated: Boolean = false) : SummaryState
    data class AiUnavailable(
        val last: SummaryDto? = null,
        val redFlags: List<String> = emptyList(),
        /** Provider/service code; the UI turns it into recovery copy. */
        val reasonCode: String? = null,
    ) : SummaryState
    data class Rejected(val code: String?, val last: SummaryDto? = null) : SummaryState
}

/**
 * Summary (boards 10-11): 7/14/30-day periods over stored notes. Known flags
 * survive refresh and failure — clearing happens only when a successful
 * response reports none, or when the session ends upstream. Transport
 * failures surface AiUnavailable and HTTP failures surface Rejected; both
 * carry a reason code so the UI can show localized recovery copy, never the
 * raw code.
 */
class SummaryViewModel(
    private val recipientId: String,
    private val apis: BackendApis,
    private val repository: AuthRepository,
    scope: CoroutineScope? = null,
    initialPeriod: Int = DEFAULT_PERIOD_DAYS,
    private val aiKeyStore: AiKeyStore? = null,
    private val llmClientFactory: LlmClientFactory = LlmClientFactory(),
    /** UI language for AI-generated summary text ("ar"/"en"); defaults to English. */
    private val language: String = "en",
) : ViewModel() {
    private val exec: CoroutineScope = scope ?: viewModelScope

    private val _period = MutableStateFlow(initialPeriod)
    val period: StateFlow<Int> = _period.asStateFlow()

    private val _state = MutableStateFlow<SummaryState>(SummaryState.Loading)
    val state: StateFlow<SummaryState> = _state.asStateFlow()

    private val _suggesting = MutableStateFlow(false)
    val suggesting: StateFlow<Boolean> = _suggesting.asStateFlow()

    private val _suggestedForCurrent = MutableStateFlow(false)
    val suggestedForCurrent: StateFlow<Boolean> = _suggestedForCurrent.asStateFlow()

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
        var aiRedFlags: List<String> = emptyList()
        if (store != null && store.hasKey()) {
            if (!store.consentGiven()) {
                _state.value = SummaryState.NeedsConsent
                return
            }
            try {
                val provider = store.provider() ?: AiProvider.OPENAI
                val key = store.apiKey().orEmpty()
                val model = store.model() ?: provider.defaultModel
                val baseUrl = store.baseUrlOrDefault(provider)
                val client = when (provider) {
                    AiProvider.OPENAI,
                    AiProvider.XAI,
                    AiProvider.OPEN_ROUTER ->
                        llmClientFactory.createClient(baseUrl, model, key)
                    AiProvider.ANTHROPIC ->
                        llmClientFactory.createAnthropicClient(baseUrl, model, key)
                    AiProvider.GEMINI ->
                        llmClientFactory.createGeminiClient(baseUrl, model, key)
                }
                val service = SummaryAiService(client, store, language = language)
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
                // Compute deterministic safety flags up front so they survive even
                // when the AI provider throws instead of returning a fallback.
                // Scope by recipient here too: this path can surface flags in
                // the AiUnavailable state without the service's own filter.
                val windowedSignals = signals.filter {
                    it.recipientId == recipientId &&
                        SummaryWindow.contains(it.date, today, _period.value)
                }
                aiRedFlags = SafetySignalEvaluator.evaluate(windowedSignals)
                when (val result = service.summarize(recipientId, _period.value, notes, signals, today)) {
                    is SummaryAiResult.Success -> {
                        _state.value = SummaryState.Content(result.summary, aiGenerated = true)
                        _suggestedForCurrent.value = false
                        _suggestStatus.value = null
                        return
                    }
                    is SummaryAiResult.Failure -> {
                        aiRedFlags = result.redFlags
                        _state.value = SummaryState.AiUnavailable(
                            SummaryDto(
                                id = "",
                                recipientId = recipientId,
                                periodDays = _period.value,
                                text = "",
                                redFlags = result.redFlags,
                                evidence = emptyList(),
                                uncertainties = listOf(SummaryDto.UncertaintyDto("ai_unavailable", "AI unavailable")),
                            ),
                            redFlags = result.redFlags,
                            reasonCode = result.reasonCode,
                        )
                        return
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: HttpException) {
                _state.value = SummaryState.AiUnavailable(
                    lastSummary(),
                    redFlags = aiRedFlags,
                    reasonCode = ApiErrors.parse(e)?.code ?: "http_${e.code()}",
                )
                return
            } catch (e: IOException) {
                _state.value = SummaryState.AiUnavailable(
                    lastSummary(),
                    redFlags = aiRedFlags,
                    reasonCode = "network",
                )
                return
            } catch (e: Exception) {
                _state.value = SummaryState.AiUnavailable(
                    lastSummary(),
                    redFlags = aiRedFlags,
                    reasonCode = "unavailable",
                )
                return
            }
        }
        try {
            // No AI key: the backend (or the local-mode fallback) is the
            // summary source. It is still responsible for returning the
            // deterministic safety flags; an empty payload must never hide a
            // reported fall. LocalBackendApis computes them from Room.
            val summary = repository.authorized {
                apis.summaries().summarize(SummaryRequest(recipientId, _period.value))
            }
            _state.value = SummaryState.Content(summary)
            _suggestedForCurrent.value = false
            _suggestStatus.value = null
        } catch (e: LoggedOutException) {
            if (!preserve) {
                _state.value = SummaryState.Loading
            } else {
                lastSummary()?.let { _state.value = SummaryState.Content(it) }
            }
        } catch (e: HttpException) {
            _state.value = SummaryState.Rejected(
                ApiErrors.parse(e)?.code ?: "http_${e.code()}",
                lastSummary(),
            )
        } catch (e: IOException) {
            _state.value = SummaryState.AiUnavailable(
                lastSummary(),
                redFlags = aiRedFlags,
                reasonCode = "network",
            )
        }
    }

    private val _suggestStatus = MutableStateFlow<String?>(null)
    val suggestStatus: StateFlow<String?> = _suggestStatus.asStateFlow()

    fun suggestPlan() {
        val store = aiKeyStore ?: return
        if (!store.hasKey()) return
        if (!store.consentGiven()) return
        if (_suggesting.value || _suggestedForCurrent.value) return
        val summaryText = (_state.value as? SummaryState.Content)?.summary?.text.orEmpty()
        if (summaryText.isBlank()) {
            _suggestStatus.value = "empty_after_filter"
            return
        }
        _suggesting.value = true
        _suggestStatus.value = null
        exec.launch {
            try {
                val provider = store.provider() ?: AiProvider.OPENAI
                val key = store.apiKey().orEmpty()
                val model = store.model() ?: provider.defaultModel
                val baseUrl = store.baseUrlOrDefault(provider)
                val client = when (provider) {
                    AiProvider.OPENAI,
                    AiProvider.XAI,
                    AiProvider.OPEN_ROUTER ->
                        llmClientFactory.createClient(baseUrl, model, key)
                    AiProvider.ANTHROPIC ->
                        llmClientFactory.createAnthropicClient(baseUrl, model, key)
                    AiProvider.GEMINI ->
                        llmClientFactory.createGeminiClient(baseUrl, model, key)
                }
                val summaryIds = (_state.value as? SummaryState.Content)?.summary?.evidence?.map { it.noteId }?.toSet().orEmpty()
                val noteIds = if (summaryIds.isNotEmpty()) {
                    summaryIds
                } else {
                    val today = java.time.LocalDate.now()
                    val from = SummaryWindow.start(today, _period.value).toString()
                    val to = today.toString()
                    repository.authorized { apis.notes().history(recipientId, from, to) }.map { it.id }.toSet()
                }
                // Suggestions must be backed by tracked notes: only the
                // emptiness of this set is checked — the plan call itself
                // receives just the finished summary, never note IDs.
                if (noteIds.isEmpty()) {
                    _suggestStatus.value = "empty_after_filter"
                    return@launch
                }
                val service = PlanSuggestionService(client, store)
                val result = service.suggest(summaryText)
                when (result) {
                    is PlanSuggestionResult.Success -> {
                        val items = result.suggestion.changes.mapNotNull { change ->
                            val reason = change.reason.trim()
                            if (reason.isBlank()) return@mapNotNull null
                            buildString {
                                append("[").append(change.actionId).append("] ")
                                append(reason)
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
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            _suggestStatus.value = "error"
                        }
                    }
                    is PlanSuggestionResult.Failure -> {
                        _suggestStatus.value = result.reasonCode
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
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
