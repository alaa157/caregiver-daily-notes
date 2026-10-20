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
