package com.caregiver.mobile.presentation.auth

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.caregiver.mobile.R
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.data.accounts.Account

/**
 * Local account picker (#608). [accounts] must already be in
 * `listAccounts()` order (lastUsedAt descending); the composable does not
 * re-sort. Selection is callback-driven so #594 can own the actual switch.
 */
@Composable
fun AccountPicker(
    accounts: List<Account>,
    activeAccountId: String?,
    onSelect: (Account) -> Unit,
    onAddAccount: () -> Unit,
    onDismiss: () -> Unit,
    onRemoveAccount: (suspend (Account) -> Result<Unit>)? = null,
    modifier: Modifier = Modifier,
) {
    var accountToRemove by remember { mutableStateOf<Account?>(null) }
    // #912: the picker is hosted in a Popup, which — unlike Dialog — does not
    // consume system Back. Claim Back for as long as the picker is composed so
    // only it is dismissed and NavController cannot pop Settings underneath.
    // The owner guard keeps headless JVM tests (no dispatcher) renderable.
    if (LocalOnBackPressedDispatcherOwner.current != null) {
        BackHandler { onDismiss() }
    }
    val title = stringResource(R.string.account_switch)
    Box(modifier = modifier) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 8.dp,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = CaregiverColors.Ink,
            )
            Spacer(Modifier.height(8.dp))
            accounts.forEach { account ->
                val isActive = account.id == activeAccountId
                AccountPickerRow(
                    account = account,
                    isActive = isActive,
                    onRemove = if (onRemoveAccount != null && !isActive) {
                        { accountToRemove = account }
                    } else {
                        null
                    },
                    onClick = {
                        if (isActive) {
                            onDismiss()
                        } else {
                            onSelect(account)
                            onDismiss()
                        }
                    },
                )
            }
            AccountPickerAddRow(onAddAccount)
        }
    }
    val removeAccount = onRemoveAccount
    accountToRemove?.let { target ->
        if (removeAccount != null) {
            RemoveAccountDialog(
                account = target,
                isActive = target.id == activeAccountId,
                onDismiss = { accountToRemove = null },
                onConfirm = removeAccount,
            )
        }
    }
    }
}

@Composable
private fun AccountPickerRow(
    account: Account,
    isActive: Boolean,
    onRemove: (() -> Unit)?,
    onClick: () -> Unit,
) {
    val activeLabel = stringResource(R.string.account_active_marker)
    val currentLabel = stringResource(R.string.account_current_marker)
    val rowDescription = buildString {
        append(account.displayName)
        append(", ")
        append(Bidi.isolate(account.email))
        if (isActive) {
            append(", ")
            append(currentLabel)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("account_picker_row_${account.id}")
            .semantics { contentDescription = rowDescription }
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = CaregiverColors.Ink,
            )
            Text(
                text = Bidi.isolate(account.email),
                style = MaterialTheme.typography.bodySmall,
                color = CaregiverColors.Muted,
            )
        }
        if (isActive) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.testTag("account_active_marker"),
            ) {
                Text(
                    text = activeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        if (!isActive && onRemove != null) {
            val removeLabel = stringResource(R.string.account_remove)
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("account_remove_${account.id}")
                    .semantics { contentDescription = removeLabel },
            ) {
                Icon(
                    imageVector = Icons.Outlined.Delete,
                    contentDescription = null,
                    tint = CaregiverColors.Muted,
                )
            }
        }
    }
}

@Composable
private fun AccountPickerAddRow(onAddAccount: () -> Unit) {
    val label = stringResource(R.string.account_add)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("account_add_row")
            .semantics { contentDescription = label }
            .clickable(onClick = onAddAccount)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
