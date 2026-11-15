package com.caregiver.mobile.presentation.settings

import kotlinx.coroutines.CancellationException

enum class SetupStage { Connection, Format, Quality }

sealed interface StageOutcome {
    data object Pass : StageOutcome

    data class Graded(val grounded: Int, val total: Int, val full: Boolean) : StageOutcome

    data class Fail(val reasonCode: String) : StageOutcome

    data object NeedsConsent : StageOutcome
}

sealed interface StageStatus {
    data object Pending : StageStatus

    data object Running : StageStatus

    data object Passed : StageStatus

    data class Graded(val grounded: Int, val total: Int, val full: Boolean) : StageStatus

    data class Failed(val reasonCode: String) : StageStatus

    data object Skipped : StageStatus

    data object Cancelled : StageStatus
}

data class SetupCheckState(
    val stages: Map<SetupStage, StageStatus>,
    val running: Boolean,
    val awaitingConsent: Boolean,
) {
    val blockingFailure: Boolean
        get() = stages[SetupStage.Connection] is StageStatus.Failed ||
            stages[SetupStage.Format] is StageStatus.Failed
}

class AiSetupCheckRunner(
    private val runStage: suspend (SetupStage) -> StageOutcome,
) {
    suspend fun run(
        from: SetupStage = SetupStage.Connection,
        previous: SetupCheckState? = null,
        onState: (SetupCheckState) -> Unit,
    ): SetupCheckState {
        val orderedStages = SetupStage.values().toList()
        val statuses = orderedStages.associateWith { stage ->
            if (stage.ordinal < from.ordinal) {
                previous?.stages?.get(stage) ?: StageStatus.Pending
            } else {
                StageStatus.Pending
            }
        }.toMutableMap()
        var awaitingConsent = false

        fun emit(running: Boolean): SetupCheckState =
            SetupCheckState(
                stages = statuses.toMap(),
                running = running,
                awaitingConsent = awaitingConsent,
            ).also(onState)

        emit(running = true)
        for (index in from.ordinal..orderedStages.lastIndex) {
            val stage = orderedStages[index]
            statuses[stage] = StageStatus.Running
            emit(running = true)

            val outcome = try {
                runStage(stage)
            } catch (e: CancellationException) {
                for (laterIndex in index..orderedStages.lastIndex) {
                    val laterStage = orderedStages[laterIndex]
                    if (statuses[laterStage] == StageStatus.Running ||
                        statuses[laterStage] == StageStatus.Pending
                    ) {
                        statuses[laterStage] = StageStatus.Cancelled
                    }
                }
                emit(running = false)
                throw e
            }

            when (outcome) {
                StageOutcome.Pass -> statuses[stage] = StageStatus.Passed
                is StageOutcome.Graded -> {
                    statuses[stage] = StageStatus.Graded(outcome.grounded, outcome.total, outcome.full)
                }
                is StageOutcome.Fail -> statuses[stage] = StageStatus.Failed(outcome.reasonCode)
                StageOutcome.NeedsConsent -> {
                    statuses[stage] = StageStatus.Pending
                    awaitingConsent = true
                    return emit(running = false)
                }
            }

            if (outcome is StageOutcome.Fail && stage != SetupStage.Quality) {
                for (laterStage in orderedStages.drop(index + 1)) {
                    statuses[laterStage] = StageStatus.Skipped
                }
                return emit(running = false)
            }

            if (index == orderedStages.lastIndex) {
                return emit(running = false)
            }
            emit(running = true)
        }

        return emit(running = false)
    }
}
