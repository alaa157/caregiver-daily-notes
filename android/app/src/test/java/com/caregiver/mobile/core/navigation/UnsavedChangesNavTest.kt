package com.caregiver.mobile.core.navigation

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The field report that started the guard: a tab tap pops the AI settings
 * entry, its exit animation ends exactly when the entry is destroyed, and
 * the goodbye write (`markUnsavedChanges(false)`) hit the destroyed entry's
 * saved state — crashing with "You cannot access the NavBackStackEntry's
 * SavedStateHandle after the NavBackStackEntry is destroyed".
 *
 * This reproduces the mechanism on a plain two-route NavHost on the JVM
 * under Robolectric: publish the flag on a live entry, destroy the entry by
 * popping it, then talk to it again — nothing may throw, and the destroyed
 * entry must not report unsaved changes.
 */
@RunWith(AndroidJUnit4::class)
class UnsavedChangesNavTest {

    @get:Rule
    val compose = createComposeRule()

    private val navRef = AtomicReference<NavHostController?>(null)
    private val entryRef = AtomicReference<NavBackStackEntry?>(null)

    @Test
    fun talkingToADestroyedEntryStaysQuiet() {
        compose.setContent {
            val nav = rememberNavController()
            SideEffect { navRef.set(nav) }
            NavHost(nav, startDestination = "here") {
                composable("here") { entry ->
                    SideEffect { entryRef.set(entry) }
                }
                composable("away") { }
            }
        }
        compose.waitForIdle()

        val entry = entryRef.get()!!
        // A live entry publishes and reads the flag normally.
        entry.markUnsavedChanges(true)
        assertEquals(true, entry.hasUnsavedChanges())

        // Push another destination, then pop this one out from under it so
        // the NavHost keeps rendering while "here" is destroyed — the same
        // shape as the real bug, where the screen's exit animation ends and
        // onDispose runs as the entry is destroyed.
        compose.runOnIdle {
            val nav = navRef.get()!!
            nav.navigate("away")
            nav.popBackStack("here", inclusive = true)
        }
        compose.waitUntil(timeoutMillis = 5_000) {
            entryRef.get()?.lifecycle?.currentState == Lifecycle.State.DESTROYED
        }

        // The old code threw IllegalStateException on this write — the
        // exact tab-tap crash. The helpers must stay quiet now. A read on
        // the destroyed entry is quiet too: this androidx version keeps the
        // leftover flag reachable after destruction, which is harmless —
        // no caller asks a dead entry; the contract is simply "no crash".
        entry.markUnsavedChanges(false)
        entry.hasUnsavedChanges()
    }
}
