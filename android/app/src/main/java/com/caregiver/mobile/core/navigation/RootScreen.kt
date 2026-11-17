package com.caregiver.mobile.core.navigation

/** What the app root shows. */
enum class RootScreen {
    /** A stored value has not arrived yet: nothing is shown. */
    PENDING,

    /** The first-run onboarding. */
    ONBOARDING,

    /** The login, for a build that talks to a backend. */
    LOGIN,

    /** The app itself. */
    APP,
}

/**
 * The screen the app root shows for what has been read so far.
 *
 * [onboardingCompleted] and [signedIn] are null while the stored value has
 * not arrived. The root must not guess then: treating "not read yet" as
 * "the onboarding is not done" flashed the onboarding at every cold start,
 * even for a caregiver who had skipped it long ago.
 */
fun rootScreenFor(
    onboardingCompleted: Boolean?,
    localMode: Boolean,
    signedIn: Boolean?,
): RootScreen = when {
    onboardingCompleted == null -> RootScreen.PENDING
    !onboardingCompleted -> RootScreen.ONBOARDING
    localMode -> RootScreen.APP
    signedIn == null -> RootScreen.PENDING
    signedIn -> RootScreen.APP
    else -> RootScreen.LOGIN
}

/**
 * LOCAL_MODE gate on top of [rootScreenFor]: the workspace needs an active
 * account, so sign-out — which clears it — resolves to Login, not the app.
 * The whole nav host is replaced, so Back cannot re-enter the workspace (#707).
 */
fun localLoginRequired(localMode: Boolean, hasActiveAccount: Boolean): Boolean =
    localMode && !hasActiveAccount
