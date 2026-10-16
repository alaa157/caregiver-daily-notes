package com.caregiver.mobile.presentation.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.caregiver.mobile.R
import com.caregiver.mobile.core.theme.CaregiverColors

/**
 * Top-bar end slot (Task 8d): one glyph for the device's sync state.
 * Tapping it asks for another drain pass, so a rejected note ([SyncStatus.FAILED])
 * can be retried right from the bar; the other states are informational.
 */
@Composable
fun SyncStatusIndicator(
    status: SyncStatus,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            // 48-dp target inside the 56-dp bar; only a failed sync reacts.
            .size(AppDimens.BackSize)
            .testTag("sync_status")
            .clickable(enabled = status == SyncStatus.FAILED, onClick = onRetry),
    ) {
        Icon(
            imageVector = when (status) {
                SyncStatus.SYNCED -> Icons.Outlined.CloudDone
                SyncStatus.SYNCING -> Icons.Outlined.Sync
                SyncStatus.OFFLINE -> Icons.Outlined.CloudOff
                SyncStatus.FAILED -> Icons.Outlined.ErrorOutline
            },
            contentDescription = stringResource(
                when (status) {
                    SyncStatus.SYNCED -> R.string.sync_status_synced
                    SyncStatus.SYNCING -> R.string.sync_status_syncing
                    SyncStatus.OFFLINE -> R.string.sync_status_offline
                    SyncStatus.FAILED -> R.string.sync_status_failed
                },
            ),
            tint = when (status) {
                SyncStatus.SYNCED -> CaregiverColors.Muted
                SyncStatus.SYNCING -> CaregiverColors.Primary
                SyncStatus.OFFLINE -> CaregiverColors.Muted
                SyncStatus.FAILED -> CaregiverColors.Danger
            },
            modifier = Modifier.size(AppDimens.IconMd),
        )
    }
}
