package com.caregiver.mobile.presentation.recipients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.LoggedOutException
import com.caregiver.mobile.data.api.BackendApis
import com.caregiver.mobile.data.api.NoteDto
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

enum class DetailAction { AddNote, History, CarePlan, Summary }

data class RecipientDetail(
    val id: String,
    val name: String,
    val active: Boolean,
    val lastNote: NoteDto?,
    val actions: List<DetailAction> = DetailAction.entries,
)

sealed interface DetailState {
    data object Loading : DetailState
    data class Content(val detail: RecipientDetail) : DetailState
    data object Error : DetailState
}

/**
 * Recipient detail (board 4): header data, latest note, and the four design
 * actions. The screen maps actions to destinations; the ViewModel only
 * declares them with recipient context.
 */
class RecipientDetailViewModel(
    private val recipientId: String,
    private val apis: BackendApis,
    private val repository: AuthRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {
    // Production uses viewModelScope; tests inject their TestScope.
    private val execScope: CoroutineScope = scope ?: viewModelScope
    private val _state = MutableStateFlow<DetailState>(DetailState.Loading)
    val state: StateFlow<DetailState> = _state

    init {
        execScope.launch { load() }
    }

    fun refresh() {
        _state.value = DetailState.Loading
        execScope.launch { load() }
    }

    private suspend fun load() {
        try {
            val recipients = repository.authorized { apis.recipients().list() }
            val recipient = recipients.firstOrNull { it.id == recipientId }
            if (recipient == null) {
                _state.value = DetailState.Error
                return
            }
            val notes = repository.authorized { apis.notes().history(recipientId, null, null) }
            _state.value = DetailState.Content(
                RecipientDetail(
                    id = recipient.id,
                    name = recipient.name,
                    active = recipient.active,
                    lastNote = notes.maxByOrNull { it.date },
                ),
            )
        } catch (e: LoggedOutException) {
            // Root nav flips to login; nothing to show here.
        } catch (e: HttpException) {
            _state.value = DetailState.Error
        } catch (e: IOException) {
            _state.value = DetailState.Error
        }
    }
}
