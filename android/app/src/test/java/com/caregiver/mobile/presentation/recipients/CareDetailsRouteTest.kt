package com.caregiver.mobile.presentation.recipients

import com.caregiver.mobile.core.navigation.AppDestinations
import com.caregiver.mobile.core.navigation.AppRoutes
import org.junit.Assert.assertEquals
import org.junit.Test

class CareDetailsRouteTest {
    @Test
    fun routeBuilderMatchesRegisteredCareDetailsDestination() {
        assertEquals(
            AppDestinations.CareDetails.base.replace("{recipientId}", "abc"),
            AppRoutes.careDetails("abc"),
        )
    }
}
