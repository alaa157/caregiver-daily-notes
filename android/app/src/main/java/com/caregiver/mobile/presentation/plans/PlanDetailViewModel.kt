package com.caregiver.mobile.presentation.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.LoggedOutException
import com.caregiver.mobile.data.api.BackendApis
import com.caregiver.mobile.data.api.PlanVersionDto
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

enum class PlanAction { Accept, Dismiss, Archive }

data class PlanDetail(val planId: String, val versions: List<PlanVersionDto>)

sealed interface PlanDetailState {
    data object Loading : PlanDetailState
    data class Content(val detail: PlanDetail) : PlanDetailState
    data object Error : PlanDetailState
}

sealed interface PlanActionError {
    data object IllegalTransition : PlanActionError
    data object Failed : PlanActionError
}

/**
 * Plan proposal (board 12): versions newest-first plus the accept, dismiss,
 * and archive transitions. A 422 means the server rejected the transition
 * for the current status — localized at the screen, never raw.
 */
class PlanDetailViewModel(
    private val planId: String,
    private val apis: BackendApis,
    private val repository: AuthRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val exec: CoroutineScope = scope ?: viewModelScope

    private val _state = MutableStateFlow<PlanDetailState>(PlanDetailState.Loading)
    val state: StateFlow<PlanDetailState> = _state

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy

    private val _actionError = MutableStateFlow<PlanActionError?>(null)
    val actionError: StateFlow<PlanActionError?> = _actionError

    init {
        exec.launch { load() }
    }

    fun refresh() {
        _state.value = PlanDetailState.Loading
        exec.launch { load() }
    }

    fun clearActionError() {
        _actionError.value = null
    }

    fun transition(action: PlanAction) {
        _busy.value = true
        _actionError.value = null
        exec.launch {
            try {
                repository.authorized {
                    when (action) {
                        PlanAction.Accept -> apis.plans().accept(planId)
                        PlanAction.Dismiss -> apis.plans().dismiss(planId)
                        PlanAction.Archive -> apis.plans().archive(planId)
                    }
                }
                _busy.value = false
                load()
            } catch (e: LoggedOutException) {
                _busy.value = false
            } catch (e: HttpException) {
                _busy.value = false
                _actionError.value = if (e.code() == 422) {
                    PlanActionError.IllegalTransition
                } else {
                    PlanActionError.Failed
                }
            } catch (e: IOException) {
                _busy.value = false
                _actionError.value = PlanActionError.Failed
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
            if (plan == null) {
                _state.value = PlanDetailState.Error
                return
            }
            _state.value = PlanDetailState.Content(
                PlanDetail(planId, plan.versions.sortedByDescending { it.version }),
            )
        } catch (e: LoggedOutException) {
            // Root nav flips to login; nothing to show here.
        } catch (e: HttpException) {
            _state.value = PlanDetailState.Error
        } catch (e: IOException) {
            _state.value = PlanDetailState.Error
        }
    }
}
