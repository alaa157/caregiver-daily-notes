package com.caregiver.mobile.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The root gate must not guess what it has not read yet.
 *
 * The onboarding state is read from DataStore, so it arrives after the first
 * frame. Treating "not read yet" as "the onboarding is not done" flashed the
 * onboarding at every cold start, even for a caregiver who had skipped it
 * long ago.
 */
class RootScreenTest {

    @Test
    fun nothingIsShownBeforeTheStoredStateArrives() {
        assertEquals(RootScreen.PENDING, rootScreenFor(null, localMode = true, signedIn = null))
        assertEquals(RootScreen.PENDING, rootScreenFor(null, localMode = false, signedIn = null))
    }

    @Test
    fun aCompletedOnboardingGoesStraightToTheApp() {
        // The standalone build never signs in, so the token is irrelevant.
        assertEquals(RootScreen.APP, rootScreenFor(true, localMode = true, signedIn = null))
        assertEquals(RootScreen.APP, rootScreenFor(true, localMode = false, signedIn = true))
    }

    @Test
    fun anUnfinishedOnboardingShowsTheOnboarding() {
        assertEquals(RootScreen.ONBOARDING, rootScreenFor(false, localMode = true, signedIn = true))
        assertEquals(RootScreen.ONBOARDING, rootScreenFor(false, localMode = false, signedIn = true))
    }

    @Test
    fun theLoginIsShownOnlyWhenTheTokenIsKnownAndMissing() {
        // The token is read too, so the same rule applies to it: a value
        // that has not arrived is not "not signed in".
        assertEquals(RootScreen.PENDING, rootScreenFor(true, localMode = false, signedIn = null))
        assertEquals(RootScreen.LOGIN, rootScreenFor(true, localMode = false, signedIn = false))
        assertEquals(RootScreen.APP, rootScreenFor(true, localMode = false, signedIn = true))
    }
}
