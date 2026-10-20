package com.caregiver.mobile.presentation.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.ColorPalettes
import com.caregiver.mobile.core.theme.THEME_MODE_DARK
import com.caregiver.mobile.core.theme.THEME_MODE_LIGHT
import com.caregiver.mobile.core.theme.THEME_MODE_SYSTEM
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.data.api.ApiClient
import com.caregiver.mobile.data.api.BaseUrlCheck
import com.caregiver.mobile.data.api.UrlProblem
import com.caregiver.mobile.data.backup.AndroidBackupDocumentIo
import com.caregiver.mobile.data.csv.AndroidCsvDocumentIo
import com.caregiver.mobile.presentation.common.AppTopBar
import com.caregiver.mobile.presentation.common.AppDimens
import com.caregiver.mobile.presentation.common.CaregiverSettingsGroup
import com.caregiver.mobile.presentation.common.CaregiverSettingsItem
import com.caregiver.mobile.presentation.common.PrimaryButton
import com.caregiver.mobile.presentation.common.SecondaryButton
import com.caregiver.mobile.presentation.common.assistedViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(graph: AppGraph, navController: NavController) {
    val scope = rememberCoroutineScope()
    val activity = LocalContext.current as? Activity

    // Data & Backup: the CSV actions read/write only the document the user
    // picks in the system picker, so no storage permission is involved.
    val csvDocuments = remember(graph) { AndroidCsvDocumentIo(graph.context.contentResolver) }
    val csvVm: CsvTransferViewModel = assistedViewModel("csv-transfer") {
        CsvTransferViewModel(graph.csvTransferService, csvDocuments)
    }
    val csvState by csvVm.state.collectAsStateWithLifecycle()
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri -> if (uri != null) csvVm.exportTo(uri) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) csvVm.importFrom(uri) }

    // Data & Backup: full backup/restore of the standalone store. Same SAF
    // rule — one document, no storage permission.
    val backupDocuments = remember(graph) { AndroidBackupDocumentIo(graph.context.contentResolver) }
    val backupVm: BackupRestoreViewModel = assistedViewModel("backup-restore") {
        BackupRestoreViewModel(graph.backupRestoreService, backupDocuments)
    }
    val backupState by backupVm.state.collectAsStateWithLifecycle()
    val backupCreateLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> if (uri != null) backupVm.createTo(uri) }
    val backupRestoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) backupVm.restoreFrom(uri) }

    val language by graph.settings.language.collectAsState(initial = SettingsStore.DEFAULT_LANGUAGE)
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AppTopBar(
            title = stringResource(R.string.settings_title),
            // Settings is a pushed destination (the account pop-up opens it),
            // so the top bar carries the visible way back; the system back
            // button is no longer the only affordance.
            onBack = { navController.popBackStack() },
        )

        // Application-specific: Language
        CaregiverSettingsGroup(title = stringResource(R.string.settings_language)) {
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
        }

        // Application-specific: AI and appearance. Untitled card so these
        // rows carry the same card chrome as the titled sections above and
        // below, instead of sitting outside every group as bare rows.
        CaregiverSettingsGroup {
            CaregiverSettingsItem(
                title = stringResource(R.string.settings_ai),
                icon = Icons.Outlined.SmartToy,
                // No description: it used to repeat the title verbatim.
                onClick = { navController.navigate("ai-settings") },
            )
            CaregiverSettingsItem(
                title = stringResource(R.string.settings_theme_colors),
                icon = Icons.Outlined.Palette,
                onClick = { navController.navigate("theme-colors") },
            )
        }

        // Application-specific: Data & Backup — CSV import/export and full
        // backup/restore. The file pickers grant access to one document each;
        // no broad storage permission is requested. Backup actions need the
        // standalone store; where Room is a server cache they are disabled
        // with an explanation instead of writing into the sync path.
        CaregiverSettingsGroup(title = stringResource(R.string.settings_data_backup)) {
            val dataBusy = csvState.operation != null || backupState.operation != null
            val backupAvailable = backupVm.available
            CaregiverSettingsItem(
                title = stringResource(R.string.csv_import_title),
                icon = Icons.Outlined.UploadFile,
                description = if (csvState.operation == CsvOperation.IMPORT) {
                    stringResource(R.string.csv_import_working)
                } else {
                    stringResource(R.string.csv_import_description)
                },
                enabled = !dataBusy,
                onClick = { importLauncher.launch(CSV_MIME_TYPES) },
            )
            CaregiverSettingsItem(
                title = stringResource(R.string.csv_export_title),
                icon = Icons.Outlined.FileDownload,
                description = if (csvState.operation == CsvOperation.EXPORT) {
                    stringResource(R.string.csv_export_working)
                } else {
                    stringResource(R.string.csv_export_description)
                },
                enabled = !dataBusy,
                onClick = { exportLauncher.launch(csvVm.exportFileName()) },
            )
            CaregiverSettingsItem(
                title = stringResource(R.string.backup_create_title),
                icon = Icons.Outlined.Backup,
                description = when {
                    !backupAvailable -> stringResource(R.string.backup_unavailable)
                    backupState.operation == BackupOperation.CREATE ->
                        stringResource(R.string.backup_create_working)
                    else -> stringResource(R.string.backup_create_description)
                },
                enabled = backupAvailable && !dataBusy,
                onClick = { backupCreateLauncher.launch(backupVm.fileName()) },
            )
            CaregiverSettingsItem(
                title = stringResource(R.string.backup_restore_title),
                icon = Icons.Outlined.SettingsBackupRestore,
                description = when {
                    !backupAvailable -> stringResource(R.string.backup_unavailable)
                    backupState.operation == BackupOperation.RESTORE ->
                        stringResource(R.string.backup_restore_working)
                    else -> stringResource(R.string.backup_restore_description)
                },
                enabled = backupAvailable && !dataBusy,
                onClick = { backupRestoreLauncher.launch(arrayOf("application/json")) },
            )
        }
        csvState.notice?.let { notice ->
            CsvNoticeLine(notice = notice, onDismiss = csvVm::dismissNotice)
        }
        backupState.notice?.let { notice ->
            BackupNoticeLine(notice = notice, onDismiss = backupVm::dismissNotice)
        }

        // Application-specific: Data
        if (com.caregiver.mobile.BuildConfig.LOCAL_MODE) {
            CaregiverSettingsGroup {
                CaregiverSettingsItem(
                    title = stringResource(R.string.settings_load_sample_data),
                    icon = Icons.Outlined.Storage,
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

        // Application-specific: About. The About row keeps the version as its
        // description; the duplicate footer line that used to repeat the same
        // version below the list is gone.
        CaregiverSettingsGroup {
            CaregiverSettingsItem(
                title = stringResource(R.string.settings_about),
                icon = Icons.Outlined.Info,
                description = stringResource(R.string.settings_app_version, com.caregiver.mobile.BuildConfig.VERSION_NAME),
                onClick = { navController.navigate("about") },
            )
        }

        // Application-specific: Server / Logout
        if (!com.caregiver.mobile.BuildConfig.LOCAL_MODE) {
            CaregiverSettingsGroup {
                CaregiverSettingsItem(
                    title = stringResource(R.string.settings_server),
                    icon = Icons.Outlined.Dns,
                    onClick = { navController.navigate("server-url") },
                )
                CaregiverSettingsItem(
                    title = stringResource(R.string.settings_logout),
                    icon = Icons.Outlined.Logout,
                    onClick = { scope.launch { graph.auth.signOut() } },
                )
            }
        }

        csvState.preview?.let { plan ->
            CsvImportDialog(
                plan = plan,
                working = csvState.operation == CsvOperation.IMPORT,
                onResolve = csvVm::resolveUnmatched,
                onCommit = csvVm::commitImport,
                onDismiss = csvVm::cancelImportPreview,
            )
        }

        backupState.preview?.let { preview ->
            RestorePreviewDialog(
                preview = preview,
                working = backupState.operation == BackupOperation.RESTORE,
                onContinue = backupVm::commitRestore,
                onCancel = backupVm::cancelRestorePreview,
            )
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

/**
 * MIME types offered to the import picker. `text/csv` is the real type;
 * `text/comma-separated-values` and `application/vnd.ms-excel` cover files
 * some providers and spreadsheet exports report, and `text/plain` covers
 * CSVs saved with a `.txt` extension.
 */
private val CSV_MIME_TYPES = arrayOf(
    "text/csv",
    "text/comma-separated-values",
    "text/plain",
    "application/vnd.ms-excel",
)