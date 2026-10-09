package com.caregiver.mobile.presentation.settings

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.theme.AppSpacing
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.presentation.common.AppTopBar
import com.caregiver.mobile.presentation.common.Pill
import com.caregiver.mobile.presentation.common.SecondaryButton
import java.time.LocalDate
import kotlinx.coroutines.launch

/**
 * Settings (spec screen 12): AppBar, language + account rows, sign-out
 * action. Language applies RTL/LTR immediately via activity recreation.
 * The server-address editor is gone with the offline demo. "Reset demo
 * data" and the "Demo" account marker are DEMO-ONLY chrome (copy owns no
 * demo keys). Sign-out uses action_cancel until the owner supplies a
 * sign-out string (flagged spec gap).
 * States: success; sign-out flips the root gate; reset reseeds Room.
 */
// DEMO-ONLY markers (non-translated). Flagged in the report.
private const val DEMO_BADGE = "Demo"
private const val RESET_DEMO_DATA = "Reset demo data"

@Composable
fun SettingsScreen(graph: AppGraph, navController: NavController) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val activity = context as Activity
    val language by graph.settings.language.collectAsState(initial = SettingsStore.DEFAULT_LANGUAGE)
    var resetting by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = AppSpacing.md).testTag("screen_settings"),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        AppTopBar(title = stringResource(R.string.nav_settings))
        Text(
            text = stringResource(R.string.nav_settings),
            style = MaterialTheme.typography.titleMedium,
            color = CaregiverColors.TextPrimary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            // Language names stay literal: each language must be
            // recognizable no matter which locale is active.
            FilterChip(
                selected = language == "ar",
                onClick = {
                    if (language != "ar") {
                        scope.launch {
                            graph.settings.setLanguage("ar")
                            activity.recreate()
                        }
                    }
                },
                label = { Text("العربية") },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CaregiverColors.Primary,
                    selectedLabelColor = CaregiverColors.Surface,
                ),
            )
            FilterChip(
                selected = language == "en",
                onClick = {
                    if (language != "en") {
                        scope.launch {
                            graph.settings.setLanguage("en")
                            activity.recreate()
                        }
                    }
                },
                label = { Text("English") },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CaregiverColors.Primary,
                    selectedLabelColor = CaregiverColors.Surface,
                ),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            Pill(DEMO_BADGE, CaregiverColors.PrimarySoft, CaregiverColors.Primary)
        }
        SecondaryButton(
            label = RESET_DEMO_DATA,
            onClick = {
                scope.launch {
                    resetting = true
                    try {
                        graph.demoNotes.resetToSeed(LocalDate.now())
                        graph.demoPlans.reset()
                        Toast.makeText(context, RESET_DEMO_DATA, Toast.LENGTH_SHORT).show()
                    } finally {
                        resetting = false
                    }
                }
            },
            enabled = !resetting,
        )
        SecondaryButton(
            label = stringResource(R.string.action_cancel),
            onClick = {
                scope.launch { graph.demoSession.signOut() }
            },
            danger = true,
        )
    }
}
