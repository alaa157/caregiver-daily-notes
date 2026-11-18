/*
 * Caregiver Daily Notes.
 *
 * Ported from Metrolist (https://github.com/MetrolistGroup/Metrolist)
 * ui/utils/ShowMediaInfo.kt (GPL-3.0): a copy-to-clipboard details list built
 * from Material3SettingsGroups, with the shimmer loader shown while the
 * content is still loading.
 * Personalization: the YouTube/database/song plumbing is hoisted into a
 * MediaInfoContent model (General and Information row lists plus an optional
 * description); the caller decides what the rows are, icons are optional
 * caller-provided Painters, and passing null keeps Metrolist's loading state.
 */

package com.caregiver.mobile.ui.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.caregiver.mobile.R
import com.caregiver.mobile.ui.component.Material3SettingsGroup
import com.caregiver.mobile.ui.component.Material3SettingsItem
import com.caregiver.mobile.ui.component.shimmer.ShimmerHost
import com.caregiver.mobile.ui.component.shimmer.TextPlaceholder

/** One copyable label/value row rendered by [ShowMediaInfo]. */
data class MediaInfoItem(
    val label: String,
    val value: String?,
    val icon: Painter? = null,
)

/** The data [ShowMediaInfo] renders; null content shows the shimmer loader. */
data class MediaInfoContent(
    val general: List<MediaInfoItem> = emptyList(),
    val information: List<MediaInfoItem> = emptyList(),
    val description: String? = null,
)

@Composable
fun ShowMediaInfo(content: MediaInfoContent?) {
    val windowInsets = WindowInsets.systemBars
    val context = LocalContext.current

    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier
            .padding(
                windowInsets
                    .asPaddingValues()
            )
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (content != null) {
            item(contentType = "MediaDetails") {
                Column {
                    val unknownText = stringResource(R.string.unknown)
                    val clipboardManager =
                        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val copyToClipboard: (String) -> Unit = { displayText ->
                        clipboardManager.setPrimaryClip(ClipData.newPlainText("text", displayText))
                        Toast.makeText(context, R.string.copied, Toast.LENGTH_SHORT).show()
                    }

                    val generalCards = content.general.map { info ->
                        val displayText = info.value ?: unknownText
                        Material3SettingsItem(
                            title = { Text(info.label) },
                            description = { Text(displayText) },
                            icon = info.icon,
                            onClick = { copyToClipboard(displayText) },
                        )
                    }

                    val informationCards = content.information.map { info ->
                        val displayText = info.value ?: unknownText
                        Material3SettingsItem(
                            title = { Text(info.label) },
                            description = { Text(displayText) },
                            icon = info.icon,
                            onClick = { copyToClipboard(displayText) },
                        )
                    }

                    Material3SettingsGroup(
                        title = stringResource(R.string.general),
                        items = generalCards
                    )

                    if (informationCards.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))

                        Material3SettingsGroup(
                            title = stringResource(R.string.information),
                            items = informationCards
                        )
                    }

                    content.description?.let { descriptionText ->
                        Spacer(Modifier.height(8.dp))

                        Material3SettingsGroup(
                            title = stringResource(R.string.description),
                            items = listOf(
                                Material3SettingsItem(
                                    title = { Text(stringResource(R.string.description)) },
                                    description = { Text(descriptionText) },
                                    onClick = { copyToClipboard(descriptionText) }
                                )
                            )
                        )
                    }
                }
            }
        } else {
            item(contentType = "MediaInfoLoader") {
                ShimmerHost {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(all = 16.dp)
                    ) {
                        TextPlaceholder()
                    }
                }
            }
        }
    }
}
