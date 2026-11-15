package com.caregiver.mobile.presentation.recipients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.LoggedOutException
import com.caregiver.mobile.data.api.BackendApis
import com.caregiver.mobile.data.local.ArchivedRecipientException
import com.caregiver.mobile.data.local.RecipientAdmin
import com.caregiver.mobile.data.recipient.NameCheck
import com.caregiver.mobile.data.recipient.RecipientNameRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EditState(
    val loaded: Boolean = false,
    val name: String = "",
    val originalName: String = "",
    val nameError: NameError? = null,
    val duplicate: DuplicateMatch? = null,
    val busy: Boolean = false,
    val failed: Boolean = false,
    val archived: Boolean = false,
    val missing: Boolean = false,
    val savedId: String? = null,
)

class EditRecipientViewModel(
    private val recipientId: String,
    private val apis: BackendApis,
    private val repository: AuthRepository,
    private val admin: RecipientAdmin,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val execScope: CoroutineScope = scope ?: viewModelScope
    private val _state = MutableStateFlow(EditState())
    val state: StateFlow<EditState> = _state.asStateFlow()
    private var duplicateConfirmed = false

    init {
        execScope.launch {
            try {
                val recipient = repository.authorized {
                    apis.recipients().get(recipientId)
                }
                _state.value = EditState(
                    loaded = true,
                    name = recipient.name,
                    originalName = recipient.name,
                    archived = recipient.archivedAt != null,
                )
            } catch (e: LoggedOutException) {
                // Root navigation handles the expired session.
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(missing = true)
            }
        }
    }

    fun onName(value: String) {
        duplicateConfirmed = false
        _state.value = _state.value.copy(
            name = value,
            nameError = null,
            failed = false,
            duplicate = null,
        )
    }

    fun save() {
        val current = _state.value
        if (current.busy || current.archived) return

        val name = RecipientNameRules.normalize(current.name)
        if (name == RecipientNameRules.normalize(current.originalName)) {
            _state.value = current.copy(savedId = recipientId)
            return
        }

        when (RecipientNameRules.check(current.name)) {
            NameCheck.Required -> {
                _state.value = current.copy(nameError = NameError.Required)
                return
            }
            NameCheck.TooLong -> {
                _state.value = current.copy(nameError = NameError.TooLong)
                return
            }
            NameCheck.Ok -> Unit
        }

        _state.value = current.copy(
            nameError = null,
            duplicate = null,
            busy = true,
            failed = false,
        )
        execScope.launch {
            try {
                if (!duplicateConfirmed) {
                    val recipients = repository.authorized { apis.recipients().list() }
                    val duplicate = RecipientNameRules.findDuplicate(
                        name,
                        recipients,
                        excludeId = recipientId,
                    )
                    if (duplicate != null) {
                        _state.value = _state.value.copy(
                            duplicate = DuplicateMatch(
                                id = duplicate.id,
                                name = duplicate.name,
                                archived = duplicate.archivedAt != null,
                            ),
                        )
                        return@launch
                    }
                }
                val saved = admin.rename(recipientId, name)
                _state.value = _state.value.copy(savedId = saved.id)
            } catch (e: ArchivedRecipientException) {
                _state.value = _state.value.copy(archived = true)
            } catch (e: NoSuchElementException) {
                _state.value = _state.value.copy(missing = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(failed = true)
            } finally {
                _state.value = _state.value.copy(busy = false)
            }
        }
    }

    fun saveAnyway() {
        duplicateConfirmed = true
        _state.value = _state.value.copy(duplicate = null)
        save()
    }

    fun dismissDuplicate() {
        _state.value = _state.value.copy(duplicate = null)
    }
}
