package com.caregiver.mobile.presentation.recipients

import com.caregiver.mobile.data.recipient.CareDetails
import com.caregiver.mobile.data.recipient.CareDetailsField
import org.junit.Assert.assertEquals
import org.junit.Test

class CareDetailLinesTest {
    @Test
    fun onlyNonBlankFieldsProduceLines() {
        val lines = careDetailLines(
            CareDetails(
                dateOfBirth = " 1980-03-04 ",
                allergies = "  ",
                conditions = "Asthma",
                emergencyContactName = null,
                emergencyContactPhone = "+1 555 555 5555",
            ),
        )

        assertEquals(
            listOf(
                CareDetailLine(CareDetailsField.DateOfBirth, "1980-03-04"),
                CareDetailLine(CareDetailsField.Conditions, "Asthma"),
                CareDetailLine(CareDetailsField.EmergencyContactPhone, "+1 555 555 5555"),
            ),
            lines,
        )
    }

    @Test
    fun allEmptyFieldsProduceNoLines() {
        assertEquals(emptyList<CareDetailLine>(), careDetailLines(CareDetails()))
    }
}
