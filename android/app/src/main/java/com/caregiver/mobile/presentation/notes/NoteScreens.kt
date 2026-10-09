package com.caregiver.mobile.presentation.notes

import androidx.activity.ComponentActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.theme.AppRadius
import com.caregiver.mobile.core.theme.AppSizes
import com.caregiver.mobile.core.theme.AppSpacing
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.AppFontFamily
import com.caregiver.mobile.core.time.DateFormats
import com.caregiver.mobile.presentation.common.AppCard
import com.caregiver.mobile.presentation.common.AppTopBar
import com.caregiver.mobile.presentation.common.BottomActionBar
import com.caregiver.mobile.presentation.common.FieldError
import com.caregiver.mobile.presentation.common.HelperCaption
import com.caregiver.mobile.presentation.common.InfoAlertCard
import com.caregiver.mobile.presentation.common.PrimaryButton
import com.caregiver.mobile.presentation.common.SecondaryButton
import com.caregiver.mobile.presentation.common.SectionTitle
import com.caregiver.mobile.presentation.common.assistedViewModel
import com.caregiver.mobile.presentation.home.LoadFailed
import com.caregiver.mobile.presentation.home.LoadingRow
import java.util.Locale

/**
 * Note editor (board 5, 1560px): الحالة العامة / الأدوية والسقوط / الألم /
 * ملاحظات إضافية + bottom action bar (56dp save + centered caption).
 * Same ViewModel and validation; only the presentation matches the HTML.
 */
