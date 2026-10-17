package com.caregiver.mobile.ai

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun AiConsentDialog(
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI consent") },
        text = { Text("Care notes are sent to the chosen AI provider. This app is not a medical device and is not HIPAA-compliant.") },
        confirmButton = { TextButton(onClick = onAccept) { Text("I understand") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
