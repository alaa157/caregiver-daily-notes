package com.caregiver.mobile.presentation.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.caregiver.mobile.R
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.data.accounts.Account
import com.caregiver.mobile.presentation.common.CaregiverSettingsGroup
import com.caregiver.mobile.presentation.common.CaregiverSettingsRow

/** Active-account section for local Settings (#609). Callback-driven like the picker. */
@Composable
fun AccountSettingsSection(
    account: Account,
    onSwitch: () -> Unit,
    onAdd: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sectionTitle = stringResource(R.string.settings_account)
    val cannotRemove = stringResource(R.string.account_cannot_remove_active)
    CaregiverSettingsGroup(
        title = sectionTitle,
        modifier = modifier.testTag("settings_account_section"),
        items = listOf(
            CaregiverSettingsRow(
                title = account.displayName,
                description = Bidi.isolate(account.email),
                enabled = false,
                onClick = null,
            ),
            CaregiverSettingsRow(
                title = stringResource(R.string.account_switch),
                onClick = onSwitch,
            ),
            CaregiverSettingsRow(
                title = stringResource(R.string.account_add),
                onClick = onAdd,
            ),
            CaregiverSettingsRow(
                title = stringResource(R.string.account_remove),
                description = cannotRemove,
                enabled = false,
                onClick = null,
            ),
        ),
    )
}
