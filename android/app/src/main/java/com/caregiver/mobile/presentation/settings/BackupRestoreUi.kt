package com.caregiver.mobile.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caregiver.mobile.R
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.data.backup.AndroidBackupDocumentIo
import com.caregiver.mobile.data.backup.BackupFailure
import com.caregiver.mobile.data.backup.BackupFailureReason
import com.caregiver.mobile.data.backup.RestorePreview
import com.caregiver.mobile.ui.component.DefaultDialog
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

/**
 * The result of the last backup action: an exact count on success, a
 * localized, actionable message on failure, and a dismiss button. Shown under
 * the settings group and replaced by the next action's result.
 */
@Composable
fun BackupNoticeLine(notice: BackupNotice, onDismiss: () -> Unit) {
    val text = when (notice) {
        is BackupNotice.Created -> if (notice.counts.total == 0) {
            stringResource(R.string.backup_create_empty)
        } else {
            stringResource(R.string.backup_create_success, notice.counts.total)
        }
        is BackupNotice.Restored -> stringResource(R.string.backup_restore_success, notice.counts.total)
        is BackupNotice.Failed -> when (notice.operation) {
            BackupOperation.CREATE -> stringResource(
                R.string.backup_create_failed,
                backupFailureText(notice.failure),
            )
            BackupOperation.RESTORE -> stringResource(
                R.string.backup_restore_failed,
                backupFailureText(notice.failure),
            )
        }
    }
    val isError = notice is BackupNotice.Failed
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isError) CaregiverColors.DangerContainer else CaregiverColors.SuccessContainer,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) CaregiverColors.Danger else CaregiverColors.Success,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.backup_dismiss))
        }
    }
}

/**
 * Restore confirmation: provenance and per-entity counts of the file, the
 * local data that would be replaced, the replacement warning, and the
 * exclusions notice. Nothing is written until Restore is tapped; Cancel and
 * dismissing leave the database untouched. Note contents are never shown —
 * only counts.
 */
@Composable
fun RestorePreviewDialog(
    preview: RestorePreview,
    working: Boolean,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
) {
    DefaultDialog(
        onDismiss = { if (!working) onCancel() },
        modifier = Modifier.fillMaxWidth(),
        title = { Text(stringResource(R.string.backup_restore_preview_title)) },
        buttons = {
            TextButton(onClick = onCancel, enabled = !working) {
                Text(stringResource(android.R.string.cancel))
            }
            TextButton(onClick = onContinue, enabled = !working) {
                Text(
                    text = stringResource(R.string.backup_restore_confirm),
                    color = CaregiverColors.Danger,
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (working) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.backup_restore_working),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            Text(
                text = stringResource(
                    R.string.backup_preview_created,
                    formatBackupCreatedAt(preview.createdAt),
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.backup_preview_app_version, preview.appVersion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.backup_preview_version, preview.formatVersion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(4.dp))
            BackupPreviewCount(stringResource(R.string.backup_preview_notes, preview.backup.notes), emphasized = true)
            BackupPreviewCount(stringResource(R.string.backup_preview_recipients, preview.backup.recipients))
            BackupPreviewCount(stringResource(R.string.backup_preview_addenda, preview.backup.addenda))
            BackupPreviewCount(stringResource(R.string.backup_preview_plans, preview.backup.plans))

            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.backup_preview_current,
                    preview.current.notes,
                    preview.current.recipients,
                    preview.current.addenda,
                    preview.current.plans,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.backup_preview_warning),
                style = MaterialTheme.typography.bodySmall,
                color = CaregiverColors.Danger,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.backup_preview_exclusions),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BackupPreviewCount(text: String, emphasized: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
    )
}

/** Localized sentence for a fatal backup/restore failure. No file content involved. */
@Composable
internal fun backupFailureText(failure: BackupFailure): String = when (failure.reason) {
    BackupFailureReason.EMPTY_FILE -> stringResource(R.string.backup_error_empty)
    BackupFailureReason.READ_FAILED -> stringResource(R.string.backup_error_read)
    BackupFailureReason.TOO_LARGE -> stringResource(
        R.string.backup_error_too_large,
        AndroidBackupDocumentIo.MAX_IMPORT_MEGABYTES,
    )
    BackupFailureReason.WRITE_FAILED -> stringResource(R.string.backup_error_write)
    BackupFailureReason.UNREADABLE -> stringResource(R.string.backup_error_unreadable)
    BackupFailureReason.NOT_A_BACKUP -> stringResource(R.string.backup_error_not_a_backup)
    BackupFailureReason.UNSUPPORTED_VERSION -> stringResource(
        R.string.backup_error_unsupported_version,
        failure.detail.orEmpty(),
    )
    BackupFailureReason.MISSING_INTEGRITY -> stringResource(R.string.backup_error_integrity)
    BackupFailureReason.UNSUPPORTED_INTEGRITY -> stringResource(R.string.backup_error_integrity_unsupported)
    BackupFailureReason.CHECKSUM_MISMATCH -> stringResource(R.string.backup_error_integrity)
    BackupFailureReason.INVALID_DATA -> stringResource(
        R.string.backup_error_invalid_data,
        failure.issueCount,
    )
    BackupFailureReason.SOURCE_FAILED -> stringResource(R.string.backup_error_source)
    BackupFailureReason.RESTORE_FAILED -> stringResource(R.string.backup_error_restore)
    BackupFailureReason.RESTORE_CONFLICT -> stringResource(R.string.backup_error_conflict)
    BackupFailureReason.UNAVAILABLE -> stringResource(R.string.backup_error_unavailable)
    BackupFailureReason.UNKNOWN -> stringResource(R.string.backup_error_unknown)
}

/**
 * Formats the envelope's ISO-8601 UTC timestamp in the user's locale and time
 * zone; an unparseable value is shown verbatim (the file is already rejected
 * elsewhere by then, this only keeps the dialog total).
 */
private fun formatBackupCreatedAt(createdAt: String): String = try {
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(Instant.parse(createdAt))
} catch (e: DateTimeParseException) {
    createdAt
}
