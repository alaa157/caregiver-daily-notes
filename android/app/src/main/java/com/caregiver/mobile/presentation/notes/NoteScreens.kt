package com.caregiver.mobile.presentation.notes

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.navigation.AppRoutes
import com.caregiver.mobile.core.theme.AppSizes
import com.caregiver.mobile.core.theme.AppSpacing
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.presentation.common.AppTopBar
import com.caregiver.mobile.presentation.common.BottomActionBar
import com.caregiver.mobile.presentation.common.PrimaryButton
import com.caregiver.mobile.presentation.common.SecondaryButton
import com.caregiver.mobile.presentation.common.assistedViewModel
import com.caregiver.mobile.presentation.home.LoadFailed
import com.caregiver.mobile.presentation.home.LoadingRow

/**
 * Saved confirmation (extra, not in spec): reached from the spec Daily note
 * editor on save. Shows note.saved plus view/summary follow-ups.
 */
@Composable
fun NoteSavedScreen(noteId: String, graph: AppGraph, navController: NavController) {
    val activity = LocalContext.current as ComponentActivity
    val vm: NoteDetailDemoViewModel = assistedViewModel("note-demo-$noteId", activity) {
        NoteDetailDemoViewModel(noteId, graph.demoNotes)
    }
    val state by vm.state.collectAsState()
    val detail = (state as? NoteDetailDemoState.Content)?.detail
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (state) {
            NoteDetailDemoState.Loading -> LoadingRow()
            NoteDetailDemoState.Error -> LoadFailed(onRetry = vm::refresh)
            NoteDetailDemoState.Empty -> LoadFailed(onRetry = vm::refresh)
            is NoteDetailDemoState.Content -> Unit
        }
        if (detail == null) return@Column
        SpacerSaved()
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(AppSizes.bottomNavHeight)
                .background(
                    CaregiverColors.Success.copy(alpha = 0.12f),
                    RoundedCornerShape(AppSizes.cardRadius),
                ),
        ) {
            Text("✓", style = MaterialTheme.typography.displayLarge, color = CaregiverColors.Success)
        }
        Text(
            text = stringResource(R.string.note_saved),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = CaregiverColors.TextPrimary,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.md),
        ) {
            SecondaryButton(
                label = stringResource(R.string.note_saved),
                onClick = { navController.navigate(AppRoutes.noteDetail(noteId)) },
                height = AppSizes.buttonHeightLarge,
            )
            PrimaryButton(
                label = stringResource(R.string.summary_generate),
                onClick = { navController.navigate(AppRoutes.summary(detail.note.recipientId)) },
                height = AppSizes.buttonHeightLarge,
                large = true,
            )
        }
    }
}

@Composable
private fun SpacerSaved() {
    androidx.compose.foundation.layout.Spacer(Modifier.height(AppSizes.multilineMinHeight))
}

/**
 * Addendum form (extra, not in spec): reached from the spec Note detail via
 * note.addendum. Corrections append as new entries; the original is never
 * altered. Preserves entered text on failure.
 */
@Composable
fun AddendumScreen(noteId: String, graph: AppGraph, navController: NavController) {
    val activity = LocalContext.current as ComponentActivity
    val vm: NoteDetailDemoViewModel = assistedViewModel("note-demo-$noteId", activity) {
        NoteDetailDemoViewModel(noteId, graph.demoNotes)
    }
    val text by vm.addendumText.collectAsState()
    val busy by vm.addendumBusy.collectAsState()
    val failed by vm.addendumFailed.collectAsState()
    var done by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (done) {
        LaunchedEffect(Unit) { navController.popBackStack() }
    }
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
        ) {
            AppTopBar(
                title = stringResource(R.string.note_addendum),
                onBack = { navController.popBackStack() },
            )
            Text(
                text = stringResource(R.string.note_addendum_hint),
                style = MaterialTheme.typography.bodySmall,
                color = CaregiverColors.TextSecondary,
            )
            OutlinedTextField(
                value = text,
                onValueChange = vm::onAddendumText,
                isError = failed,
                supportingText = {
                    if (failed) {
                        Text(
                            stringResource(R.string.state_error),
                            color = CaregiverColors.Danger,
                        )
                    }
                },
                minLines = 6,
                shape = RoundedCornerShape(AppSizes.cardRadius),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CaregiverColors.Primary,
                    unfocusedBorderColor = CaregiverColors.Border,
                    focusedContainerColor = CaregiverColors.Surface,
                    unfocusedContainerColor = CaregiverColors.Surface,
                ),
                modifier = Modifier.fillMaxWidth().height(AppSizes.multilineMinHeight),
            )
        }
        BottomActionBar {
            PrimaryButton(
                label = stringResource(R.string.note_save),
                onClick = { vm.submitAddendum { done = true } },
                loading = busy,
                height = AppSizes.buttonHeightLarge,
                large = true,
            )
        }
    }
}
