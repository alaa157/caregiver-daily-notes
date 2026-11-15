package com.caregiver.mobile.presentation.recipients

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.data.AuthRepository
import com.caregiver.mobile.data.LoggedOutException
import com.caregiver.mobile.data.api.BackendApis
import com.caregiver.mobile.data.local.RecipientAdmin
import com.caregiver.mobile.data.recipient.CareDetails
import com.caregiver.mobile.data.recipient.CareDetailsField
import com.caregiver.mobile.data.recipient.CareDetailsIssue
import com.caregiver.mobile.data.recipient.CareDetailsRules
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CareDetailsState(
    val loaded: Boolean = false,
    val dateOfBirth: String = "",
    val allergies: String = "",
    val conditions: String = "",
    val emergencyContactName: String = "",
    val emergencyContactPhone: String = "",
    val errors: Map<CareDetailsField, CareDetailsIssue> = emptyMap(),
    val busy: Boolean = false,
    val failed: Boolean = false,
    val archived: Boolean = false,
    val missing: Boolean = false,
    val saved: Boolean = false,
)

class CareDetailsViewModel(
    private val recipientId: String,
    private val apis: BackendApis,
    private val repository: AuthRepository,
    private val admin: RecipientAdmin,
    scope: CoroutineScope? = null,
) : ViewModel() {
    private val execScope: CoroutineScope = scope ?: viewModelScope
    private val _state = MutableStateFlow(CareDetailsState())
    val state: StateFlow<CareDetailsState> = _state.asStateFlow()

    init {
        execScope.launch {
            try {
                val recipient = repository.authorized {
                    apis.recipients().get(recipientId)
                }
                _state.value = CareDetailsState(
                    loaded = true,
                    dateOfBirth = recipient.dateOfBirth.orEmpty(),
                    allergies = recipient.allergies.orEmpty(),
                    conditions = recipient.conditions.orEmpty(),
                    emergencyContactName = recipient.emergencyContactName.orEmpty(),
                    emergencyContactPhone = recipient.emergencyContactPhone.orEmpty(),
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

    fun onDateOfBirth(value: String) {
        val current = _state.value
        _state.value = current.copy(
            dateOfBirth = value,
            errors = current.errors - CareDetailsField.DateOfBirth,
            failed = false,
            saved = false,
        )
    }

    fun onAllergies(value: String) {
        val current = _state.value
        _state.value = current.copy(
            allergies = value,
            errors = current.errors - CareDetailsField.Allergies,
            failed = false,
            saved = false,
        )
    }

    fun onConditions(value: String) {
        val current = _state.value
        _state.value = current.copy(
            conditions = value,
            errors = current.errors - CareDetailsField.Conditions,
            failed = false,
            saved = false,
        )
    }

    fun onEmergencyContactName(value: String) {
        val current = _state.value
        _state.value = current.copy(
            emergencyContactName = value,
            errors = current.errors - CareDetailsField.EmergencyContactName,
            failed = false,
            saved = false,
        )
    }

    fun onEmergencyContactPhone(value: String) {
        val current = _state.value
        _state.value = current.copy(
            emergencyContactPhone = value,
            errors = current.errors - CareDetailsField.EmergencyContactPhone,
            failed = false,
            saved = false,
        )
    }

    fun save() {
        val current = _state.value
        if (current.busy || current.archived) return

        val details = CareDetailsRules.normalize(
            CareDetails(
                dateOfBirth = current.dateOfBirth,
                allergies = current.allergies,
                conditions = current.conditions,
                emergencyContactName = current.emergencyContactName,
                emergencyContactPhone = current.emergencyContactPhone,
            ),
        )
        val errors = CareDetailsRules.validate(details, LocalDate.now())
        if (errors.isNotEmpty()) {
            _state.value = current.copy(errors = errors, failed = false, saved = false)
            return
        }

        _state.value = current.copy(
            dateOfBirth = details.dateOfBirth.orEmpty(),
            allergies = details.allergies.orEmpty(),
            conditions = details.conditions.orEmpty(),
            emergencyContactName = details.emergencyContactName.orEmpty(),
            emergencyContactPhone = details.emergencyContactPhone.orEmpty(),
            errors = emptyMap(),
            busy = true,
            failed = false,
            saved = false,
        )
        execScope.launch {
            try {
                admin.updateCareDetails(recipientId, details)
                _state.value = _state.value.copy(busy = false, saved = true)
            } catch (e: LoggedOutException) {
                _state.value = _state.value.copy(busy = false)
            } catch (e: CancellationException) {
                _state.value = _state.value.copy(busy = false)
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(busy = false, failed = true)
            }
        }
    }
}
