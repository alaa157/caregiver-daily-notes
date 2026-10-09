package com.caregiver.mobile.presentation.notes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.navigation.AppRoutes
import com.caregiver.mobile.core.theme.AppSizes
import com.caregiver.mobile.core.theme.AppSpacing
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.presentation.common.AppErrorState
import com.caregiver.mobile.presentation.common.AppTopBar
import com.caregiver.mobile.presentation.common.BottomActionBar
import com.caregiver.mobile.presentation.common.ChoiceRowOption
import com.caregiver.mobile.presentation.common.GroupLabel
import com.caregiver.mobile.presentation.common.PainScale
import com.caregiver.mobile.presentation.common.PrimaryButton
import com.caregiver.mobile.presentation.common.SafetyAlertCard
import com.caregiver.mobile.presentation.common.SectionTitle
import com.caregiver.mobile.presentation.common.assistedViewModel
import java.util.Locale

/**
 * Daily note editor (spec screen 5): AppBar, ChoiceRow per observation,
 * PainScale, falls toggle, MultilineField, footer PrimaryButton note.save.
 * Falls or high pain show the AlertSafety preview before save, using the
 * alert.redFlag copy key (demo fall rule). Option value labels come from
 * the bilingual OptionLabels until the owner supplies note.option.* copy.
 * States: idle, saving (button spinner), saved (note.saved toast + saved
 * confirmation), error with entered text preserved.
 */
@Composable
fun NoteEditorScreen(recipientId: String, graph: AppGraph, navController: NavController) {
    val vm: EditorDemoViewModel = assistedViewModel("editor-demo-$recipientId") {
        EditorDemoViewModel(recipientId, graph.demoRecipients, graph.demoNotes)
    }
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    state.savedId?.let { noteId ->
        LaunchedEffect(noteId) {
            navController.navigate(AppRoutes.noteSaved(noteId)) {
                popUpTo(AppRoutes.noteEditor(recipientId)) { inclusive = true }
            }
        }
    }
    val arabic = Locale.getDefault().language == "ar"
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            AppTopBar(
                title = stringResource(R.string.note_new_title),
                subtitle = Bidi.isolate(state.recipientName),
                onBack = { navController.popBackStack() },
            )
            OptionGroup.entries.forEach { group ->
                SectionTitle(groupTitle(group))
                FlowRow {
                    NoteOptions.values(group).forEach { value ->
                        val selected = when (group) {
                            OptionGroup.Mood -> state.mood == value
                            OptionGroup.Appetite -> state.appetite == value
                            OptionGroup.Sleep -> state.sleep == value
                            OptionGroup.Mobility -> state.mobility == value
                            OptionGroup.Medication -> state.medication == value
                        }
                        ChoiceRowOption(
                            label = OptionLabels.label(group, value, arabic),
                            selected = selected,
                            onClick = { vm.select(group, value) },
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.note_field_falls),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.fall,
                    onCheckedChange = vm::setFall,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = CaregiverColors.Danger,
                        checkedThumbColor = CaregiverColors.Surface,
                    ),
                )
            }
            SectionTitle(stringResource(R.string.note_field_pain))
            PainScale(selected = state.pain, onSelect = vm::setPain)
            if (vm.preview) {
                // AlertSafety preview before save (demo rule output).
                SafetyAlertCard(
                    title = stringResource(R.string.alert_redFlag_fall),
                    body = stringResource(R.string.alert_redFlag_fall),
                )
            }
            SectionTitle(stringResource(R.string.note_freeText_label))
            OutlinedTextField(
                value = state.text,
                onValueChange = vm::onText,
                isError = state.invalid || state.failed,
                supportingText = {
                    if (state.invalid || state.failed) {
                        Text(
                            stringResource(R.string.state_error),
                            color = CaregiverColors.Danger,
                        )
                    }
                },
                minLines = 5,
                shape = RoundedCornerShape(AppSizes.cardRadius),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CaregiverColors.Primary,
                    unfocusedBorderColor = CaregiverColors.Border,
                    focusedContainerColor = CaregiverColors.Surface,
                    unfocusedContainerColor = CaregiverColors.Surface,
                ),
                modifier = Modifier.fillMaxWidth().height(AppSizes.multilineMinHeight),
            )
            if (state.failed) {
                AppErrorState(
                    message = stringResource(R.string.state_error),
                    onRetry = { vm.submit(context) },
                )
            }
            Spacer(Modifier.height(AppSpacing.xs))
        }
        BottomActionBar {
            PrimaryButton(
                label = stringResource(R.string.note_save),
                onClick = { vm.submit(context) },
                loading = state.busy,
                height = AppSizes.buttonHeightLarge,
                large = true,
            )
        }
    }
}

@Composable
private fun groupTitle(group: OptionGroup): String = stringResource(
    when (group) {
        OptionGroup.Mood -> R.string.note_field_mood
        OptionGroup.Appetite -> R.string.note_field_appetite
        OptionGroup.Sleep -> R.string.note_field_sleep
        OptionGroup.Mobility -> R.string.note_field_mobility
        OptionGroup.Medication -> R.string.note_field_medication
    },
)

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FlowRow(
    content: @Composable () -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        modifier = Modifier.fillMaxWidth(),
    ) {
        content()
    }
}
