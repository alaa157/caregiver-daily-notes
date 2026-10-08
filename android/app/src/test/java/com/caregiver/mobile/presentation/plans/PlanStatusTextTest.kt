package com.caregiver.mobile.presentation.plans

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanStatusTextTest {

    @Test
    fun knownStatusesMap() {
        assertEquals(
            com.caregiver.mobile.R.string.plan_status_suggested,
            PlanStatusText.res("Suggested"),
        )
        assertEquals(
            com.caregiver.mobile.R.string.plan_status_archived,
            PlanStatusText.res("Archived"),
        )
    }

    @Test
    fun unknownStatusesFallBackToRaw() {
        assertNull(PlanStatusText.res("Some-Future-State"))
    }
}
