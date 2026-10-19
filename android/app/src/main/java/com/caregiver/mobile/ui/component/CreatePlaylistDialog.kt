/*
 * Caregiver Daily Notes.
 *
 * Ported from Metrolist (https://github.com/MetrolistGroup/Metrolist)
 * ui/component/CreatePlaylistDialog.kt (GPL-3.0): a TextFieldDialog wrapper
 * with the add icon and "Create playlist" title; the entered name is reported
 * back through onPlaylistCreated.
 * Personalization: this app has no YouTube/playlist sync, so Metrolist's
 * sync-with-YouTube switch block (and its sign-in/sync checks and toasts) is
 * dropped — the dialog is kept as the generic "create a named item" pattern.
 */

package com.caregiver.mobile.ui.component

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import com.caregiver.mobile.R

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    initialTextFieldValue: String? = null,
    onPlaylistCreated: ((String) -> Unit)? = null,
) {
    TextFieldDialog(
        icon = { Icon(imageVector = Icons.Filled.Add, contentDescription = null) },
        title = { Text(text = stringResource(R.string.create_playlist)) },
        initialTextFieldValue = TextFieldValue(initialTextFieldValue ?: ""),
        onDismiss = onDismiss,
        onDone = { playlistName ->
            onPlaylistCreated?.invoke(playlistName)
        },
    )
}
