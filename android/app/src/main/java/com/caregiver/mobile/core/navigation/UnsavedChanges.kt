package com.caregiver.mobile.core.navigation

import androidx.navigation.NavBackStackEntry

/**
 * Detail screens can advertise that leaving would discard edits. The shell
 * reads this on a tab tap and asks before popping the screen, so a tab tap
 * gets the same protection as the top-bar back button and system back.
 */
object UnsavedChanges {
    const val KEY = "has_unsaved_changes"
}

fun NavBackStackEntry.markUnsavedChanges(hasChanges: Boolean) {
    savedStateHandle[UnsavedChanges.KEY] = hasChanges
}

fun NavBackStackEntry.hasUnsavedChanges(): Boolean =
    savedStateHandle.get<Boolean>(UnsavedChanges.KEY) == true
