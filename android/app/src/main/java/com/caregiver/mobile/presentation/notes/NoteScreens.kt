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
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.PlexArabic
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppTopBar(
                title = stringResource(R.string.editor_title),
                onBack = { navController.popBackStack() },
            )
            // Section 1: الحالة العامة — 4 chip groups.
            SectionTitle(stringResource(R.string.editor_section_general))
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
                    FieldError(stringResource(R.string.editor_error_mood))
                }
            }
            // Section 2: الأدوية والسقوط.
            SectionTitle(stringResource(R.string.editor_section_meds_fall))
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
            GroupLabelText(stringResource(R.string.editor_fall_question))
            ChipWrap {
                HtmlChip(
                    label = stringResource(R.string.editor_no),
                    selected = !state.fall,
                    onClick = { vm.setFall(false) },
                )
                HtmlChip(
                    label = stringResource(R.string.editor_yes),
                    selected = state.fall,
                    onClick = { vm.setFall(true) },
                )
            }
            // Section 3: الألم — 11 square 44x44 buttons.
            SectionTitle(stringResource(R.string.editor_section_pain))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (0..10).forEach { level ->
                    val selected = state.pain == level
                    OutlinedButton(
                        onClick = { vm.setPain(level) },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            if (selected) CaregiverColors.Primary else CaregiverColors.Border,
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) CaregiverColors.Primary else Color.White,
                            contentColor = if (selected) Color.White else CaregiverColors.Ink,
                        ),
                        modifier = Modifier.size(44.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        Text("$level", fontFamily = PlexArabic, fontWeight = FontWeight.Bold)
                    }
                }
            }
            HelperCaption(stringResource(R.string.editor_pain_caption))
            // Section 4: ملاحظات إضافية — textarea 130dp.
            SectionTitle(stringResource(R.string.editor_section_extra))
            OutlinedTextField(
                value = state.text,
                onValueChange = vm::onText,
                placeholder = { Text(stringResource(R.string.editor_hint_extra), fontFamily = PlexArabic) },
                isError = state.textError != null,
                supportingText = {
                    if (state.textError != null) {
                        Text(
                            stringResource(R.string.editor_error_text),
                            fontFamily = PlexArabic,
                            color = CaregiverColors.Danger,
                        )
                    }
                },
                minLines = 5,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CaregiverColors.Primary,
                    unfocusedBorderColor = CaregiverColors.Border,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth().height(130.dp),
            )
            state.formError?.let {
                FieldError(
                    when (it) {
                        is EditorError.Rejected -> stringResource(EditorErrorText.res(it.code))
                        EditorError.Unreachable -> stringResource(R.string.auth_error_unreachable)
                        else -> ""
                    },
                )
            }
            Spacer(Modifier.height(4.dp))
        }
        // Footer action bar: primary 56dp + centered caption.
        BottomActionBar {
            PrimaryButton(
                label = stringResource(R.string.editor_save),
                onClick = vm::submit,
                enabled = !state.busy,
                height = 56.dp,
                large = true,
            )
            HelperCaption(
                stringResource(R.string.editor_save_note),
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
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selected) {
                    Text("✓", fontFamily = PlexArabic, fontWeight = FontWeight.Bold)
                }
                Text(label, fontFamily = PlexArabic, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        },
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(
            1.dp,
            if (selected) CaregiverColors.Primary else CaregiverColors.Border,
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = Color.White,
            labelColor = CaregiverColors.Ink,
            selectedContainerColor = CaregiverColors.Primary,
            selectedLabelColor = Color.White,
        ),
    )
}

@Composable
private fun GroupLabel(group: OptionGroup) {
    GroupLabelText(
        stringResource(
            when (group) {
                OptionGroup.Mood -> R.string.editor_mood
                OptionGroup.Appetite -> R.string.editor_appetite
                OptionGroup.Sleep -> R.string.editor_sleep
                OptionGroup.Mobility -> R.string.editor_mobility
                OptionGroup.Medication -> R.string.editor_medication
            },
        ),
    )
}

