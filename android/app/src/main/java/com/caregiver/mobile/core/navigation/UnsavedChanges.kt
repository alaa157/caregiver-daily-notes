package com.caregiver.mobile.core.navigation

import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry

/**
 * Detail screens can advertise that leaving would discard edits. The shell
 * reads this on a tab tap and asks before popping the screen, so a tab tap
 * gets the same protection as the top-bar back button and system back.
 */
object UnsavedChanges {
    const val KEY = "has_unsaved_changes"
}

/**
 * The entry is destroyed the moment its exit animation finishes, which is
 * exactly when the screen says goodbye to the flag. Reading or writing a
 * destroyed entry's saved state throws, so these helpers stay quiet instead
 * of crashing the app on a tab tap (the destroyed entry's flag is gone with
 * it — there is nothing left to clear or ask about).
 */
fun NavBackStackEntry.markUnsavedChanges(hasChanges: Boolean) {
    if (lifecycle.currentState == Lifecycle.State.DESTROYED) return
    try {
        savedStateHandle[UnsavedChanges.KEY] = hasChanges
    } catch (_: IllegalStateException) {
        // Entry was destroyed between the check and the write.
    }
}

fun NavBackStackEntry.hasUnsavedChanges(): Boolean =
    try {
        savedStateHandle.get<Boolean>(UnsavedChanges.KEY) == true
    } catch (_: IllegalStateException) {
        false
    }
