package com.caregiver.mobile.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.caregiver.mobile.R
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.data.csv.AndroidCsvDocumentIo
import com.caregiver.mobile.data.csv.CsvFailure
import com.caregiver.mobile.data.csv.CsvFailureReason
import com.caregiver.mobile.data.csv.ImportIssue
import com.caregiver.mobile.data.csv.ImportPlan
import com.caregiver.mobile.data.csv.ImportSummary
import com.caregiver.mobile.data.csv.RecipientRef
import com.caregiver.mobile.data.csv.UnmatchedRecipient
import com.caregiver.mobile.presentation.common.selectableCsvRecipients
import com.caregiver.mobile.ui.component.DefaultDialog

/** Max invalid rows listed in the preview; the rest are summarized by count. */
private const val MAX_LISTED_INVALID_ROWS = 5

/**
 * The result of the last Data & Backup action: an exact count on success, a
 * localized, actionable message on failure, and a dismiss button. Shown under
 * the settings group and replaced by the next action's result.
 */
@Composable
fun CsvNoticeLine(notice: CsvNotice, onDismiss: () -> Unit) {
    val text = when (notice) {
        is CsvNotice.ExportDone -> if (notice.recordCount == 0) {
            stringResource(R.string.csv_export_empty)
        } else {
            stringResource(R.string.csv_export_success, notice.recordCount)
        }
        is CsvNotice.ImportDone -> importSummaryText(notice.summary)
        is CsvNotice.Failed -> when (notice.operation) {
            CsvOperation.EXPORT -> stringResource(
                R.string.csv_export_failed,
                csvFailureText(notice.failure),
            )
            CsvOperation.IMPORT -> stringResource(
                R.string.csv_import_failed,
                csvFailureText(notice.failure),
            )
        }
    }
    val isError = notice is CsvNotice.Failed ||
        (notice is CsvNotice.ImportDone && notice.summary.failure != null)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isError) CaregiverColors.DangerContainer else CaregiverColors.SuccessContainer,
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) CaregiverColors.Danger else CaregiverColors.Success,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.csv_dismiss))
        }
    }
}

@Composable
private fun importSummaryText(summary: ImportSummary): String {
    val base = stringResource(
        R.string.csv_import_summary,
        summary.imported,
        summary.skipped,
        summary.duplicates,
        summary.rejected,
    )
    return if (summary.failure != null) {
        "$base ${stringResource(R.string.csv_import_summary_failed)}"
    } else {
        base
    }
}

/**
 * Import preview: exact counts, the duplicate policy in plain words, and one
 * row per recipient identifier that matched nothing so the user can map it to
 * an existing recipient or leave it skipped. Nothing is written until Import
 * is tapped; every interaction is disabled while the commit runs.
 */
@Composable
fun CsvImportDialog(
    plan: ImportPlan,
    working: Boolean,
    onResolve: (key: String, recipientId: String?) -> Unit,
    onCommit: () -> Unit,
    onDismiss: () -> Unit,
) {
    DefaultDialog(
        onDismiss = { if (!working) onDismiss() },
        modifier = Modifier.fillMaxWidth(),
        title = { Text(stringResource(R.string.csv_import_preview_title)) },
        buttons = {
            TextButton(onClick = onDismiss, enabled = !working) {
                Text(stringResource(android.R.string.cancel))
            }
            TextButton(
                onClick = onCommit,
                enabled = !working && plan.valid.isNotEmpty(),
            ) {
                Text(stringResource(R.string.csv_import_start))
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 460.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (working) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.csv_import_working),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            PreviewCount(stringResource(R.string.csv_import_total, plan.totalRows))
            PreviewCount(
                text = stringResource(R.string.csv_import_valid, plan.valid.size),
                emphasized = true,
            )
            if (plan.duplicates.isNotEmpty()) {
                PreviewCount(stringResource(R.string.csv_import_duplicates, plan.duplicates.size))
            }
            if (plan.invalid.isNotEmpty()) {
                PreviewCount(stringResource(R.string.csv_import_invalid, plan.invalid.size))
            }
            if (plan.valid.isEmpty()) {
                Text(
                    text = stringResource(R.string.csv_import_nothing_ready),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (plan.unmatched.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.csv_import_unmatched_heading),
                    style = MaterialTheme.typography.titleSmall,
                )
                plan.unmatched.forEach { entry ->
                    UnmatchedRecipientRow(
                        entry = entry,
                        recipients = plan.recipients,
                        selectedId = plan.overrides[entry.key],
                        enabled = !working,
                        onSelect = { id -> onResolve(entry.key, id) },
                    )
                }
                Text(
                    text = stringResource(R.string.csv_import_unmatched_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (plan.invalid.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                plan.invalid.take(MAX_LISTED_INVALID_ROWS).forEach { row ->
                    Text(
                        text = stringResource(
                            R.string.csv_import_invalid_row,
                            row.line,
                            importIssueText(row.issue, row.column),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (plan.invalid.size > MAX_LISTED_INVALID_ROWS) {
                    Text(
                        text = stringResource(
                            R.string.csv_import_more_rows,
                            plan.invalid.size - MAX_LISTED_INVALID_ROWS,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (plan.extraHeaders.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.csv_import_extra_headers,
                        plan.extraHeaders.joinToString(", "),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.csv_import_duplicate_policy),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!plan.policy.preservesDates) {
                Text(
                    text = stringResource(R.string.csv_import_server_date_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = CaregiverColors.Warning,
                )
            }
        }
    }
}

@Composable
private fun PreviewCount(text: String, emphasized: Boolean = false) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal,
    )
}

@Composable
private fun UnmatchedRecipientRow(
    entry: UnmatchedRecipient,
    recipients: List<RecipientRef>,
    selectedId: String?,
    enabled: Boolean,
    onSelect: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = recipients.firstOrNull { it.id == selectedId }?.name

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                R.string.csv_import_unmatched_entry,
                entry.identifier,
                entry.rowCount,
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { expanded = true }, enabled = enabled) {
            Text(selectedName ?: stringResource(R.string.csv_import_skip))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.csv_import_skip)) },
                onClick = {
                    expanded = false
                    onSelect(null)
                },
            )
            selectableCsvRecipients(recipients)
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
                .forEach { recipient ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (recipient.active) {
                                    recipient.name
                                } else {
                                    "${recipient.name} ${stringResource(R.string.csv_import_inactive)}"
                                },
                            )
                        },
                        onClick = {
                            expanded = false
                            onSelect(recipient.id)
                        },
                    )
                }
        }
    }
}

