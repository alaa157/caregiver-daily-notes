package com.caregiver.mobile.data.recipient

import java.time.LocalDate
import java.time.format.DateTimeParseException

data class CareDetails(
    val dateOfBirth: String? = null,
    val allergies: String? = null,
    val conditions: String? = null,
    val emergencyContactName: String? = null,
    val emergencyContactPhone: String? = null,
)

enum class CareDetailsField {
    DateOfBirth,
    Allergies,
    Conditions,
    EmergencyContactName,
    EmergencyContactPhone,
}

enum class CareDetailsIssue { InvalidDate, TooLong, InvalidPhone }

object CareDetailsRules {
    const val ALLERGIES_MAX_LENGTH = 500
    const val CONDITIONS_MAX_LENGTH = 500
    const val EMERGENCY_CONTACT_NAME_MAX_LENGTH = 120

    val PHONE = Regex("^[0-9+()\\- ]{3,30}$")
    val EARLIEST = LocalDate.of(1900, 1, 1)

    fun normalize(details: CareDetails): CareDetails = CareDetails(
        dateOfBirth = details.dateOfBirth.normalizedOrNull(),
        allergies = details.allergies.normalizedOrNull(),
        conditions = details.conditions.normalizedOrNull(),
        emergencyContactName = details.emergencyContactName.normalizedOrNull(),
        emergencyContactPhone = details.emergencyContactPhone.normalizedOrNull(),
    )

    fun validate(details: CareDetails, today: LocalDate): Map<CareDetailsField, CareDetailsIssue> {
        val normalized = normalize(details)
        val issues = mutableMapOf<CareDetailsField, CareDetailsIssue>()

        normalized.dateOfBirth?.let { raw ->
            val date = try {
                LocalDate.parse(raw)
            } catch (_: DateTimeParseException) {
                null
            }
            if (date == null || date.isBefore(EARLIEST) || date.isAfter(today)) {
                issues[CareDetailsField.DateOfBirth] = CareDetailsIssue.InvalidDate
            }
        }

        if ((normalized.allergies?.length ?: 0) > ALLERGIES_MAX_LENGTH) {
            issues[CareDetailsField.Allergies] = CareDetailsIssue.TooLong
        }
        if ((normalized.conditions?.length ?: 0) > CONDITIONS_MAX_LENGTH) {
            issues[CareDetailsField.Conditions] = CareDetailsIssue.TooLong
        }
        if ((normalized.emergencyContactName?.length ?: 0) > EMERGENCY_CONTACT_NAME_MAX_LENGTH) {
            issues[CareDetailsField.EmergencyContactName] = CareDetailsIssue.TooLong
        }
        normalized.emergencyContactPhone?.let { phone ->
            if (!PHONE.matches(phone)) {
                issues[CareDetailsField.EmergencyContactPhone] = CareDetailsIssue.InvalidPhone
            }
        }

        return issues
    }

    private fun String?.normalizedOrNull(): String? =
        this?.trim()?.takeIf(String::isNotEmpty)
}
