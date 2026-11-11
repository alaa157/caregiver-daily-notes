package com.caregiver.mobile.presentation.common

import com.caregiver.mobile.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Reason codes are internal: the card must always get localized copy plus
 * the action that can actually recover, never the code itself.
 */
class AiRecoveryTest {

    @Test
    fun invalidKeyPointsAtSettings() {
        val copy = AiRecovery.copyFor("invalid_key", 7)

        assertEquals(R.string.ai_recovery_invalid_key, copy.message)
        assertEquals(AiRecoveryAction.OpenAiSettings, copy.primary)
        assertEquals(AiRecoveryAction.Retry, copy.secondary)
    }

    @Test
    fun rateLimitOffersRetry() {
        val copy = AiRecovery.copyFor("rate_limit", 7)

        assertEquals(R.string.ai_recovery_rate_limit, copy.message)
        assertEquals(AiRecoveryAction.Retry, copy.primary)
        assertNull(copy.secondary)
    }

    @Test
    fun noSourceDataOffersANoteAndTheLongerPeriod() {
        val copy = AiRecovery.copyFor("no_source_data", 7)

        assertEquals(R.string.ai_recovery_no_source, copy.message)
        assertEquals(7, copy.formatDays)
        assertEquals(AiRecoveryAction.AddNote, copy.primary)
        assertEquals(AiRecoveryAction.ExtendPeriod, copy.secondary)
    }

    @Test
    fun noSourceDataAtThirtyDaysOffersANoteAndRetry() {
        val copy = AiRecovery.copyFor("no_source_data", 30)

        assertEquals(R.string.ai_recovery_no_source_30, copy.message)
        assertNull(copy.formatDays)
        assertEquals(AiRecoveryAction.AddNote, copy.primary)
        assertEquals(AiRecoveryAction.Retry, copy.secondary)
    }

    @Test
    fun providerHttp400IsARequestProblemWithSettingsAndRetry() {
        val copy = AiRecovery.copyFor("http_400", 7)

        assertEquals(R.string.ai_recovery_request, copy.message)
        assertEquals(AiRecoveryAction.OpenAiSettings, copy.primary)
        assertEquals(AiRecoveryAction.Retry, copy.secondary)
    }

    @Test
    fun serviceHttp400IsNotBlamedOnTheAiKey() {
        val copy = AiRecovery.copyFor("http_400", 7, AiFailureSource.Service)

        assertEquals(R.string.ai_recovery_service_request, copy.message)
        assertEquals(AiRecoveryAction.Retry, copy.primary)
        assertNull(copy.secondary)
    }

    @Test
    fun malformedOutputAndUnavailableHavePlainCopy() {
        assertEquals(
            R.string.ai_recovery_malformed,
            AiRecovery.copyFor("malformed_output", 7).message,
        )
        assertEquals(
            R.string.ai_recovery_unavailable,
            AiRecovery.copyFor("unavailable", 7).message,
        )
    }

    @Test
    fun networkAndTimeoutAreDistinctFromGenericUnavailable() {
        val network = AiRecovery.copyFor("network", 7)
        assertEquals(R.string.ai_recovery_network, network.message)
        assertEquals(AiRecoveryAction.Retry, network.primary)
        assertNull(network.secondary)

        val timeout = AiRecovery.copyFor("timeout", 7)
        assertEquals(R.string.ai_recovery_timeout, timeout.message)
        assertEquals(AiRecoveryAction.Retry, timeout.primary)
        assertEquals(AiRecoveryAction.OpenAiSettings, timeout.secondary)

        // The generic fallback stays for genuinely unknown failures.
        assertEquals(
            R.string.ai_recovery_unavailable,
            AiRecovery.copyFor("unavailable", 7).message,
        )
    }

