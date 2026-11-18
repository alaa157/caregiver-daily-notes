package com.caregiver.mobile.presentation.settings

import com.caregiver.mobile.data.update.AvailableUpdate
import com.caregiver.mobile.data.update.UpdateChecker
import com.caregiver.mobile.data.update.UpdateResult
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AboutViewModelTest {

    private val update = AvailableUpdate(
        version = "0.3.5",
        releaseUrl = "https://example.test/release",
        downloadUrl = "https://example.test/app.apk",
    )

    @Test
    fun checksOnceWhenCreatedAndExposesTheAvailableUpdate() = runTest {
        var calls = 0
        val vm = AboutViewModel(
            UpdateChecker {
                calls += 1
                UpdateResult.Available(update)
            },
            this,
        )

        advanceUntilIdle()

        assertEquals(1, calls)
        assertEquals(
            AboutUpdateState.Available(
                version = update.version,
                releaseUrl = update.releaseUrl,
                downloadUrl = update.downloadUrl,
            ),
            vm.updateState.value,
        )
    }

    @Test
    fun failureBecomesPlainRecoveryStateAndRetryCanSucceed() = runTest {
        var fail = true
        val vm = AboutViewModel(
            UpdateChecker {
                if (fail) throw IOException("offline") else UpdateResult.UpToDate
            },
            this,
        )
        advanceUntilIdle()
        assertEquals(AboutUpdateState.Failed, vm.updateState.value)

        fail = false
        vm.check()
        assertEquals(AboutUpdateState.Checking, vm.updateState.value)
        advanceUntilIdle()

        assertEquals(AboutUpdateState.UpToDate, vm.updateState.value)
    }

    @Test
    fun repeatedTapsWhileCheckingDoNotStartAnotherRequest() = runTest {
        val gate = CompletableDeferred<Unit>()
        var calls = 0
        val vm = AboutViewModel(
            UpdateChecker {
                calls += 1
                gate.await()
                UpdateResult.UpToDate
            },
            this,
        )
        runCurrent()
        assertEquals(1, calls)

        vm.check()
        runCurrent()
        assertEquals(1, calls)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(AboutUpdateState.UpToDate, vm.updateState.value)
    }
}
