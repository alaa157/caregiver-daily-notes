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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withTimeout
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

    /** The active load: cancelled by the user through cancelGeneration. */
    private var loadJob: Job? = null

    /** Set on user cancel so a queued load never paints over it. */
    private var cancelledByUser = false

    /** Last deterministic flags, so cancel/timeout states keep them. */
    private var lastRedFlags: List<String> = emptyList()

    private val _period = MutableStateFlow(initialPeriod)
    val period: StateFlow<Int> = _period.asStateFlow()

    private val _state = MutableStateFlow<SummaryState>(SummaryState.Loading)
    val state: StateFlow<SummaryState> = _state.asStateFlow()

    private val _suggesting = MutableStateFlow(false)
    val suggesting: StateFlow<Boolean> = _suggesting.asStateFlow()

    private val _suggestedForCurrent = MutableStateFlow(false)
    val suggestedForCurrent: StateFlow<Boolean> = _suggestedForCurrent.asStateFlow()

    init {
        loadJob = exec.launch { load(preserve = false) }
    }

    fun setPeriod(days: Int) {
        if (days != _period.value) {
            _period.value = days
            refresh()
        }
    }

    fun refresh() {
        cancelledByUser = false
        val current = (_state.value as? SummaryState.Content)?.summary
        _suggestedForCurrent.value = false
        _suggestStatus.value = null
        _state.value = if (current != null) {
            SummaryState.Content(current, refreshing = true)
        } else {
            SummaryState.Loading
        }
        loadJob = exec.launch { load(preserve = current != null) }
    }

    fun grantConsent() {
        aiKeyStore?.setConsentGiven()
        cancelledByUser = false
        _state.value = SummaryState.Loading
        loadJob = exec.launch { load(preserve = false) }
    }

    /** True while the screen is waiting for a generation to finish. */
    private fun waiting(): Boolean =
        _state.value is SummaryState.Loading ||
            (_state.value as? SummaryState.Content)?.refreshing == true

/**
     * The user ends a generation that providers failed to answer in time.
     * The screen leaves Loading with recovery copy ("AI unavailable",
     * retry) instead of a stuck spinner. A queued-but-not-started load
     * honours the cancel and never paints over the cancelled state.
     */
    fun cancelGeneration() {
        if (!waiting()) return
        cancelledByUser = true
        loadJob?.cancel()
        _state.value = SummaryState.AiUnavailable(
            lastSummary(),
            redFlags = lastRedFlags,
            reasonCode = "cancelled",
        )
    }

    private suspend fun load(preserve: Boolean) {
        if (cancelledByUser) return
        val store = aiKeyStore
        val provider = store?.selectedProvider()
        var aiRedFlags: List<String> = emptyList()
        if (store != null && provider != null && store.hasKey(provider)) {
            if (!store.consentGiven()) {
                _state.value = SummaryState.NeedsConsent
                return
            }
            try {
                val key = store.apiKey(provider).orEmpty()
                val model = store.model(provider) ?: provider.defaultModel
                val baseUrl = store.baseUrlOrDefault(provider)
                // One overall deadline for the whole generation. The client's
                // retry rounds (empty/truncated/5xx backoff) plus the JSON
                // repair retry can otherwise stretch without bound: the
                // per-call timeout defaults to 0 (none) in LlmModels, and a
                // nested timeout surfaced as an ordinary CancellationException
                // that died silently in Loading.
                val result = withTimeout(SUMMARY_DEADLINE_MS) {
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
                    lastRedFlags = aiRedFlags
                    service.summarize(recipientId, _period.value, notes, signals, today)
                }
                when (result) {
                    is SummaryAiResult.Success -> {
                        _state.value = SummaryState.Content(result.summary, aiGenerated = true)
                        _suggestedForCurrent.value = false
                        _suggestStatus.value = null
                        return
                    }
                    is SummaryAiResult.Failure -> {
                        aiRedFlags = result.redFlags
                        lastRedFlags = result.redFlags
                        if (waiting()) _state.value = SummaryState.AiUnavailable(
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
            } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                // The overall deadline beat the provider: bounded failure,
                // recovery copy — never a silent death at Loading.
                if (waiting()) _state.value = SummaryState.AiUnavailable(
                    lastSummary(),
                    redFlags = aiRedFlags,
                    reasonCode = "timeout",
                )
                return
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: HttpException) {
                if (waiting()) _state.value = SummaryState.AiUnavailable(
                    lastSummary(),
                    redFlags = aiRedFlags,
                    reasonCode = ApiErrors.parse(e)?.code ?: "http_${e.code()}",
                )
                return
            } catch (e: IOException) {
                if (waiting()) _state.value = SummaryState.AiUnavailable(
                    lastSummary(),
                    redFlags = aiRedFlags,
                    reasonCode = "network",
                )
                return
            } catch (e: Exception) {
                // Catch-all (Finding 2): any unexpected failure ends in a
                // bounded error state instead of escaping the coroutine.
                if (waiting()) _state.value = SummaryState.AiUnavailable(
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
            val summary = withTimeout(SUMMARY_DEADLINE_MS) {
                repository.authorized {
                    apis.summaries().summarize(SummaryRequest(recipientId, _period.value))
                }
            }
            _state.value = SummaryState.Content(summary)
            _suggestedForCurrent.value = false
            _suggestStatus.value = null
        } catch (e: LoggedOutException) {
            if (!preserve) {
                if (waiting()) _state.value = SummaryState.Loading
            } else {
                lastSummary()?.let { if (waiting()) _state.value = SummaryState.Content(it) }
            }
        } catch (e: HttpException) {
            if (waiting()) _state.value = SummaryState.Rejected(
                ApiErrors.parse(e)?.code ?: "http_${e.code()}",
                lastSummary(),
            )
        } catch (e: IOException) {
            if (waiting()) _state.value = SummaryState.AiUnavailable(
                lastSummary(),
                redFlags = lastRedFlags,
                reasonCode = "network",
            )
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            if (waiting()) _state.value = SummaryState.AiUnavailable(
                lastSummary(),
                redFlags = lastRedFlags,
                reasonCode = "timeout",
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Catch-all (Finding 2): the non-AI path previously caught only
            // LoggedOut/Http/IO — any other exception escaped and either
            // stuck the screen on Loading or crashed the process.
            if (waiting()) _state.value = SummaryState.AiUnavailable(
                lastSummary(),
                redFlags = lastRedFlags,
                reasonCode = "unavailable",
            )
        }
    }

    private val _suggestStatus = MutableStateFlow<String?>(null)
    val suggestStatus: StateFlow<String?> = _suggestStatus.asStateFlow()

    fun suggestPlan() {
        val store = aiKeyStore ?: return
        val provider = store.selectedProvider()
        if (!store.hasKey(provider)) return
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
                val key = store.apiKey(provider).orEmpty()
                val model = store.model(provider) ?: provider.defaultModel
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

        /**
         * One overall deadline for a summary generation: it must cover the
         * client's retry rounds (3 attempts with backoff on empty, truncated
         * and 5xx replies) plus the JSON repair round in the shared reader —
         * and still end the attempt while the user is watching.
         */
        const val SUMMARY_DEADLINE_MS = 120_000L
    }
}
