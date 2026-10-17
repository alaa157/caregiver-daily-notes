package com.caregiver.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.data.SettingsStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as CaregiverApp).graph
        setContent {
            val themeMode by graph.settings.themeMode.collectAsState(initial = SettingsStore.DEFAULT_THEME_MODE)
            val colorPalette by graph.settings.colorPalette.collectAsState(initial = SettingsStore.DEFAULT_COLOR_PALETTE)
            val dark = when (themeMode) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            CaregiverTheme(darkTheme = dark, palette = colorPalette) {
                CaregiverRoot(graph)
            }
        }
    }
}
