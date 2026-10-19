package com.caregiver.mobile

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.core.theme.resolveDarkTheme
import com.caregiver.mobile.core.util.LocaleHelper
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.ui.component.shimmer.ShimmerTheme
import com.valentinilk.shimmer.LocalShimmerTheme

class MainActivity : ComponentActivity() {

    /**
     * Apply the stored language to this activity's resources before the first
     * frame. `CaregiverApp` seeds [LocaleHelper]'s cache from the settings
     * store at process start and `SettingsStore.setLanguage` refreshes it, so
     * the recreation the language chips trigger lands here with the new
     * language: visible text, RTL direction, and every localized resource
     * follow the selection without a flash of the old language.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(
            LocaleHelper.wrap(newBase, LocaleHelper.cachedOr(SettingsStore.DEFAULT_LANGUAGE)),
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as CaregiverApp).graph
        setContent {
            val themeMode by graph.settings.themeMode.collectAsState(initial = SettingsStore.DEFAULT_THEME_MODE)
            val colorPalette by graph.settings.colorPalette.collectAsState(initial = SettingsStore.DEFAULT_COLOR_PALETTE)
            val pureBlack by graph.settings.pureBlack.collectAsState(initial = false)
            val dark = resolveDarkTheme(themeMode, systemInDark = isSystemInDarkTheme())
            CaregiverTheme(darkTheme = dark, palette = colorPalette, pureBlack = pureBlack) {
                // One shimmer theme for every skeleton in the app; Metrolist
                // distributes its ShimmerTheme from MainActivity the same way.
                CompositionLocalProvider(LocalShimmerTheme provides ShimmerTheme) {
                    CaregiverRoot(graph)
                }
            }
        }
    }
}
