package com.caregiver.mobile.presentation.recipients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.LoggedOutException
import com.caregiver.mobile.data.api.BackendApis
import com.caregiver.mobile.data.api.CreateRecipientRequest
import com.caregiver.mobile.data.api.RecipientDto
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import retrofit2.HttpException

sealed interface PeopleState {
    data object Loading : PeopleState
    data class Content(val recipients: List<RecipientDto>) : PeopleState
    data object Error : PeopleState
}

sealed interface NameError {
    data object Required : NameError
}

data class AddPersonState(
    val name: String = "",
    val nameError: NameError? = null,
    val addedId: String? = null,
    val busy: Boolean = false,
)

/**
 * Recipients list (board 3) plus the add-person form (missing-page spec).
 * [addedId] is set once the server confirms creation; the screen pops the
 * form on it and clears it via [consumeAdded].
 */
class RecipientsViewModel(
    private val apis: BackendApis,
    private val repository: AuthRepository,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val exec: CoroutineScope? = scope

    private val _state = MutableStateFlow<PeopleState>(PeopleState.Loading)
    val state: StateFlow<PeopleState> = _state

    private val _addState = MutableStateFlow(AddPersonState())
    val addState: StateFlow<AddPersonState> = _addState

    init {
        launch { refresh() }
    }

    fun refresh() {
        launch { load() }
    }

    fun onName(value: String) {
        _addState.value = _addState.value.copy(name = value, nameError = null)
    }

    fun add() {
        val name = _addState.value.name.trim()
        if (name.isEmpty()) {
            _addState.value = _addState.value.copy(nameError = NameError.Required)
            return
        }
        launch {
            _addState.value = _addState.value.copy(busy = true)
            try {
                val created = repository.authorized {
                    apis.recipients().create(CreateRecipientRequest(name))
                }
                _addState.value = _addState.value.copy(busy = false, addedId = created.id)
                load()
            } catch (e: LoggedOutException) {
                _addState.value = _addState.value.copy(busy = false)
            } catch (e: HttpException) {
                _addState.value = _addState.value.copy(busy = false)
                _state.value = PeopleState.Error
            } catch (e: IOException) {
                _addState.value = _addState.value.copy(busy = false, nameError = null)
                _state.value = PeopleState.Error
            } catch (e: CancellationException) {
                _addState.value = _addState.value.copy(busy = false)
                throw e
            }
        }
    }

    fun consumeAdded() {
        _addState.value = _addState.value.copy(addedId = null, name = "")
    }

    private suspend fun load() {
        try {
            val recipients = repository.authorized { apis.recipients().list() }
            _state.value = PeopleState.Content(recipients)
        } catch (e: LoggedOutException) {
            // Root nav flips to login; nothing to show here.
        } catch (e: HttpException) {
            _state.value = PeopleState.Error
        } catch (e: IOException) {
            _state.value = PeopleState.Error
        }
    }

    private fun launch(block: suspend CoroutineScope.() -> Unit) {
        (exec ?: viewModelScope).launch(block = block)
    }
}
