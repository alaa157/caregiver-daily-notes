/*
 * Caregiver Daily Notes.
 *
 * Ported from Metrolist (https://github.com/MetrolistGroup/Metrolist)
 * ui/component/ExpandableText.kt (GPL-3.0), personalized: the LinkSegment /
 * open-URL feature is dropped on purpose — auto-opening links embedded in
 * care notes or AI summaries is a prompt-injection surface for this app, so
 * the text is plain and only expands/collapses. Uses the app's bilingual
 * "show more"/"show less" strings. NoteRow already self-expands short feed
 * captions, so this is for long free-text bodies (summaries, addendum text).
 */

package com.caregiver.mobile.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.caregiver.mobile.R

@Composable
fun ExpandableText(
    text: String,
    modifier: Modifier = Modifier,
    collapsedMaxLines: Int = 3,
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    var hasOverflow by rememberSaveable { mutableStateOf(false) }
    val bodyColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier.animateContentSize()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(color = bodyColor),
            maxLines = if (isExpanded) Int.MAX_VALUE else collapsedMaxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = { textLayoutResult ->
                hasOverflow =
                    textLayoutResult.hasVisualOverflow || textLayoutResult.lineCount > collapsedMaxLines
            },
            modifier = Modifier.clickable(enabled = hasOverflow) {
                isExpanded = !isExpanded
            },
        )

        if (hasOverflow) {
            Text(
                text = stringResource(
                    if (isExpanded) R.string.note_row_show_less else R.string.note_row_show_more
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { isExpanded = !isExpanded }
            )
        }
    }
}