    @Test
    fun oversizedTellsTheUserToShortenTheDateRange() {
        val copy = AiRecovery.copyFor("oversized", 30)

        assertEquals(R.string.ai_recovery_oversized_input, copy.message)
        assertEquals(AiRecoveryAction.Retry, copy.primary)
        assertNull(copy.secondary)
    }

    @Test
    fun invalidEvidencePointsAtSettingsWithRetryAvailable() {
        val copy = AiRecovery.copyFor("invalid_evidence", 7)

        assertEquals(R.string.ai_recovery_invalid_evidence, copy.message)
        assertEquals(AiRecoveryAction.OpenAiSettings, copy.primary)
        assertEquals(AiRecoveryAction.Retry, copy.secondary)
    }

    @Test
    fun otherCodesRecoveryActionsAreUnchanged() {
        // Regression guard: the invalid_evidence change must not alter the
        // actions of neighbouring reason codes.
        val invalidKey = AiRecovery.copyFor("invalid_key", 7)
        assertEquals(AiRecoveryAction.OpenAiSettings, invalidKey.primary)
        assertEquals(AiRecoveryAction.Retry, invalidKey.secondary)

        val rateLimit = AiRecovery.copyFor("rate_limit", 7)
        assertEquals(AiRecoveryAction.Retry, rateLimit.primary)
        assertNull(rateLimit.secondary)

        val malformed = AiRecovery.copyFor("malformed_output", 7)
        assertEquals(AiRecoveryAction.Retry, malformed.primary)
        assertNull(malformed.secondary)

        val noSource = AiRecovery.copyFor("no_source_data", 7)
        assertEquals(AiRecoveryAction.AddNote, noSource.primary)
        assertEquals(AiRecoveryAction.ExtendPeriod, noSource.secondary)

        val network = AiRecovery.copyFor("network", 7)
        assertEquals(AiRecoveryAction.Retry, network.primary)
        assertNull(network.secondary)

        val unknown = AiRecovery.copyFor("some_new_provider_code", 7)
        assertEquals(AiRecoveryAction.Retry, unknown.primary)
        assertNull(unknown.secondary)
    }

    @Test
    fun tableDrivenFailuresMapToTheirRecoveryCopy() {
        assertEquals(
            R.string.ai_recovery_missing_key,
            AiRecovery.copyFor("no_key", 7).message,
        )
        assertEquals(
            AiRecoveryAction.OpenAiSettings,
            AiRecovery.copyFor("no_key", 7).primary,
        )
        assertEquals(
            R.string.ai_recovery_invalid_model,
            AiRecovery.copyFor("invalid_model", 7).message,
        )
        assertEquals(
            AiRecoveryAction.OpenAiSettings,
            AiRecovery.copyFor("invalid_model", 7).primary,
        )
        assertEquals(
            R.string.ai_recovery_invalid_evidence,
            AiRecovery.copyFor("invalid_evidence", 7).message,
        )
        assertEquals(
            R.string.ai_recovery_malformed,
            AiRecovery.copyFor("malformed_output", 7).message,
        )
    }

    @Test
    fun serviceTimeoutAndNetworkUseTheSamePlainCopy() {
        assertEquals(
            R.string.ai_recovery_timeout,
            AiRecovery.copyFor("timeout", 7, AiFailureSource.Service).message,
        )
        assertEquals(
            R.string.ai_recovery_network,
            AiRecovery.copyFor("network", 7, AiFailureSource.Service).message,
        )
    }

    @Test
    fun unknownCodesFallBackToGenericCopyInsteadOfLeakingRawText() {
        val copy = AiRecovery.copyFor("some_new_provider_code", 7)

        assertEquals(R.string.ai_recovery_generic, copy.message)
        assertEquals(AiRecoveryAction.Retry, copy.primary)
    }

    @Test
    fun codesAreMatchedCaseInsensitivelyAndIgnoreWhitespace() {
        val copy = AiRecovery.copyFor("  INVALID_KEY ", 7)

        assertEquals(R.string.ai_recovery_invalid_key, copy.message)
    }
}
