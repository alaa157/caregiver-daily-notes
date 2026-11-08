package com.caregiver.mobile.ai

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.caregiver.mobile.R

@Composable
fun AiConsentDialog(
    /** Named in the body so consent always refers to a specific provider. */
    providerName: String,
    /** Named too: the caregiver sees exactly which model notes would go to. */
    modelName: String? = null,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_consent_title)) },
        text = {
            Text(
                if (modelName != null) {
                    stringResource(R.string.ai_consent_body_model, providerName, modelName)
                } else {
                    stringResource(R.string.ai_consent_body, providerName)
                },
            )
        },
        confirmButton = { TextButton(onClick = onAccept) { Text(stringResource(R.string.ai_consent_accept)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
