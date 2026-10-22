package com.caregiver.mobile.presentation.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.caregiver.mobile.R
import java.util.Locale

/** What the caregiver can do about a failed AI call. */
enum class AiRecoveryAction {
    Retry,
    OpenAiSettings,
    AddNote,
    ExtendPeriod,
}

/** Whether the failure came from the AI provider or the summary service. */
enum class AiFailureSource {
    Provider,
    Service,
}

/**
 * Localized recovery copy for a reason code. Never render the code itself:
 * callers resolve this copy through [resolvedMessage] and render the actions
 * as buttons.
 */
data class AiRecoveryCopy(
    @StringRes val message: Int,
    /** When set, [message] has a single `%1$d` placeholder for this period. */
    val formatDays: Int? = null,
    val primary: AiRecoveryAction? = null,
    val secondary: AiRecoveryAction? = null,
)

/**
 * Maps provider/service reason codes to plain language and a fix. Unknown
 * codes deliberately fall back to generic copy so a raw code can never leak
 * into the UI.
 */
object AiRecovery {
    const val EXTENDED_PERIOD_DAYS = 30

    fun copyFor(
        reasonCode: String?,
        periodDays: Int = 7,
        source: AiFailureSource = AiFailureSource.Provider,
    ): AiRecoveryCopy {
        val code = reasonCode?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return if (source == AiFailureSource.Service) {
            serviceCopy(code, periodDays)
        } else {
            providerCopy(code, periodDays)
        }
    }

    private fun providerCopy(code: String, periodDays: Int): AiRecoveryCopy = when {
        code == "invalid_key" || code == "http_401" || code == "http_403" ->
            AiRecoveryCopy(
                R.string.ai_recovery_invalid_key,
                primary = AiRecoveryAction.OpenAiSettings,
                secondary = AiRecoveryAction.Retry,
            )
        code == "rate_limit" || code == "http_429" -> rateLimit()
        code == "no_source_data" -> noSource(periodDays)
        code == "malformed_output" || code == "parse_error" ->
            AiRecoveryCopy(
                R.string.ai_recovery_malformed,
                primary = AiRecoveryAction.Retry,
            )
        code == "invalid_evidence" ->
            AiRecoveryCopy(
                R.string.ai_recovery_invalid_evidence,
                primary = AiRecoveryAction.Retry,
            )
        code == "oversized" ->
            AiRecoveryCopy(
                R.string.ai_recovery_oversized,
                primary = AiRecoveryAction.Retry,
                secondary = AiRecoveryAction.OpenAiSettings,
            )
        code == "invalid_url" || code == "http_404" ->
            AiRecoveryCopy(
                R.string.ai_recovery_invalid_url,
                primary = AiRecoveryAction.OpenAiSettings,
                secondary = AiRecoveryAction.Retry,
            )
        code == "json_mode_unsupported" ->
            AiRecoveryCopy(
                R.string.ai_recovery_json_mode,
                primary = AiRecoveryAction.OpenAiSettings,
                secondary = AiRecoveryAction.Retry,
            )
        code == "blocked" ->
            AiRecoveryCopy(
                R.string.ai_recovery_blocked,
                primary = AiRecoveryAction.Retry,
                secondary = AiRecoveryAction.OpenAiSettings,
            )
        code == "invalid_request" || code == "http_400" ||
            code == "http_422" || code == "validation_error" ->
            AiRecoveryCopy(
                R.string.ai_recovery_request,
                primary = AiRecoveryAction.OpenAiSettings,
                secondary = AiRecoveryAction.Retry,
            )
        code == "missing_key" || code == "no_key" || code == "not_configured" ->
            AiRecoveryCopy(
                R.string.ai_recovery_missing_key,
                primary = AiRecoveryAction.OpenAiSettings,
                secondary = AiRecoveryAction.Retry,
            )
        code == "network" || code == "unavailable" || code == "llm_fallback" ||
            code == "timeout" || code == "cancelled" ->
            AiRecoveryCopy(
                R.string.ai_recovery_unavailable,
                primary = AiRecoveryAction.Retry,
            )
        code.startsWith("http_5") || code == "server_error" ->
            AiRecoveryCopy(
                R.string.ai_recovery_provider_down,
                primary = AiRecoveryAction.Retry,
            )
        code == "empty_output" || code == "truncated" || code == "incomplete" ->
            AiRecoveryCopy(
                R.string.ai_recovery_incomplete,
                primary = AiRecoveryAction.Retry,
            )
        else ->
            AiRecoveryCopy(
                R.string.ai_recovery_generic,
                primary = AiRecoveryAction.Retry,
            )
    }

    private fun serviceCopy(code: String, periodDays: Int): AiRecoveryCopy = when {
        code == "no_source_data" -> noSource(periodDays)
        code == "rate_limit" || code == "rate_limited" || code == "http_429" -> rateLimit()
        code.startsWith("http_5") || code == "server_error" ->
            AiRecoveryCopy(
                R.string.ai_recovery_service_down,
                primary = AiRecoveryAction.Retry,
            )
        code == "http_400" || code == "http_422" ||
            code == "validation_error" || code == "bad_request" ->
            AiRecoveryCopy(
                R.string.ai_recovery_service_request,
                primary = AiRecoveryAction.Retry,
            )
        else ->
            AiRecoveryCopy(
                R.string.ai_recovery_service,
                primary = AiRecoveryAction.Retry,
            )
    }

    private fun rateLimit() = AiRecoveryCopy(
        R.string.ai_recovery_rate_limit,
        primary = AiRecoveryAction.Retry,
    )

    private fun noSource(periodDays: Int): AiRecoveryCopy =
        if (periodDays >= EXTENDED_PERIOD_DAYS) {
            AiRecoveryCopy(
                R.string.ai_recovery_no_source_30,
                primary = AiRecoveryAction.AddNote,
                secondary = AiRecoveryAction.Retry,
            )
        } else {
            AiRecoveryCopy(
                R.string.ai_recovery_no_source,
                formatDays = periodDays,
                primary = AiRecoveryAction.AddNote,
                secondary = AiRecoveryAction.ExtendPeriod,
            )
        }
}

fun AiRecoveryCopy.resolvedMessage(context: Context): String =
    formatDays?.let { context.getString(message, it) } ?: context.getString(message)

@Composable
fun AiRecoveryCopy.resolvedMessage(): String =
    formatDays?.let { stringResource(message, it) } ?: stringResource(message)
