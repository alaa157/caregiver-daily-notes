package com.caregiver.mobile.ai

import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException

internal enum class GroundingProbeStatus { PASS, PARTIAL, FAIL, ERROR }

internal data class GroundingProbeResult(
    val totalObservations: Int,
    val groundedObservations: Int,
    val status: GroundingProbeStatus,
    /** Internal provider reason; callers must resolve it to localized copy before displaying it. */
    val reasonCode: String? = null,
)

/** Runs a deliberately fictional fixture through the same service used for real summaries. */
internal object GroundingProbe {
    suspend fun run(
        client: LlmClient,
        language: String,
        endInclusive: LocalDate = LocalDate.now(),
        promptPreferences: String? = null,
    ): GroundingProbeResult = try {
        val result = SummaryAiService(
            client = client,
            language = language,
            includeDiagnostics = true,
            promptPreferencesOverride = promptPreferences,
        ).summarize(
            recipientId = GroundingProbeFixture.RECIPIENT_ID,
            periodDays = SUMMARY_WINDOW_DAYS,
            notes = GroundingProbeFixture.notes(endInclusive),
            signals = emptyList(),
            endInclusive = endInclusive,
        )
        when (result) {
            is SummaryAiResult.Success -> {
                val diagnostics = result.diagnostics
                val total = diagnostics?.totalObservations ?: result.summary.evidence.size
                val grounded = diagnostics?.groundedObservations ?: result.summary.evidence.size
                val status = when {
                    total == 0 -> GroundingProbeStatus.FAIL
                    grounded == total -> GroundingProbeStatus.PASS
                    grounded > 0 -> GroundingProbeStatus.PARTIAL
                    else -> GroundingProbeStatus.FAIL
                }
                GroundingProbeResult(total, grounded, status)
            }
            is SummaryAiResult.Failure -> {
                val diagnostics = result.diagnostics
                if (diagnostics?.providerFailure == true) {
                    GroundingProbeResult(
                        totalObservations = diagnostics.totalObservations,
                        groundedObservations = diagnostics.groundedObservations,
                        status = GroundingProbeStatus.ERROR,
                        reasonCode = result.reasonCode,
                    )
                } else {
                    GroundingProbeResult(
                        totalObservations = diagnostics?.totalObservations ?: 0,
                        groundedObservations = diagnostics?.groundedObservations ?: 0,
                        status = GroundingProbeStatus.FAIL,
                    )
                }
            }
        }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: IOException) {
        GroundingProbeResult(0, 0, GroundingProbeStatus.ERROR, "network")
    } catch (_: Exception) {
        GroundingProbeResult(0, 0, GroundingProbeStatus.ERROR, "unavailable")
    }

    /** Consent is checked before constructing or sending the fictional payload. */
    suspend fun runIfConsented(
        client: LlmClient,
        language: String,
        consentGiven: Boolean,
        endInclusive: LocalDate = LocalDate.now(),
        promptPreferences: String? = null,
    ): GroundingProbeResult? {
        if (!consentGiven) return null
        return run(client, language, endInclusive, promptPreferences)
    }

    private const val SUMMARY_WINDOW_DAYS = 7
}
