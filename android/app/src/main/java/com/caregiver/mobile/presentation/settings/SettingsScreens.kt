package com.caregiver.mobile.presentation.settings

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.PlexArabic
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.api.ApiClient
import com.caregiver.mobile.data.api.BaseUrlCheck
import com.caregiver.mobile.data.api.UrlProblem
import com.caregiver.mobile.presentation.common.AppTopBar
import com.caregiver.mobile.presentation.common.AppDimens
import com.caregiver.mobile.presentation.common.PrimaryButton
import com.caregiver.mobile.presentation.common.SecondaryButton
import com.caregiver.mobile.ui.component.SettingsGroup
import kotlinx.coroutines.launch

/**
 * Settings (missing page): server address, language, sign-out. Language
 * persists immediately and the activity recreates to apply it; the notice
 * covers process-level resources that need a full restart.
 */
@Composable
fun SettingsScreen(graph: AppGraph, navController: NavController) {
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as Activity
    val language by graph.settings.language.collectAsState(initial = SettingsStore.DEFAULT_LANGUAGE)
    val themeMode by graph.settings.themeMode.collectAsState(initial = SettingsStore.DEFAULT_THEME_MODE)
    val colorPalette by graph.settings.colorPalette.collectAsState(initial = SettingsStore.DEFAULT_COLOR_PALETTE)
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppTopBar(title = stringResource(R.string.settings_title))

        SettingsGroup(title = stringResource(R.string.settings_appearance)) {
            Text(
                text = stringResource(R.string.settings_mode),
                style = MaterialTheme.typography.labelMedium,
                color = CaregiverColors.Muted,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf("light", "dark", "system").forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = themeMode == mode,
                        onClick = { scope.launch { graph.settings.setThemeMode(mode) } },
                        shape = SegmentedButtonDefaults.itemShape(index, 3),
                    ) {
                        Text(mode.replaceFirstChar { it.uppercase() })
                    }
                }
            }
            Text(
                text = stringResource(R.string.settings_accent),
                style = MaterialTheme.typography.labelMedium,
                color = CaregiverColors.Muted,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("metrolist", "teal", "indigo", "rose").forEach { pal ->
                    FilterChip(
                        selected = colorPalette == pal,
                        onClick = { scope.launch { graph.settings.setColorPalette(pal) } },
                        label = { Text(pal.replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
        }

        SettingsGroup(title = stringResource(R.string.settings_language)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                )
            }
            Text(
                text = stringResource(R.string.settings_language_restart),
                fontFamily = PlexArabic,
                style = MaterialTheme.typography.bodySmall,
                color = CaregiverColors.Muted,
            )
        }

        SettingsGroup(title = stringResource(R.string.settings_ai)) {
            SecondaryButton(
                label = stringResource(R.string.settings_ai),
                onClick = { navController.navigate("ai-settings") },
            )
        }

        if (com.caregiver.mobile.BuildConfig.LOCAL_MODE) {
            SettingsGroup(title = stringResource(R.string.settings_data)) {
                SecondaryButton(
                    label = stringResource(R.string.settings_load_sample_data),
                    onClick = {
                        scope.launch {
                            com.caregiver.mobile.data.SampleDataLoader.load(
                                com.caregiver.mobile.data.local.AppDatabase.create(graph.context),
                            )
                        }
                    },
                )
            }
        }

        SettingsGroup(title = stringResource(R.string.settings_about)) {
            Text(
                text = stringResource(R.string.settings_app_version, com.caregiver.mobile.BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodyMedium,
            )
            SecondaryButton(
                label = stringResource(R.string.settings_about),
                onClick = { navController.navigate("about") },
            )
        }

        if (!com.caregiver.mobile.BuildConfig.LOCAL_MODE) {
            SettingsGroup(title = stringResource(R.string.settings_server_section)) {
                SecondaryButton(
                    label = stringResource(R.string.settings_server),
                    onClick = { navController.navigate("server-url") },
                )
                SecondaryButton(
                    label = stringResource(R.string.settings_logout),
                    onClick = {
                        scope.launch { graph.auth.signOut() }
                    },
                    danger = true,
                )
            }
        }
    }
}

/**
 * Server address editor (missing page), reachable pre-auth from login and
 * post-auth from settings. The address validates before persisting; the
 * provider rebuilds on the next call, so no restart is needed.
 */
@Composable
fun ServerUrlScreen(graph: AppGraph, navController: NavController) {
    val scope = rememberCoroutineScope()
    val current by graph.settings.baseUrl.collectAsState(
        initial = SettingsStore.DEFAULT_BASE_URL,
    )
    var field by rememberSaveable(current) { mutableStateOf(current) }
    // Saveable (not just remembered): rotation must not eat the invalid-URL
    // line or the save/reset confirmation.
    var notice by rememberSaveable { mutableStateOf<Int?>(null) }
    var invalid by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppTopBar(
            title = stringResource(R.string.settings_server),
            onBack = { navController.popBackStack() },
        )
        OutlinedTextField(
            value = field,
            onValueChange = { field = it; invalid = false; notice = null },
            label = { Text(stringResource(R.string.settings_server)) },
            isError = invalid,
            supportingText = {
                if (invalid) {
                    Text(stringResource(R.string.settings_url_invalid))
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(AppDimens.ControlRadius),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CaregiverColors.Primary,
                unfocusedBorderColor = CaregiverColors.Border,
                focusedContainerColor = CaregiverColors.Surface,
                unfocusedContainerColor = CaregiverColors.Surface,
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = AppDimens.InputHeight),
        )
        PrimaryButton(
            label = stringResource(R.string.settings_server_save),
            onClick = {
                when (val check = ApiClient.checkBaseUrl(field)) {
                    is BaseUrlCheck.Valid -> {
                        scope.launch {
                            graph.settings.setBaseUrl(field.trim())
                            notice = R.string.settings_server_saved
                        }
                    }
                    is BaseUrlCheck.Invalid -> {
                        invalid = true
                        notice = if (check.problem == UrlProblem.HttpNotAllowed) {
                            R.string.settings_url_http_local
                        } else {
                            null
                        }
                    }
                }
            },
        )
        SecondaryButton(
            label = stringResource(R.string.settings_server_reset),
            onClick = {
                scope.launch {
                    graph.settings.resetBaseUrl()
                    field = SettingsStore.DEFAULT_BASE_URL
                    invalid = false
                    notice = R.string.settings_server_reset_done
                }
            },
        )
        notice?.let {
            Spacer(Modifier.height(8.dp))
            Text(text = stringResource(it))
        }
    }
}
