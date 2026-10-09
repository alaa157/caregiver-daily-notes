package com.caregiver.mobile.presentation.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.navigation.AppRoutes
import com.caregiver.mobile.core.theme.AppSpacing
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.time.DateFormats
import com.caregiver.mobile.presentation.common.AppCard
import com.caregiver.mobile.presentation.common.AppEmptyState
import com.caregiver.mobile.presentation.common.AppTopBar
import com.caregiver.mobile.presentation.common.BottomActionBar
import com.caregiver.mobile.presentation.common.PrimaryButton
import com.caregiver.mobile.presentation.common.SafetyAlertCard
import com.caregiver.mobile.presentation.common.SecondaryButton
import com.caregiver.mobile.presentation.common.SectionTitle
import com.caregiver.mobile.presentation.common.assistedViewModel
import com.caregiver.mobile.presentation.home.LoadFailed
import com.caregiver.mobile.presentation.home.LoadingRow
import java.util.Locale

/**
 * Note detail (spec screen 6): AppBar, Card with structured fields, the
 * original free text, appended addenda, SecondaryButton note.addendum plus
 * the correction hint. No edit action exists for the original note.
 * States: loading, empty (no data), error, success.
 */
@Composable
fun NoteDetailScreen(noteId: String, graph: AppGraph, navController: NavController) {
    val vm: NoteDetailDemoViewModel = assistedViewModel("note-demo-$noteId") {
        NoteDetailDemoViewModel(noteId, graph.demoNotes)
    }
    val state by vm.state.collectAsState()
    Column(Modifier.fillMaxSize().testTag("screen_note_detail")) {
        when (val s = state) {
            NoteDetailDemoState.Loading -> LoadingRow()
            NoteDetailDemoState.Error -> LoadFailed(onRetry = vm::refresh)
            NoteDetailDemoState.Empty -> Column(
                Modifier.fillMaxSize().padding(horizontal = AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
            ) {
                AppTopBar(
                    title = stringResource(R.string.note_new_title),
                    onBack = { navController.popBackStack() },
                )
                AppEmptyState(
                    icon = "○",
                    title = stringResource(R.string.note_new_title),
                    subtitle = stringResource(R.string.state_error),
                    actionLabel = stringResource(R.string.action_retry),
                    onAction = vm::refresh,
                )
            }
            is NoteDetailDemoState.Content -> DetailBody(s, navController)
        }
    }
}

@Composable
private fun DetailBody(
    s: NoteDetailDemoState.Content,
    navController: NavController,
) {
    val arabic = Locale.getDefault().language == "ar"
    val note = s.detail.note
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            AppTopBar(
                title = stringResource(R.string.note_new_title),
                subtitle = DateFormats.historyDay(note.date, arabic),
                onBack = { navController.popBackStack() },
            )
            AppCard {
                FieldLine(R.string.note_field_mood, OptionLabels.label(OptionGroup.Mood, note.mood, arabic))
                FieldLine(R.string.note_field_appetite, OptionLabels.label(OptionGroup.Appetite, note.appetite, arabic))
                FieldLine(R.string.note_field_sleep, OptionLabels.label(OptionGroup.Sleep, note.sleep, arabic))
                FieldLine(R.string.note_field_mobility, OptionLabels.label(OptionGroup.Mobility, note.mobility, arabic))
                FieldLine(
                    R.string.note_field_medication,
                    OptionLabels.label(OptionGroup.Medication, note.medicationTaken, arabic),
                )
                FieldLine(R.string.note_field_pain, "${note.pain} / 10")
                if (note.fall) {
                    // Demo rule output through the alert.redFlag copy.
                    SafetyAlertCard(
                        title = stringResource(R.string.alert_redFlag_fall),
                        body = DateFormats.historyDay(note.date, arabic),
                    )
                } else {
                    FieldLine(R.string.note_field_falls, "—")
                }
            }
            Text(
                text = note.text,
                style = MaterialTheme.typography.bodyLarge,
                color = CaregiverColors.TextPrimary,
            )
            SectionTitle(stringResource(R.string.note_addendum))
            if (s.detail.addenda.isEmpty()) {
                Text(
                    text = stringResource(R.string.note_addendum_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = CaregiverColors.TextSecondary,
                )
            } else {
                s.detail.addenda.forEach { addendum ->
                    AppCard {
                        Text(
                            text = addendum.text,
                            style = MaterialTheme.typography.bodyLarge,
                            color = CaregiverColors.TextPrimary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(AppSpacing.xs))
        }
        BottomActionBar {
            SecondaryButton(
                label = stringResource(R.string.note_addendum),
                onClick = { navController.navigate(AppRoutes.addendum(note.id)) },
            )
        }
    }
}

@Composable
private fun FieldLine(label: Int, value: String) {
    if (value.isBlank()) return
    Text(
        text = stringResource(label) + ": " + Bidi.isolate(value),
        style = MaterialTheme.typography.bodyLarge,
        color = CaregiverColors.TextPrimary,
    )
}
