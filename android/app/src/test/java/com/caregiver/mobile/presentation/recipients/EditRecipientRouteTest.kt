package com.caregiver.mobile.presentation.recipients

import com.caregiver.mobile.core.navigation.AppDestinations
import com.caregiver.mobile.core.navigation.AppRoutes
import org.junit.Assert.assertEquals
import org.junit.Test

class EditRecipientRouteTest {
    @Test
    fun editRouteMatchesItsRegisteredDestination() {
        assertEquals(
            AppDestinations.EditRecipient.base.replace("{recipientId}", "abc"),
            AppRoutes.editRecipient("abc"),
        )
    }
}
