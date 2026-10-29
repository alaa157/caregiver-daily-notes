package com.caregiver.mobile.core.util

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import java.util.Locale

/**
 * The locale the UI is actually rendered in.
 *
 * `LocaleHelper` wraps the activity context with the language chosen in the
 * app; the process default (`Locale.getDefault()`) still follows the device.
 * Reading the wrapped context here keeps dates, option labels and number
 * formatting in lockstep with `stringResource` — including in tests that
 * inject a localized `LocalContext`.
 */
@Composable
@ReadOnlyComposable
fun appLocale(): Locale {
    val configuration = LocalContext.current.resources.configuration
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        configuration.locales[0]
    } else {
        @Suppress("DEPRECATION")
        configuration.locale
    }
}

/** True when the app — not the device — is currently in Arabic. */
@Composable
@ReadOnlyComposable
fun isAppArabic(): Boolean = appLocale().language == "ar"
