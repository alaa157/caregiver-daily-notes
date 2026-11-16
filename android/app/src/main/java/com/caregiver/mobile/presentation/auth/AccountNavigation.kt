package com.caregiver.mobile.presentation.auth

import androidx.navigation.NavController
import com.caregiver.mobile.core.navigation.AppDestinations

/**
 * Account-scoped navigation resets (#620). Both helpers clear the whole back
 * stack before landing on the documented destination, so Back cannot reopen a
 * screen that still shows the previous account's data.
 */
fun NavController.navigateAfterAccountSignOut() {
    navigateClearingBackStack(AppDestinations.Login.base)
}

fun NavController.navigateAfterAccountSwitch() {
    navigateClearingBackStack(AppDestinations.Home.base)
}

private fun NavController.navigateClearingBackStack(route: String) {
    navigate(route) {
        popUpTo(graph.startDestinationId) { inclusive = true }
    }
}