/** Localized sentence for a fatal transfer failure. No note content involved. */
@Composable
internal fun csvFailureText(failure: CsvFailure): String = when (failure.reason) {
    CsvFailureReason.EMPTY_FILE -> stringResource(R.string.csv_error_empty)
    CsvFailureReason.NO_ROWS -> stringResource(R.string.csv_error_no_rows)
    CsvFailureReason.MALFORMED_CSV -> if (failure.line != null) {
        stringResource(R.string.csv_error_malformed, failure.line)
    } else {
        stringResource(R.string.csv_error_malformed_generic)
    }
    CsvFailureReason.MISSING_HEADERS -> stringResource(
        R.string.csv_error_missing_headers,
        failure.missingHeaders.joinToString(", "),
    )
    CsvFailureReason.DUPLICATE_HEADERS -> stringResource(
        R.string.csv_error_duplicate_headers,
        failure.detail.orEmpty(),
    )
    CsvFailureReason.READ_FAILED -> stringResource(R.string.csv_error_read)
    CsvFailureReason.TOO_LARGE -> stringResource(
        R.string.csv_error_too_large,
        AndroidCsvDocumentIo.MAX_IMPORT_MEGABYTES,
    )
    CsvFailureReason.WRITE_FAILED -> stringResource(R.string.csv_error_write)
    CsvFailureReason.SOURCE_FAILED -> stringResource(R.string.csv_error_source)
    CsvFailureReason.INSERT_FAILED -> stringResource(R.string.csv_error_insert)
    CsvFailureReason.UNKNOWN -> stringResource(R.string.csv_error_unknown)
}

/** Localized per-row rejection reason. */
@Composable
internal fun importIssueText(issue: ImportIssue, column: String?): String = when (issue) {
    ImportIssue.WRONG_FIELD_COUNT -> stringResource(R.string.csv_issue_field_count)
    ImportIssue.MISSING_DATE -> stringResource(R.string.csv_issue_date_required)
    ImportIssue.INVALID_DATE -> stringResource(R.string.csv_issue_date)
    ImportIssue.DATE_NOT_SUPPORTED -> stringResource(R.string.csv_issue_date_not_supported)
    ImportIssue.INVALID_PAIN -> stringResource(R.string.csv_issue_pain)
    ImportIssue.INVALID_FALL -> stringResource(R.string.csv_issue_fall)
    ImportIssue.MISSING_MOOD -> stringResource(R.string.csv_issue_mood)
    ImportIssue.VALUE_TOO_LONG -> stringResource(R.string.csv_issue_too_long, column.orEmpty())
    ImportIssue.EMPTY_TEXT -> stringResource(R.string.csv_issue_text)
    ImportIssue.MISSING_RECIPIENT -> stringResource(R.string.csv_issue_recipient)
    ImportIssue.UNMATCHED_RECIPIENT -> stringResource(R.string.csv_issue_unmatched)
    ImportIssue.DUPLICATE -> stringResource(R.string.csv_issue_duplicate)
    ImportIssue.UNKNOWN -> stringResource(R.string.csv_issue_unknown)
}
