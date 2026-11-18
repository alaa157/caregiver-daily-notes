package com.caregiver.mobile.ai

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.caregiver.mobile.R

/** The data-use notice a provider/model pair requires, as a string resource. */
@StringRes
fun AiDataUseNotice.stringRes(): Int = when (this) {
    AiDataUseNotice.KEY_TIER -> R.string.ai_data_use_gemini
    AiDataUseNotice.FREE_HOST -> R.string.ai_data_use_free_host
}

@Composable
fun AiConsentDialog(
    /** Named in the body so consent always refers to a specific provider. */
    providerName: String,
    /** Named too: the caregiver sees exactly which model notes would go to. */
    modelName: String? = null,
    /** Extra data-use paragraph (key tier / free host), when one applies. */
    @StringRes dataUseNoticeRes: Int? = null,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_consent_title)) },
        text = {
            Column {
                Text(
                    if (modelName != null) {
                        stringResource(R.string.ai_consent_body_model, providerName, modelName)
                    } else {
                        stringResource(R.string.ai_consent_body, providerName)
                    },
                )
                if (dataUseNoticeRes != null) {
                    Text(
                        text = stringResource(dataUseNoticeRes),
                        modifier = Modifier.testTag("consent_data_use_notice"),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onAccept) { Text(stringResource(R.string.ai_consent_accept)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}