@Composable
fun NoteEditorScreen(recipientId: String, graph: AppGraph, navController: NavController) {
    val vm: NoteEditorViewModel = assistedViewModel("editor-$recipientId") {
        NoteEditorViewModel(recipientId, graph.apis, graph.auth)
    }
    val state by vm.state.collectAsState()
    state.savedId?.let { noteId ->
        LaunchedEffect(noteId) {
            navController.navigate("note-saved/$noteId") {
                popUpTo("note-editor/$recipientId") { inclusive = true }
            }
        }
    }
    val arabic = Locale.getDefault().language == "ar"
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppTopBar(
                title = stringResource(R.string.note_new_title),
                onBack = { navController.popBackStack() },
            )
            // Section 1: الحالة العامة — 4 chip groups.
            SectionTitle(stringResource(R.string.note_field_mood))
            OptionGroup.entries.filter { it != OptionGroup.Medication }.forEach { group ->
                GroupLabel(group)
                ChipWrap {
                    NoteOptions.values(group).forEach { value ->
                        val selected = when (group) {
                            OptionGroup.Mood -> state.mood == value
                            OptionGroup.Appetite -> state.appetite == value
                            OptionGroup.Sleep -> state.sleep == value
                            OptionGroup.Mobility -> state.mobility == value
                            else -> false
                        }
                        HtmlChip(
                            label = OptionLabels.label(group, value, arabic),
                            selected = selected,
                            onClick = { vm.select(group, value) },
                        )
                    }
                }
                if (group == OptionGroup.Mood && state.moodError != null) {
                    FieldError(stringResource(R.string.state_error))
                }
            }
            // Section 2: الأدوية والسقوط.
            SectionTitle(stringResource(R.string.note_field_medication))
            GroupLabel(OptionGroup.Medication)
            ChipWrap {
                NoteOptions.values(OptionGroup.Medication).forEach { value ->
                    HtmlChip(
                        label = OptionLabels.label(OptionGroup.Medication, value, arabic),
                        selected = state.medication == value,
                        onClick = { vm.select(OptionGroup.Medication, value) },
                    )
                }
            }
            GroupLabelText(stringResource(R.string.note_field_falls))
            ChipWrap {
                HtmlChip(
                    label = stringResource(R.string.action_cancel),
                    selected = !state.fall,
                    onClick = { vm.setFall(false) },
                )
                HtmlChip(
                    label = stringResource(R.string.action_retry),
                    selected = state.fall,
                    onClick = { vm.setFall(true) },
                )
            }
            // Section 3: الألم — 11 square 44x44 buttons.
            SectionTitle(stringResource(R.string.note_field_pain))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0..10).forEach { level ->
                    val selected = state.pain == level
                    OutlinedButton(
                        onClick = { vm.setPain(level) },
                        shape = RoundedCornerShape(AppSpacing.sm),
                        border = BorderStroke(
                            AppSizes.borderWidth,
                            if (selected) CaregiverColors.Primary else CaregiverColors.Border,
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) CaregiverColors.Primary else CaregiverColors.Surface,
                            contentColor = if (selected) CaregiverColors.Surface else CaregiverColors.TextPrimary,
                        ),
                        modifier = Modifier.size(44.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        Text("$level", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold)
                    }
                }
            }
            HelperCaption(stringResource(R.string.note_field_pain))
            // Section 4: ملاحظات إضافية — textarea 130dp.
            SectionTitle(stringResource(R.string.note_freeText_label))
            OutlinedTextField(
                value = state.text,
                onValueChange = vm::onText,
                placeholder = { Text(stringResource(R.string.note_freeText_label), fontFamily = AppFontFamily) },
                isError = state.textError != null,
                supportingText = {
                    if (state.textError != null) {
                        Text(
                            stringResource(R.string.state_error),
                            fontFamily = AppFontFamily,
                            color = CaregiverColors.Danger,
                        )
                    }
                },
                minLines = 5,
                shape = RoundedCornerShape(AppSpacing.sm),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CaregiverColors.Primary,
                    unfocusedBorderColor = CaregiverColors.Border,
                    focusedContainerColor = CaregiverColors.Surface,
                    unfocusedContainerColor = CaregiverColors.Surface,
                ),
                modifier = Modifier.fillMaxWidth().height(130.dp),
            )
            state.formError?.let {
                FieldError(
                    when (it) {
                        is EditorError.Rejected -> stringResource(EditorErrorText.res(it.code))
                        EditorError.Unreachable -> stringResource(R.string.state_error)
                        else -> ""
                    },
                )
            }
            Spacer(Modifier.height(AppSpacing.xxs))
        }
        // Footer action bar: primary 56dp + centered caption.
        BottomActionBar {
            PrimaryButton(
                label = stringResource(R.string.note_save),
                onClick = vm::submit,
                enabled = !state.busy,
                height = AppSizes.buttonHeightLarge,
                large = true,
            )
            HelperCaption(
                stringResource(R.string.summary_unavailable),
                align = TextAlign.Center,
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChipWrap(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        content()
    }
}

@Composable
private fun HtmlChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selected) {
                    Text("✓", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold)
                }
                Text(label, fontFamily = AppFontFamily, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        },
        shape = RoundedCornerShape(AppRadius.pill),
        border = BorderStroke(
            AppSizes.borderWidth,
            if (selected) CaregiverColors.Primary else CaregiverColors.Border,
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = CaregiverColors.Surface,
            labelColor = CaregiverColors.TextPrimary,
            selectedContainerColor = CaregiverColors.Primary,
            selectedLabelColor = CaregiverColors.Surface,
        ),
    )
}

@Composable
private fun GroupLabel(group: OptionGroup) {
    GroupLabelText(
        stringResource(
            when (group) {
                OptionGroup.Mood -> R.string.note_field_mood
                OptionGroup.Appetite -> R.string.note_field_appetite
                OptionGroup.Sleep -> R.string.note_field_sleep
                OptionGroup.Mobility -> R.string.note_field_mobility
                OptionGroup.Medication -> R.string.note_field_medication
            },
        ),
    )
}

@Composable
private fun GroupLabelText(text: String) {
    Text(
        text,
        fontFamily = AppFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = CaregiverColors.TextPrimary,
    )
}

/**
 * Saved confirmation (board 6): centered 72dp success circle + 24sp title
 * + secondary view + primary summarize (56dp).
 */
