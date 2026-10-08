package com.caregiver.mobile.presentation.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.LoggedOutException
import com.caregiver.mobile.data.api.AppendVersionRequest
import com.caregiver.mobile.data.api.BackendApis
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface PlanEditState {
    data object Loading : PlanEditState
    data class Content(val lines: List<String>) : PlanEditState
    data object Error : PlanEditState
}

/**
 * Plan edit (board 13): item list editing plus a single version append. The
 * backend generates the version reason itself ("<status> via preview.") and
 * accepts no reason input, so the client sends only the edited items with
 * the edited-and-accepted status (see docs/api/android-gaps.md).
 */
class PlanEditViewModel(
    private val planId: String,
    private val apis: BackendApis,
    private val repository: AuthRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val exec: CoroutineScope = scope ?: viewModelScope

    private val _state = MutableStateFlow<PlanEditState>(PlanEditState.Loading)
    val state: StateFlow<PlanEditState> = _state

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved

    private val _saveFailed = MutableStateFlow(false)
    val saveFailed: StateFlow<Boolean> = _saveFailed

    init {
        exec.launch { load() }
    }

    fun updateLine(index: Int, value: String) {
        val lines = (state.value as? PlanEditState.Content)?.lines ?: return
        if (index !in lines.indices) {
            return
        }
        _state.value = PlanEditState.Content(lines.toMutableList().also { it[index] = value })
        _saveFailed.value = false
    }

    fun addLine() {
        val lines = (state.value as? PlanEditState.Content)?.lines ?: return
        _state.value = PlanEditState.Content(lines + "")
        _saveFailed.value = false
    }

    fun removeLine(index: Int) {
        val lines = (state.value as? PlanEditState.Content)?.lines ?: return
        if (index !in lines.indices) {
            return
        }
        _state.value = PlanEditState.Content(lines.toMutableList().also { it.removeAt(index) })
        _saveFailed.value = false
    }

    fun save() {
        val lines = (state.value as? PlanEditState.Content)?.lines ?: return
        _busy.value = true
        _saveFailed.value = false
        exec.launch {
            try {
                repository.authorized {
                    apis.plans().appendVersion(
                        planId,
                        AppendVersionRequest(
                            EDITED_STATUS,
                            lines.filter { it.isNotBlank() },
                        ),
                    )
                }
                _busy.value = false
                _saved.value = true
            } catch (e: LoggedOutException) {
                _busy.value = false
            } catch (e: HttpException) {
                _busy.value = false
                _saveFailed.value = true
            } catch (e: IOException) {
                _busy.value = false
                _saveFailed.value = true
            } catch (e: CancellationException) {
                _busy.value = false
                throw e
            }
        }
    }

    private suspend fun load() {
        try {
            val plans = repository.authorized { apis.plans().list() }
            val plan = plans.firstOrNull { it.id == planId }
            val latest = plan?.versions?.maxByOrNull { it.version }
            if (latest == null) {
                _state.value = PlanEditState.Error
                return
            }
            _state.value = PlanEditState.Content(latest.items.toList())
        } catch (e: LoggedOutException) {
            // Root nav flips to login; nothing to show here.
        } catch (e: HttpException) {
            _state.value = PlanEditState.Error
        } catch (e: IOException) {
            _state.value = PlanEditState.Error
        }
    }

    companion object {
        const val EDITED_STATUS = "Edited-and-Accepted"
    }
}
