package com.caregiver.mobile.presentation.plans

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.LoggedOutException
import com.caregiver.mobile.data.api.BackendApis
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class PlanRow(
    val id: String,
    val recipientId: String,
    val recipientName: String,
    val latestStatus: String,
    val versionCount: Int,
)

sealed interface PlansState {
    data object Loading : PlansState
    data class Content(val plans: List<PlanRow>, val refreshing: Boolean = false) : PlansState
    data object Error : PlansState
}

/**
 * Plans list (missing page): one row per plan with the recipient name,
 * latest status, and version count. Unknown recipients fall back to the
 * raw ID rather than inventing a name.
 */
class PlansViewModel(
    private val apis: BackendApis,
    private val repository: AuthRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val exec: CoroutineScope = scope ?: viewModelScope

    private val _state = MutableStateFlow<PlansState>(PlansState.Loading)
    val state: StateFlow<PlansState> = _state

    init {
        exec.launch { load() }
    }

    fun refresh() {
        // Keep the visible list with a progress marker instead of flashing
        // back to full-screen loading (same convention as Home/Summary).
        val current = (_state.value as? PlansState.Content)?.plans
        _state.value = if (current != null) {
            PlansState.Content(current, refreshing = true)
        } else {
            PlansState.Loading
        }
        exec.launch { load() }
    }

    private suspend fun load() {
        try {
            val recipients = repository.authorized { apis.recipients().list() }
            val names = recipients.associate { it.id to it.name }
            val plans = repository.authorized { apis.plans().list() }
            _state.value = PlansState.Content(
                plans.map { plan ->
                    val latest = plan.versions.maxByOrNull { it.version }
                    PlanRow(
                        id = plan.id,
                        recipientId = plan.recipientId,
                        recipientName = names[plan.recipientId] ?: plan.recipientId,
                        latestStatus = latest?.status.orEmpty(),
                        versionCount = plan.versions.size,
                    )
                },
            )
        } catch (e: LoggedOutException) {
            // Root nav flips to login; nothing to show here.
        } catch (e: HttpException) {
            _state.value = PlansState.Error
        } catch (e: IOException) {
            _state.value = PlansState.Error
        }
    }
}
