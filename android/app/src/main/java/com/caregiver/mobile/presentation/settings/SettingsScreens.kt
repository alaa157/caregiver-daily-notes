package com.caregiver.mobile.presentation.settings

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.Update
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
import com.caregiver.mobile.core.theme.ColorPalettes
import com.caregiver.mobile.core.theme.PlexArabic
import com.caregiver.mobile.core.theme.THEME_MODE_DARK
import com.caregiver.mobile.core.theme.THEME_MODE_LIGHT
import com.caregiver.mobile.core.theme.THEME_MODE_SYSTEM
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.api.ApiClient
import com.caregiver.mobile.data.api.BaseUrlCheck
import com.caregiver.mobile.data.api.UrlProblem
import com.caregiver.mobile.presentation.common.AppTopBar
import com.caregiver.mobile.presentation.common.AppDimens
import com.caregiver.mobile.presentation.common.PrimaryButton
import com.caregiver.mobile.presentation.common.SecondaryButton
import com.caregiver.mobile.ui.component.SettingsGroup
import com.caregiver.mobile.ui.component.SettingsTile
import kotlinx.coroutines.launch

/**
 * Settings (missing page): server address, language, sign-out. Language
 * persists immediately and the activity recreates to apply it; the notice
 * covers process-level resources that need a full restart.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(graph: AppGraph, navController: NavController) {
    val scope = rememberCoroutineScope()
    // Null-safe like the drawer: the screen also composes under tests and
    // previews, where LocalContext is not necessarily an Activity.
    val activity = LocalContext.current as? Activity
    val language by graph.settings.language.collectAsState(initial = SettingsStore.DEFAULT_LANGUAGE)
    val themeMode by graph.settings.themeMode.collectAsState(initial = SettingsStore.DEFAULT_THEME_MODE)
    val colorPalette by graph.settings.colorPalette.collectAsState(initial = SettingsStore.DEFAULT_COLOR_PALETTE)
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppTopBar(title = stringResource(R.string.settings_title))

        // Section 1: Interface
        SectionHeader(stringResource(R.string.section_interface))
        SettingsTile(title = stringResource(R.string.item_zero), icon = Icons.Outlined.Circle, onClick = {}, enabled = false)

        // Section 2: Privacy & Security
        SectionHeader(stringResource(R.string.section_privacy))
        SettingsTile(title = stringResource(R.string.item_privacy), icon = Icons.Outlined.Lock, onClick = {}, enabled = false)

        // Section 3: Storage & Data
        SectionHeader(stringResource(R.string.section_storage))
        SettingsTile(title = stringResource(R.string.item_storage), icon = Icons.Outlined.Storage, onClick = {}, enabled = false)
        SettingsTile(title = stringResource(R.string.item_backup_restore), icon = Icons.Outlined.Backup, onClick = {}, enabled = false)

        // Section 4: System & About
        SectionHeader(stringResource(R.string.section_system))
        SettingsTile(title = stringResource(R.string.item_open_supported_links), icon = Icons.Outlined.OpenInNew, onClick = {}, enabled = false)
        SettingsTile(title = stringResource(R.string.item_updater), icon = Icons.Outlined.SystemUpdate, onClick = {}, enabled = false)
        SettingsTile(title = stringResource(R.string.item_changelog), icon = Icons.Outlined.History, onClick = {}, enabled = false)
        SettingsTile(title = stringResource(R.string.settings_about), icon = Icons.Outlined.Info, onClick = { navController.navigate("about") })
        SettingsTile(title = stringResource(R.string.item_check_new_version), icon = Icons.Outlined.Update, onClick = {}, enabled = false)

        // Application-specific section
        SectionHeader(stringResource(R.string.section_application))
        SettingsGroup(title = stringResource(R.string.settings_appearance)) {
            Text(
                text = stringResource(R.string.settings_mode),
                style = MaterialTheme.typography.labelMedium,
                color = CaregiverColors.Muted,
            )
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                listOf(
                    THEME_MODE_LIGHT to R.string.settings_mode_light,
                    THEME_MODE_DARK to R.string.settings_mode_dark,
                    THEME_MODE_SYSTEM to R.string.settings_mode_system,
                ).forEachIndexed { index, (mode, label) ->
                    SegmentedButton(
                        selected = themeMode == mode,
                        onClick = { scope.launch { graph.settings.setThemeMode(mode) } },
                        shape = SegmentedButtonDefaults.itemShape(index, 3),
                    ) {
                        Text(stringResource(label))
                    }
                }
            }
            Text(
                text = stringResource(R.string.settings_accent),
                style = MaterialTheme.typography.labelMedium,
                color = CaregiverColors.Muted,
            )
            // FlowRow, not Row: four chips at larger font scales exceed the
            // screen width on narrow devices, and a clipped palette chip is
            // an unreachable setting. Wrapping keeps every accent tappable.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(
                    ColorPalettes.METROLIST to R.string.settings_palette_metrolist,
                    ColorPalettes.TEAL to R.string.settings_palette_teal,
                    ColorPalettes.INDIGO to R.string.settings_palette_indigo,
                    ColorPalettes.ROSE to R.string.settings_palette_rose,
                ).forEach { (pal, label) ->
                    FilterChip(
                        selected = colorPalette == pal,
                        onClick = { scope.launch { graph.settings.setColorPalette(pal) } },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        }

        SettingsGroup(title = stringResource(R.string.settings_language)) {
            // Same wrap rule as the accents: side by side at normal sizes,
            // stacked instead of clipped at large font scales.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = language == "ar",
                    onClick = {
                        if (language != "ar") {
                            scope.launch {
                                graph.settings.setLanguage("ar")
                                activity?.recreate()
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
                                activity?.recreate()
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

        Text(
            text = stringResource(R.string.settings_app_version, com.caregiver.mobile.BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
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

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
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
