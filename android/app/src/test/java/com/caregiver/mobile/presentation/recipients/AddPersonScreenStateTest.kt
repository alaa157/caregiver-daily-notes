package com.caregiver.mobile.presentation.recipients

import org.junit.Assert.assertNotEquals
import org.junit.Test

class AddPersonScreenStateTest {
    @Test
    fun tooLongNameErrorIsDistinctFromRequiredNameError() {
        assertNotEquals(NameError.Required, NameError.TooLong)
    }
}
