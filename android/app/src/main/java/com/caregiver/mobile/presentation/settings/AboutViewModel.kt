package com.caregiver.mobile.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.data.update.UpdateChecker
import com.caregiver.mobile.data.update.UpdateResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AboutUpdateState {
    data object Checking : AboutUpdateState
    data object UpToDate : AboutUpdateState
    data class Available(
        val version: String,
        val releaseUrl: String,
        val downloadUrl: String,
    ) : AboutUpdateState
    data object Failed : AboutUpdateState
}

/**
 * One update check when About opens, plus explicit retries. Raw transport
 * and parsing errors never reach the UI: every failure becomes [Failed],
 * which the screen renders as plain-language recovery copy.
 */
class AboutViewModel(
    private val checker: UpdateChecker,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val exec: CoroutineScope = scope ?: viewModelScope

    private val _updateState = MutableStateFlow<AboutUpdateState>(AboutUpdateState.Checking)
    val updateState: StateFlow<AboutUpdateState> = _updateState.asStateFlow()

    private var checking = false

    init {
        check()
    }

    fun check() {
        if (checking) return
        checking = true
        _updateState.value = AboutUpdateState.Checking
        exec.launch {
            try {
                _updateState.value = when (val result = checker.check()) {
                    is UpdateResult.Available -> AboutUpdateState.Available(
                        version = result.update.version,
                        releaseUrl = result.update.releaseUrl,
                        downloadUrl = result.update.downloadUrl,
                    )
                    UpdateResult.UpToDate -> AboutUpdateState.UpToDate
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _updateState.value = AboutUpdateState.Failed
            } finally {
                checking = false
            }
        }
    }
}
