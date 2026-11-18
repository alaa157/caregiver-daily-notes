package com.caregiver.mobile.presentation.settings

import java.util.concurrent.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSetupCheckRunnerTest {

    @Test
    fun allPassingStagesArePassedAndDoNotBlock() = runTest {
        val runner = AiSetupCheckRunner { StageOutcome.Pass }
        val emissions = mutableListOf<SetupCheckState>()

        val result = runner.run(onState = emissions::add)

        assertEquals(
            listOf(StageStatus.Passed, StageStatus.Passed, StageStatus.Passed),
            SetupStage.values().map { result.stages[it] },
        )
        assertFalse(result.blockingFailure)
        assertFalse(result.running)
        assertFalse(emissions.last().running)
    }

    @Test
    fun connectionFailureSkipsFormatAndQuality() = runTest {
        val called = mutableListOf<SetupStage>()
        val runner = AiSetupCheckRunner { stage ->
            called += stage
            StageOutcome.Fail("connection_failed")
        }

        val result = runner.run(onState = {})

        assertEquals(listOf(SetupStage.Connection), called)
        assertEquals(StageStatus.Failed("connection_failed"), result.stages[SetupStage.Connection])
        assertEquals(StageStatus.Skipped, result.stages[SetupStage.Format])
        assertEquals(StageStatus.Skipped, result.stages[SetupStage.Quality])
        assertTrue(result.blockingFailure)
    }

    @Test
    fun formatFailureSkipsQuality() = runTest {
        val called = mutableListOf<SetupStage>()
        val runner = AiSetupCheckRunner { stage ->
            called += stage
            if (stage == SetupStage.Format) StageOutcome.Fail("format_invalid") else StageOutcome.Pass
        }

        val result = runner.run(onState = {})

        assertEquals(listOf(SetupStage.Connection, SetupStage.Format), called)
        assertEquals(StageStatus.Passed, result.stages[SetupStage.Connection])
        assertEquals(StageStatus.Failed("format_invalid"), result.stages[SetupStage.Format])
        assertEquals(StageStatus.Skipped, result.stages[SetupStage.Quality])
        assertTrue(result.blockingFailure)
    }

    @Test
    fun qualityFailureDoesNotBlock() = runTest {
        val runner = AiSetupCheckRunner { stage ->
            if (stage == SetupStage.Quality) StageOutcome.Fail("quality_failed") else StageOutcome.Pass
        }

        val result = runner.run(onState = {})

        assertEquals(StageStatus.Failed("quality_failed"), result.stages[SetupStage.Quality])
        assertFalse(result.blockingFailure)
    }

    @Test
    fun partialQualityDoesNotBlock() = runTest {
        val runner = AiSetupCheckRunner { stage ->
            if (stage == SetupStage.Quality) {
                StageOutcome.Graded(grounded = 2, total = 3, full = false)
            } else {
                StageOutcome.Pass
            }
        }

        val result = runner.run(onState = {})

        assertEquals(StageStatus.Graded(2, 3, false), result.stages[SetupStage.Quality])
        assertFalse(result.blockingFailure)
    }

    @Test
    fun consentCanResumeAtFormatAndKeepConnectionPassed() = runTest {
        var consentNeeded = true
        val runner = AiSetupCheckRunner { stage ->
            if (stage == SetupStage.Format && consentNeeded) {
                StageOutcome.NeedsConsent
            } else {
                StageOutcome.Pass
            }
        }

        val waiting = runner.run(onState = {})

        assertEquals(StageStatus.Passed, waiting.stages[SetupStage.Connection])
        assertEquals(StageStatus.Pending, waiting.stages[SetupStage.Format])
        assertTrue(waiting.awaitingConsent)
        assertFalse(waiting.running)

        consentNeeded = false
        val resumed = runner.run(from = SetupStage.Format, previous = waiting, onState = {})

        assertEquals(StageStatus.Passed, resumed.stages[SetupStage.Connection])
        assertEquals(StageStatus.Passed, resumed.stages[SetupStage.Format])
        assertEquals(StageStatus.Passed, resumed.stages[SetupStage.Quality])
        assertFalse(resumed.awaitingConsent)
    }

    @Test
    fun cancellationMarksCurrentAndLaterStagesCancelledAndRethrows() = runTest {
        val emissions = mutableListOf<SetupCheckState>()
        val runner = AiSetupCheckRunner { stage ->
            when (stage) {
                SetupStage.Connection -> StageOutcome.Pass
                SetupStage.Format -> throw CancellationException("format cancelled")
                SetupStage.Quality -> StageOutcome.Pass
            }
        }
        val job = async { runner.run(onState = emissions::add) }

        val failure = runCatching { job.await() }.exceptionOrNull()

        assertTrue(failure is CancellationException)
        assertTrue(job.isCancelled)
        val cancelled = emissions.last()
        assertEquals(StageStatus.Passed, cancelled.stages[SetupStage.Connection])
        assertEquals(StageStatus.Cancelled, cancelled.stages[SetupStage.Format])
        assertEquals(StageStatus.Cancelled, cancelled.stages[SetupStage.Quality])
        assertFalse(cancelled.running)
    }
}
