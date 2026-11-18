package com.caregiver.mobile.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.caregiver.mobile.R
import com.caregiver.mobile.ai.AiFreeTier
import com.caregiver.mobile.ai.AiModelCatalog
import com.caregiver.mobile.ai.AiModelOption
import com.caregiver.mobile.ai.AiProvider
import com.caregiver.mobile.ai.dataUseNotice
import com.caregiver.mobile.ai.models.ModelSource
import com.caregiver.mobile.ai.stringRes
import com.caregiver.mobile.core.i18n.Bidi

/**
 * Repository-free content of the AI model picker sheet.
 *
 * The screen owns the catalog call and the saved [selectedModel]; everything
 * that is purely about presenting the list — the search query, the manual
 * "Other model" choice and the status copy — lives here. That split lets a
 * Robolectric test render hundreds of options without an `AppGraph`, a key,
 * or a network call.
 *
 * Selection is derived from the **displayed** list, never from the small
 * bundled catalog: a live-fetched id must not look like an "Other model"
 * choice just because it is not in [AiModelCatalog].
 */
@Composable
internal fun AiModelPickerContent(
    models: List<AiModelOption>,
    selectedModel: String,
    provider: AiProvider,
    source: ModelSource?,
    fetchedAtEpochMillis: Long?,
    refreshing: Boolean,
    loadError: Boolean,
    onSelect: (String) -> Unit,
    onCustomModelChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var otherManuallySelected by remember { mutableStateOf(false) }

    val selectedOption = models.firstOrNull { it.id == selectedModel.trim() }
    val otherSelected = otherManuallySelected || selectedOption == null
    val filtered = if (query.isBlank()) {
        models
    } else {
        models.filter { it.id.contains(query.trim(), ignoreCase = true) }
    }
    val modelInvalid = !AiSettingsValidation.isAcceptableModelId(selectedModel)
    val countText = if (query.isBlank()) {
        stringResource(R.string.settings_ai_models_count, models.size)
    } else {
        stringResource(R.string.settings_ai_models_count_filtered, filtered.size, models.size)
    }
    val showSpinner = refreshing || (source == null && !loadError)
    val searchDescription = stringResource(R.string.settings_ai_models_search_description)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.95f)
            .imePadding()
            .testTag("ai_model_sheet"),
    ) {
        // Pinned header: title, count, refresh and close.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.settings_ai_model),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = countText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("ai_model_count"),
            )
            if (showSpinner) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(16.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = stringResource(R.string.settings_ai_models_loading)
                        },
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            TextButton(
                onClick = onRefresh,
                enabled = !refreshing && (source != null || loadError),
                modifier = Modifier
                    .testTag("ai_model_refresh")
                    .semantics {
                        contentDescription = stringResource(R.string.settings_ai_models_refresh)
                    },
            ) {
                Text(stringResource(R.string.settings_ai_models_refresh))
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.semantics {
                    contentDescription = stringResource(R.string.settings_ai_models_close)
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.settings_ai_models_close),
                )
            }
        }

        // Pinned search field.
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            placeholder = { Text(stringResource(R.string.settings_ai_models_search_hint)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = searchDescription,
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    val clearDescription = stringResource(R.string.settings_ai_models_clear_search_description)
                    IconButton(
                        onClick = { query = "" },
                        modifier = Modifier
                            .testTag("ai_model_search_clear")
                            .semantics { contentDescription = clearDescription },
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = clearDescription,
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("ai_model_search")
                .semantics { contentDescription = searchDescription },
        )

        // The privacy note stays visible without scrolling: it must match
        // the list actually shown, so a bundled list never claims to fetch.
        Text(
            text = pickerPrivacyText(provider, source),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("model_list_privacy"),
        )
        // Exactly one status line, kept out of the list so it is never lost
        // among 368 rows. A failed refresh leaves the last list in place.
        Text(
            text = pickerStatusText(provider, source, fetchedAtEpochMillis, refreshing, loadError),
            style = MaterialTheme.typography.bodySmall,
            color = if (loadError) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("model_list_status"),
        )

        if (otherSelected) {
            OutlinedTextField(
                value = selectedModel,
                onValueChange = onCustomModelChange,
                label = { Text(stringResource(R.string.settings_ai_custom_model)) },
                singleLine = true,
                isError = modelInvalid,
                supportingText = {
                    if (modelInvalid) {
                        Text(stringResource(R.string.settings_ai_invalid_model))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .testTag("model_custom_field"),
            )
        }

        // "Other model" is an action, not a row in the list. Pinning it above
        // the list keeps it reachable with hundreds of models, and keeps the
        // custom field next to it when selected. It is never search-filtered.
        ModelOptionRow(
            option = null,
            label = stringResource(R.string.settings_ai_model_other),
            selected = otherSelected,
            tag = "model_option_other",
            onClick = { otherManuallySelected = true },
        )

        // The list owns the remaining height. Nothing above it scrolls, and
        // the static notes live at its end so they stay reachable.
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .selectableGroup()
                .testTag("ai_model_list"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (filtered.isEmpty()) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.settings_ai_models_empty, query.trim()),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = { query = "" }) {
                            Text(stringResource(R.string.settings_ai_models_clear_search))
                        }
                    }
                }
            }
            items(filtered, key = { it.id }) { option ->
                ModelOptionRow(
                    option = option,
                    label = Bidi.isolate(option.id),
                    selected = selectedModel.trim() == option.id,
                    tag = "model_option_${option.id}",
                    onClick = {
                        otherManuallySelected = false
                        onSelect(option.id)
                    },
                )
            }
            AiModelCatalog.dataUseNotice(provider, selectedModel.ifBlank { provider.defaultModel })
                ?.let { notice ->
                    item(key = "data_use") {
                        PickerNote(stringResource(notice.stringRes()))
                    }
                }
            provider.freeTier?.let { tier ->
                item(key = "free_tier") {
                    PickerNote(
                        stringResource(
                            when (tier) {
                                AiFreeTier.FREE_MODELS -> R.string.settings_ai_tier_free_models
                                AiFreeTier.TRIAL_CREDITS -> R.string.settings_ai_tier_trial_credits
                            },
                        ),
                    )
                }
            }
            item(key = "paid") {
                PickerNote(stringResource(R.string.settings_ai_model_paid_note))
            }
        }
    }
}

