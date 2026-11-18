package com.caregiver.mobile.presentation.auth

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.caregiver.mobile.core.navigation.AppDestinations
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Back-stack reset coverage for account sign-out/switch (#620). */
@RunWith(AndroidJUnit4::class)
class AccountNavigationTest {

    @get:Rule
    val compose = createComposeRule()

    private val navRef = AtomicReference<NavHostController?>(null)

    private fun renderNavHost() {
        compose.setContent {
            val nav = rememberNavController()
            SideEffect { navRef.set(nav) }
            NavHost(nav, startDestination = AppDestinations.Home.base) {
                composable(AppDestinations.Home.base) {}
                composable(AppDestinations.Login.base) {}
                composable("detail") {}
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun signOutClearsBackStackAndLandsOnLogin() {
        renderNavHost()
        compose.runOnIdle {
            val nav = navRef.get()!!
            nav.navigate("detail")
            nav.navigateAfterAccountSignOut()
        }
        compose.waitForIdle()

        val nav = navRef.get()!!
        assertEquals(AppDestinations.Login.base, nav.currentDestination?.route)
        assertNull(nav.previousBackStackEntry)
    }

    @Test
    fun switchClearsBackStackAndLandsOnHome() {
        renderNavHost()
        compose.runOnIdle {
            val nav = navRef.get()!!
            nav.navigate("detail")
            nav.navigateAfterAccountSwitch()
        }
        compose.waitForIdle()

        val nav = navRef.get()!!
        assertEquals(AppDestinations.Home.base, nav.currentDestination?.route)
        assertNull(nav.previousBackStackEntry)
    }
}
