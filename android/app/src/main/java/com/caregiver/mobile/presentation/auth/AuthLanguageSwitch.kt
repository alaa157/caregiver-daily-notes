package com.caregiver.mobile.presentation.auth

import android.app.Activity
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.core.theme.AppSpacing
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.PlexArabic
import com.caregiver.mobile.data.SettingsStore
import kotlinx.coroutines.launch

/**
 * The auth header's language control: a two-option **EN | عربي** segmented
 * switch (issues #871, #866). It replaces the old literal "English" button
 * whose `onClick` was empty.
 *
 * This composable deliberately reuses the Settings entry point instead of
 * inventing a second language mechanism: it observes
 * [SettingsStore.language][com.caregiver.mobile.data.SettingsStore.language]
 * to show the current choice, and a tap persists the new code through
 * [SettingsStore.setLanguage] and recreates the Activity — exactly the pair
 * Settings, the drawer and onboarding use. Because the language is mirrored
 * synchronously before the recreate (see `LocaleHelper` / `LanguagePrefs`),
 * `MainActivity.attachBaseContext` applies the new locale and RTL direction
 * from the first frame of the rebuilt screen.
 *
 * Typed text survives the switch: `LoginScreen`/`RegisterScreen` own their
 * form state in an `AuthViewModel` created with
 * `androidx.lifecycle.viewmodel.compose.viewModel`, which `ComponentActivity`
 * retains across the configuration change that `recreate()` performs. The
 * switch never owns or resets form state; it only recomposes the header.
 *
 * Layout direction is never hardcoded: `SingleChoiceSegmentedButtonRow` is a
 * `Row`, so the segments flip order under RTL, and
 * [SegmentedButtonDefaults.itemShape] rounds the start/end corners — the
 * active segment keeps its pill shape in both locales.
 */
@Composable
fun AuthLanguageSwitch(
    graph: AppGraph,
    modifier: Modifier = Modifier,
) {
    val language by graph.settings.language.collectAsState(initial = SettingsStore.DEFAULT_LANGUAGE)
    val scope = rememberCoroutineScope()
    // Null-safe like AppDrawer: the switch also composes under headless
    // Compose tests, where LocalContext is not an Activity.
    val activity = LocalContext.current as? Activity

    AuthLanguageSwitchRow(
        language = language,
        onSelect = { code ->
            // Same guard as the Settings chips: tapping the already-active
            // segment does not write or recreate anything.
            if (code != language) {
                scope.launch {
                    graph.settings.setLanguage(code)
                    activity?.recreate()
                }
            }
        },
        modifier = modifier.testTag("auth_language_switch"),
    )
}

/**
 * Stateless rendering of the switch, kept separate so its layout and the
 * selected state can be composed and reasoned about without a graph.
 *
 * Each segment is at least 48dp in both dimensions — the minimum touch
 * target — and carries its own test tag. Labels stay literal (like the
 * Settings language chips) so every option is recognizable under either
 * active locale.
 */
@Composable
internal fun AuthLanguageSwitchRow(
    language: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        LanguageOption(code = "en", label = "EN", tag = "auth_language_en"),
        LanguageOption(code = "ar", label = "عربي", tag = "auth_language_ar"),
    )
    SingleChoiceSegmentedButtonRow(
        modifier = modifier.heightIn(min = MinTouchTarget),
    ) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = language == option.code,
                onClick = { onSelect(option.code) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = CaregiverColors.Primary,
                    activeContentColor = CaregiverColors.Surface,
                    inactiveContainerColor = CaregiverColors.Surface,
                    inactiveContentColor = CaregiverColors.Ink,
                ),
                label = {
                    Text(
                        text = option.label,
                        fontFamily = PlexArabic,
                    )
                },
                modifier = Modifier
                    .heightIn(min = MinTouchTarget)
                    .widthIn(min = MinTouchTarget)
                    .testTag(option.tag),
            )
        }
    }
}

private data class LanguageOption(val code: String, val label: String, val tag: String)

/** 48dp minimum touch target, shared with the back control (`AppDimens.BackSize`). */
private val MinTouchTarget = AppSpacing.xxl
