package com.caregiver.mobile.presentation.plans

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanStatusTextTest {

    @Test
    fun knownStatusesMap() {
        // Spec copy has no per-status strings (flagged as a spec gap); all
        // statuses share the plan disclaimer until Step 5 / owner input.
        assertEquals(
            com.caregiver.mobile.R.string.plan_disclaimer,
            PlanStatusText.res("Suggested"),
        )
        assertEquals(
            com.caregiver.mobile.R.string.plan_disclaimer,
            PlanStatusText.res("Archived"),
        )
    }

    @Test
    fun unknownStatusesFallBackToRaw() {
        assertNull(PlanStatusText.res("Some-Future-State"))
    }
}