@Composable
private fun GroupLabelText(text: String) {
    Text(
        text,
        fontFamily = PlexArabic,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = CaregiverColors.Ink,
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
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(120.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .then(
                    Modifier.background(
                        CaregiverColors.SuccessContainer,
                        RoundedCornerShape(36.dp),
                    ),
                ),
        ) {
            Text("✓", fontSize = 36.sp, color = CaregiverColors.Success)
        }
        Text(
            text = stringResource(R.string.saved_title),
            fontFamily = PlexArabic,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            textAlign = TextAlign.Center,
            color = CaregiverColors.Ink,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            SecondaryButton(
                label = stringResource(R.string.saved_view),
                onClick = { navController.navigate("note/$noteId") },
                height = 56.dp,
            )
            PrimaryButton(
                label = stringResource(R.string.saved_summarize),
                onClick = { recipientId?.let { navController.navigate("summary/$it") } },
                enabled = recipientId != null,
                height = 56.dp,
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
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    AppTopBar(
                        title = stringResource(R.string.editor_title),
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
                            stringResource(R.string.editor_mood),
                            OptionLabels.label(OptionGroup.Mood, s.content.note.mood, arabic),
                        )
                        StatMini(
                            stringResource(R.string.editor_appetite),
                            OptionLabels.label(OptionGroup.Appetite, s.content.note.appetite, arabic),
                        )
                        StatMini(
                            stringResource(R.string.editor_sleep),
                            OptionLabels.label(OptionGroup.Sleep, s.content.note.sleep, arabic),
                        )
                        StatMini(
                            stringResource(R.string.editor_mobility),
                            OptionLabels.label(OptionGroup.Mobility, s.content.note.mobility, arabic),
                        )
                        StatMini(
                            stringResource(R.string.editor_pain),
                            "${s.content.note.pain} / 10",
                        )
                        StatMini(
                            stringResource(R.string.editor_fall),
                            stringResource(
                                if (s.content.note.fall) R.string.detail_fall_yes
                                else R.string.detail_fall_no,
                            ),
                        )
                    }
                    // Caregiver notes + lock row.
                    SectionTitle(stringResource(R.string.editor_text))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("🔒", fontSize = 16.sp)
                        Text(
                            stringResource(R.string.note_original_locked),
                            fontFamily = PlexArabic,
                            fontSize = 13.sp,
                            color = CaregiverColors.Muted,
                        )
                    }
                    Text(
                        s.content.note.text,
                        fontFamily = PlexArabic,
                        fontSize = 16.sp,
                        lineHeight = 29.sp,
                        color = CaregiverColors.Ink,
                    )
                    // Attachments timeline.
                    SectionTitle(stringResource(R.string.note_attachments))
                    if (s.content.addenda.isEmpty()) {
                        HelperCaption(stringResource(R.string.notes_empty))
                    } else {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(start = 14.dp),
                        ) {
                            s.content.addenda.forEach { addendum ->
                                AppCard {
                                    Text(
                                        addendum.text,
                                        fontFamily = PlexArabic,
                                        fontSize = 14.sp,
                                        color = CaregiverColors.Ink,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                BottomActionBar {
                    SecondaryButton(
                        label = stringResource(R.string.detail_add_correction),
                        onClick = { navController.navigate("addendum/$noteId") },
                        height = 52.dp,
                    )
                    PrimaryButton(
                        label = stringResource(R.string.summary_generate),
                        onClick = {
                            s.content.note.recipientId.let {
                                navController.navigate("summary/$it")
                            }
                        },
                        height = 52.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatMini(label: String, value: String) {
    AppCard(modifier = Modifier.fillMaxWidth(0.48f)) {
        Text(label, fontFamily = PlexArabic, fontSize = 13.sp, color = CaregiverColors.Muted)
        Text(
            value.ifBlank { "—" },
            fontFamily = PlexArabic,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = CaregiverColors.Ink,
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppTopBar(
                title = stringResource(R.string.addendum_title),
                onBack = { navController.popBackStack() },
            )
            InfoAlertCard(
                title = stringResource(R.string.addendum_info_title),
                body = stringResource(R.string.addendum_info_body),
            )
            GroupLabelText(stringResource(R.string.addendum_text))
            OutlinedTextField(
                value = form.text,
                onValueChange = vm::onAddendumText,
                placeholder = { Text(stringResource(R.string.addendum_hint), fontFamily = PlexArabic) },
                isError = form.addendumError,
                supportingText = {
                    if (form.addendumError) {
                        Text(
                            stringResource(R.string.addendum_error),
                            fontFamily = PlexArabic,
                            color = CaregiverColors.Danger,
                        )
                    }
                },
                minLines = 6,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CaregiverColors.Primary,
                    unfocusedBorderColor = CaregiverColors.Border,
                    focusedContainerColor = Color.White,
                    unfocusedContainerColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth().height(150.dp),
            )
            if (form.sendFailed) {
                FieldError(stringResource(R.string.addendum_error_send))
            }
        }
        BottomActionBar {
            PrimaryButton(
                label = stringResource(R.string.addendum_save),
                onClick = vm::submitAddendum,
                enabled = !form.busy,
                height = 56.dp,
                large = true,
            )
        }
    }
}
