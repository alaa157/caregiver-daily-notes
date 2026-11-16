package com.caregiver.mobile.presentation.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    modifier: Modifier = Modifier,
) {
    val title = stringResource(R.string.account_switch)
    Surface(
        modifier = modifier.fillMaxWidth(),
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
}

@Composable
private fun AccountPickerRow(account: Account, isActive: Boolean, onClick: () -> Unit) {
    val activeLabel = stringResource(R.string.account_active_marker)
    val rowDescription = buildString {
        append(account.displayName)
        append(", ")
        append(account.email)
        if (isActive) {
            append(", ")
            append(activeLabel)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
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
            color = CaregiverColors.Primary,
        )
    }
}