@Composable
private fun pickerPrivacyText(provider: AiProvider, source: ModelSource?): String =
    when (source) {
        ModelSource.LIVE, ModelSource.CACHED ->
            stringResource(R.string.settings_ai_models_privacy, provider.displayName)
        // Also covers `null` while loading and a bundled list kept after an
        // error: neither has made a request for this screen.
        else -> stringResource(R.string.settings_ai_models_bundled_privacy)
    }

@Composable
private fun pickerStatusText(
    provider: AiProvider,
    source: ModelSource?,
    fetchedAtEpochMillis: Long?,
    refreshing: Boolean,
    loadError: Boolean,
): String = when {
    loadError -> stringResource(R.string.settings_ai_models_error)
    source == ModelSource.LIVE -> stringResource(
        R.string.settings_ai_models_live,
        provider.displayName,
        relativeModelAge(fetchedAtEpochMillis),
    )
    source == ModelSource.CACHED ->
        stringResource(R.string.settings_ai_models_cached, relativeModelAge(fetchedAtEpochMillis))
    source == ModelSource.BUNDLED -> stringResource(R.string.settings_ai_models_bundled)
    // A null snapshot is the bundled placeholder shown while loading.
    refreshing || source == null -> stringResource(R.string.settings_ai_models_loading)
    else -> stringResource(R.string.settings_ai_models_bundled)
}

@Composable
private fun PickerNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun ModelOptionRow(
    option: AiModelOption?,
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
        )
        if (option?.recommendedForSummaries == true) {
            RecommendedTag()
        }
        if (option?.free == true) {
            FreeTag()
        }
    }
}
