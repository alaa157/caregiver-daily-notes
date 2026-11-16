package com.caregiver.mobile.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DialogProperties
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.caregiver.mobile.R
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.data.accounts.Account
import com.caregiver.mobile.data.accounts.AccountError
import com.caregiver.mobile.data.accounts.AccountException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Destructive remove-account confirmation (#611). The dialog names the
 * account, states exactly what is deleted, and keeps itself open with a
 * localized error when [onConfirm] fails. The active account is never
 * removable: [isActive] disables confirm and shows the #609 explanation.
 */
@Composable
fun RemoveAccountDialog(
    account: Account,
    isActive: Boolean,
    onDismiss: () -> Unit,
    onConfirm: suspend (Account) -> Result<Unit>,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var errorRes by remember { mutableStateOf<Int?>(null) }
    var errorArgs by remember { mutableStateOf<Array<Any>>(emptyArray()) }

    fun confirm() {
        if (busy || isActive) return
        busy = true
        errorRes = null
        errorArgs = emptyArray()
        scope.launch {
            val result = try {
                onConfirm(account)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
            if (result.isSuccess) {
                onDismiss()
            } else {
                val failure = result.exceptionOrNull()
                val accountError = (failure as? AccountException)?.error
                if (accountError != null) {
                    val mapped = accountErrorMessage(accountError)
                    errorRes = mapped.first
                    errorArgs = mapped.second
                } else {
                    errorRes = R.string.auth_error_generic
                    errorArgs = emptyArray()
                }
                busy = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        modifier = modifier.testTag("remove_account_dialog"),
        properties = DialogProperties(dismissOnBackPress = true, dismissOnClickOutside = false),
        title = { Text(stringResource(R.string.account_remove_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        R.string.account_remove_dialog_body,
                        account.displayName,
                        Bidi.isolate(account.email),
                    ),
                )
                if (isActive) {
                    Text(
                        stringResource(R.string.account_cannot_remove_active),
                        color = CaregiverColors.Muted,
                    )
                }
                errorRes?.let { res ->
                    Text(
                        stringResource(res, *errorArgs),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { confirm() },
                enabled = !isActive && !busy,
                modifier = Modifier.testTag("remove_account_confirm"),
                colors = ButtonDefaults.buttonColors(containerColor = CaregiverColors.Danger),
            ) {
                Text(stringResource(R.string.account_remove_confirm))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !busy,
                modifier = Modifier.testTag("remove_account_cancel"),
            ) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

private fun accountErrorMessage(error: AccountError): Pair<Int, Array<Any>> = when (error) {
    is AccountError.WeakPassword ->
        passwordProblemRes(error.problem) to passwordProblemArgs(error.problem)
    else -> accountErrorRes(error) to emptyArray()
}
