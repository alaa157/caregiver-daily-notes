package com.caregiver.mobile.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.caregiver.mobile.R
import com.caregiver.mobile.core.theme.CaregiverColors

/**
 * About section: app version, description, credits, links. No music content.
 */
@Composable
fun AboutScreen(onBack: (() -> Unit)? = null) {
    val uriHandler = LocalUriHandler.current
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Caregiver Daily Notes",
            style = MaterialTheme.typography.headlineSmall,
            color = CaregiverColors.Ink,
        )
        Text(
            text = "Version " + com.caregiver.mobile.BuildConfig.VERSION_NAME,
            style = MaterialTheme.typography.bodySmall,
            color = CaregiverColors.Muted,
        )
        Text(
            text = "A local-first companion for caregivers to record daily notes, track recipients, and review history. Inspired by modern calm clinical design.",
            style = MaterialTheme.typography.bodyMedium,
            color = CaregiverColors.Ink,
        )
        Text(
            text = "Credits: Caregiver team.",
            style = MaterialTheme.typography.bodySmall,
            color = CaregiverColors.Muted,
        )
        TextButton(onClick = { uriHandler.openUri("https://github.com") }) {
            Text(stringResource(R.string.about_project_link))
        }
    }
}