@Composable
fun NoteSavedScreen(noteId: String, graph: AppGraph, navController: NavController) {
    val activity = LocalContext.current as ComponentActivity
    val vm: NoteDetailViewModel = assistedViewModel("note-$noteId", activity) {
        NoteDetailViewModel(noteId, graph.apis, graph.auth)
    }
    val detail by vm.state.collectAsState()
    val recipientId = (detail as? NoteDetailState.Content)?.content?.note?.recipientId
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(AppSizes.multilineMinHeight))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(AppSizes.bottomNavHeight)
                .then(
                    Modifier.background(
                        CaregiverColors.Success.copy(alpha = 0.12f),
                        RoundedCornerShape(36.dp),
                    ),
                ),
        ) {
            Text("✓", fontSize = 36.sp, color = CaregiverColors.Success)
        }
        Text(
            text = stringResource(R.string.note_saved),
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            textAlign = TextAlign.Center,
            color = CaregiverColors.TextPrimary,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.md),
        ) {
            SecondaryButton(
                label = stringResource(R.string.note_saved),
                onClick = { navController.navigate("note/$noteId") },
                height = AppSizes.buttonHeightLarge,
            )
            PrimaryButton(
                label = stringResource(R.string.summary_generate),
                onClick = { recipientId?.let { navController.navigate("summary/$it") } },
                enabled = recipientId != null,
                height = AppSizes.buttonHeightLarge,
                large = true,
            )
        }
    }
}

/**
 * Note detail (board 7, 1120px): 2-col stat grid + caregiver notes + lock
 * row + attachments timeline + footer (secondary addendum + primary summary).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun NoteDetailScreen(noteId: String, graph: AppGraph, navController: NavController) {
    val activity = LocalContext.current as ComponentActivity
    val vm: NoteDetailViewModel = assistedViewModel("note-$noteId", activity) {
        NoteDetailViewModel(noteId, graph.apis, graph.auth)
    }
    val state by vm.state.collectAsState()
    Column(Modifier.fillMaxSize()) {
        when (val s = state) {
            NoteDetailState.Loading -> LoadingRow()
            NoteDetailState.Error -> LoadFailed(onRetry = vm::refresh)
            is NoteDetailState.Content -> {
                val arabic = Locale.getDefault().language == "ar"
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                        .padding(horizontal = AppSpacing.md),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AppTopBar(
                        title = stringResource(R.string.note_new_title),
                        subtitle = DateFormats.historyDay(s.content.note.date, arabic),
                        onBack = { navController.popBackStack() },
                    )
                    // 2-col grid of 6 mini-cards.
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        maxItemsInEachRow = 2,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        StatMini(
                            stringResource(R.string.note_field_mood),
                            OptionLabels.label(OptionGroup.Mood, s.content.note.mood, arabic),
                        )
                        StatMini(
                            stringResource(R.string.note_field_appetite),
                            OptionLabels.label(OptionGroup.Appetite, s.content.note.appetite, arabic),
                        )
                        StatMini(
                            stringResource(R.string.note_field_sleep),
                            OptionLabels.label(OptionGroup.Sleep, s.content.note.sleep, arabic),
                        )
                        StatMini(
                            stringResource(R.string.note_field_mobility),
                            OptionLabels.label(OptionGroup.Mobility, s.content.note.mobility, arabic),
                        )
                        StatMini(
                            stringResource(R.string.note_field_pain),
                            "${s.content.note.pain} / 10",
                        )
                        StatMini(
                            stringResource(R.string.note_field_falls),
                            stringResource(
                                if (s.content.note.fall) R.string.alert_redFlag_fall
                                else R.string.action_cancel,
                            ),
                        )
                    }
                    // Caregiver notes + lock row.
                    SectionTitle(stringResource(R.string.note_freeText_label))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("🔒", fontSize = 16.sp)
                        Text(
                            stringResource(R.string.note_addendum_hint),
                            fontFamily = AppFontFamily,
                            fontSize = 13.sp,
                            color = CaregiverColors.TextSecondary,
                        )
                    }
                    Text(
                        s.content.note.text,
                        fontFamily = AppFontFamily,
                        fontSize = 16.sp,
                        lineHeight = 29.sp,
                        color = CaregiverColors.TextPrimary,
                    )
                    // Attachments timeline.
                    SectionTitle(stringResource(R.string.note_addendum))
                    if (s.content.addenda.isEmpty()) {
                        HelperCaption(stringResource(R.string.people_empty))
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                            modifier = Modifier.padding(start = 14.dp),
                        ) {
                            s.content.addenda.forEach { addendum ->
                                AppCard {
                                    Text(
                                        addendum.text,
                                        fontFamily = AppFontFamily,
                                        fontSize = 14.sp,
                                        color = CaregiverColors.TextPrimary,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(AppSpacing.xxs))
                }
                BottomActionBar {
                    SecondaryButton(
                        label = stringResource(R.string.note_addendum),
                        onClick = { navController.navigate("addendum/$noteId") },
                        height = AppSizes.inputHeight,
                    )
                    PrimaryButton(
                        label = stringResource(R.string.summary_generate),
                        onClick = {
                            s.content.note.recipientId.let {
                                navController.navigate("summary/$it")
                            }
                        },
                        height = AppSizes.inputHeight,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatMini(label: String, value: String) {
    AppCard(modifier = Modifier.fillMaxWidth(0.48f)) {
        Text(label, fontFamily = AppFontFamily, fontSize = 13.sp, color = CaregiverColors.TextSecondary)
        Text(
            value.ifBlank { "—" },
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = CaregiverColors.TextPrimary,
        )
    }
}

/**
 * Addendum form (board 8 — إضافة ملحق): blue info alert + textarea 150dp +
 * primary 56dp save. Same ViewModel; presentation matches the HTML.
 */
