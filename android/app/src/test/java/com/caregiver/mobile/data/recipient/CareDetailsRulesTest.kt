package com.caregiver.mobile.data.recipient

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CareDetailsRulesTest {
    private val today = LocalDate.of(2026, 6, 1)

    @Test
    fun impossibleDateIsInvalid() {
        val issues = CareDetailsRules.validate(CareDetails(dateOfBirth = "2026-02-30"), today)

        assertEquals(CareDetailsIssue.InvalidDate, issues[CareDetailsField.DateOfBirth])
    }

    @Test
    fun datesBeforeEarliestAndAfterTodayAreInvalid() {
        val beforeEarliest = CareDetailsRules.validate(
            CareDetails(dateOfBirth = "1899-12-31"),
            today,
        )
        val future = CareDetailsRules.validate(
            CareDetails(dateOfBirth = today.plusDays(1).toString()),
            today,
        )

        assertEquals(CareDetailsIssue.InvalidDate, beforeEarliest[CareDetailsField.DateOfBirth])
        assertEquals(CareDetailsIssue.InvalidDate, future[CareDetailsField.DateOfBirth])
    }

    @Test
    fun earliestDateAndTodayAreValid() {
        val earliest = CareDetailsRules.validate(
            CareDetails(dateOfBirth = "1900-01-01"),
            today,
        )
        val current = CareDetailsRules.validate(
            CareDetails(dateOfBirth = today.toString()),
            today,
        )

        assertTrue(earliest.isEmpty())
        assertTrue(current.isEmpty())
    }

    @Test
    fun phoneAcceptsAllowedFormatAndRejectsInvalidOrTooLongValues() {
        val valid = CareDetailsRules.validate(
            CareDetails(emergencyContactPhone = "+20 (100) 123-4567"),
            today,
        )
        val tooShort = CareDetailsRules.validate(
            CareDetails(emergencyContactPhone = "12"),
            today,
        )
        val invalidCharacters = CareDetailsRules.validate(
            CareDetails(emergencyContactPhone = "abc"),
            today,
        )
        val tooLong = CareDetailsRules.validate(
            CareDetails(emergencyContactPhone = "1".repeat(31)),
            today,
        )

        assertTrue(valid.isEmpty())
        assertEquals(CareDetailsIssue.InvalidPhone, tooShort[CareDetailsField.EmergencyContactPhone])
        assertEquals(
            CareDetailsIssue.InvalidPhone,
            invalidCharacters[CareDetailsField.EmergencyContactPhone],
        )
        assertEquals(CareDetailsIssue.InvalidPhone, tooLong[CareDetailsField.EmergencyContactPhone])
    }

    @Test
    fun blankFieldsNormalizeToNull() {
        assertEquals(
            CareDetails(),
            CareDetailsRules.normalize(
                CareDetails(
                    dateOfBirth = "  ",
                    allergies = "\t",
                    conditions = "\n ",
                    emergencyContactName = " ",
                    emergencyContactPhone = " \t ",
                ),
            ),
        )
    }

    @Test
    fun allergyOverLimitIsTooLong() {
        val issues = CareDetailsRules.validate(CareDetails(allergies = "a".repeat(501)), today)

        assertEquals(CareDetailsIssue.TooLong, issues[CareDetailsField.Allergies])
    }

    @Test
    fun textLimitsAreInclusive() {
        val valid = CareDetailsRules.validate(
            CareDetails(
                allergies = "a".repeat(500),
                conditions = "c".repeat(500),
                emergencyContactName = "n".repeat(120),
            ),
            today,
        )
        val invalid = CareDetailsRules.validate(
            CareDetails(
                conditions = "c".repeat(501),
                emergencyContactName = "n".repeat(121),
            ),
            today,
        )

        assertTrue(valid.isEmpty())
        assertEquals(CareDetailsIssue.TooLong, invalid[CareDetailsField.Conditions])
        assertEquals(
            CareDetailsIssue.TooLong,
            invalid[CareDetailsField.EmergencyContactName],
        )
    }

    @Test
    fun normalizedValidInputHasNoIssues() {
        val issues = CareDetailsRules.validate(
            CareDetails(
                dateOfBirth = " 1900-01-01 ",
                allergies = " mild pollen ",
                conditions = " asthma ",
                emergencyContactName = " Aisha ",
                emergencyContactPhone = " +20 (100) 123-4567 ",
            ),
            today,
        )

        assertTrue(issues.isEmpty())
    }
}