@Composable
fun AddendumScreen(noteId: String, graph: AppGraph, navController: NavController) {
    val activity = LocalContext.current as ComponentActivity
    val vm: NoteDetailViewModel = assistedViewModel("note-$noteId", activity) {
        NoteDetailViewModel(noteId, graph.apis, graph.auth)
    }
    val form by vm.addendum.collectAsState()
    if (form.appended) {
        LaunchedEffect(Unit) { navController.popBackStack() }
    }
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppTopBar(
                title = stringResource(R.string.note_addendum),
                onBack = { navController.popBackStack() },
            )
            InfoAlertCard(
                title = stringResource(R.string.note_addendum_hint),
                body = stringResource(R.string.note_addendum_hint),
            )
            GroupLabelText(stringResource(R.string.note_freeText_label))
            OutlinedTextField(
                value = form.text,
                onValueChange = vm::onAddendumText,
                placeholder = { Text(stringResource(R.string.note_freeText_label), fontFamily = AppFontFamily) },
                isError = form.addendumError,
                supportingText = {
                    if (form.addendumError) {
                        Text(
                            stringResource(R.string.state_error),
                            fontFamily = AppFontFamily,
                            color = CaregiverColors.Danger,
                        )
                    }
                },
                minLines = 6,
                shape = RoundedCornerShape(AppSpacing.sm),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CaregiverColors.Primary,
                    unfocusedBorderColor = CaregiverColors.Border,
                    focusedContainerColor = CaregiverColors.Surface,
                    unfocusedContainerColor = CaregiverColors.Surface,
                ),
                modifier = Modifier.fillMaxWidth().height(150.dp),
            )
            if (form.sendFailed) {
                FieldError(stringResource(R.string.state_error))
            }
        }
        BottomActionBar {
            PrimaryButton(
                label = stringResource(R.string.note_save),
                onClick = vm::submitAddendum,
                enabled = !form.busy,
                height = AppSizes.buttonHeightLarge,
                large = true,
            )
        }
    }
}
