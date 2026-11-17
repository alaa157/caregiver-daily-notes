package com.caregiver.mobile.presentation.notes

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.navigation.AppRoutes
import com.caregiver.mobile.core.navigation.MainTab
import com.caregiver.mobile.core.navigation.markUnsavedChanges
import com.caregiver.mobile.data.NoteDraftStore
import com.caregiver.mobile.data.api.NoteDetailDto
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.presentation.common.AppDimens
import com.caregiver.mobile.core.theme.PlexArabic
import com.caregiver.mobile.core.time.DateFormats
import com.caregiver.mobile.core.util.isAppArabic
import com.caregiver.mobile.presentation.common.AppBackButton
import com.caregiver.mobile.presentation.common.AppCard
import com.caregiver.mobile.presentation.common.AppTopBar
import com.caregiver.mobile.presentation.common.BottomActionBar
import com.caregiver.mobile.presentation.common.FieldError
import com.caregiver.mobile.presentation.common.HelperCaption
import com.caregiver.mobile.presentation.common.InfoAlertCard
import com.caregiver.mobile.presentation.common.PrimaryButton
import com.caregiver.mobile.presentation.common.SecondaryButton
import com.caregiver.mobile.presentation.common.SectionCard
import com.caregiver.mobile.presentation.common.SectionTitle
import com.caregiver.mobile.presentation.common.assistedViewModel
import com.caregiver.mobile.presentation.home.LoadFailed
import com.caregiver.mobile.presentation.home.LoadingRow
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** The seven questions the progress bar counts; free text is not one of them. */
internal const val EDITOR_QUESTION_COUNT = 7

/** True when the form holds anything the user would lose by closing. */
internal fun hasUnsavedChanges(state: EditorState): Boolean =
    state.answeredCount() > 0 || state.text.isNotBlank()

/**
 * #915: a form holding answers always asks before any exit is allowed, even
 * though #906 autosaves the draft. The autosaved flag no longer suppresses
 * the prompt — the dialog now explains that the draft is kept, so this is a
 * question about intent, not about losing work. The parameter stays so
 * existing call sites/tests keep the same shape.
 */
internal fun shouldConfirmClose(state: EditorState, autosaved: Boolean = false): Boolean =
    hasUnsavedChanges(state)

/** The numerator always comes from [EditorState.answeredCount], never a literal. */
internal fun editorProgressFraction(answered: Int): Float =
    answered.coerceIn(0, EDITOR_QUESTION_COUNT).toFloat() / EDITOR_QUESTION_COUNT

/** Stable, locale-neutral clock label for the "Draft saved" line. */
internal fun draftTimeLabel(time: LocalTime): String =
    time.format(DateTimeFormatter.ofPattern("HH:mm"))

/**
 * Note editor redesign shell (#901): a round close button, "Note for <name>",
 * a muted line with the time and "Draft saved", a seven-step progress bar,
 * the seven existing question groups each in a [SectionCard] in one column,
 * and a 56 dp Save button pinned above the keyboard.
 *
 * Presentation only: the ViewModel, every field, every option and the
 * validation/error flow are unchanged. The prototype's four-section layout
 * and its extra controls are deliberately not taken.
 */
@Composable
fun NoteEditorScreen(recipientId: String, graph: AppGraph, navController: NavController) {
    // SharedPreferences-backed so an in-progress note survives app restarts.
    // Read LocalContext outside the remember lambda: it is a composable call
    // and cannot run inside a non-composable remembered calculation.
    val context = LocalContext.current
    val drafts = remember { NoteDraftStore.create(context.applicationContext) }
    val vm: NoteEditorViewModel = assistedViewModel("editor-$recipientId") {
        NoteEditorViewModel(recipientId, graph.apis, graph.auth, drafts = drafts)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    state.savedId?.let { noteId ->
        LaunchedEffect(noteId) {
            navController.navigate("note-saved/$noteId") {
                popUpTo("note-editor/$recipientId") { inclusive = true }
            }
        }
    }

    // #915: advertise the dirty state on this destination so the shell's
    // existing bottom-nav tab guard asks before navigating away (Path B).
    // Reuse the shared flag — no shell changes are needed. Once saved, the
    // editor is about to leave, so it must not block a tab tap.
    val dirty = state.savedId == null && hasUnsavedChanges(state)
    val editorEntry = navController.currentBackStackEntry
    LaunchedEffect(editorEntry, dirty) {
        editorEntry?.markUnsavedChanges(dirty)
    }

    // A name lookup is best-effort: the editor must still work if it fails
    // (offline, a deleted person, or a test backend with no get handler).
    var recipientName by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(recipientId) {
        recipientName = runCatching { graph.apis.recipients().get(recipientId).name }.getOrNull()
    }

    var confirmClose by rememberSaveable { mutableStateOf(false) }

    fun requestClose() {
        // #915: every exit path goes through here. #906 keeps the draft, so
        // the prompt is about whether the user means to leave — not a warning
        // that answers will be lost. A clean form still leaves immediately.
        if (shouldConfirmClose(state)) {
            confirmClose = true
        } else {
            navController.popBackStack()
        }
    }

    // #915: system Back is an exit too. Disabled while the dialog is up so the
    // dialog's own back handling dismisses it first (keep editing). The owner
    // guard keeps headless Compose renders without an activity working.
    if (LocalOnBackPressedDispatcherOwner.current != null) {
        BackHandler(enabled = !confirmClose) { requestClose() }
    }

    if (confirmClose) {
        AlertDialog(
            modifier = Modifier.testTag("editor_exit_dialog"),
            onDismissRequest = { confirmClose = false },
            title = {
                Text(
                    stringResource(R.string.unsaved_changes_title),
                    fontFamily = PlexArabic,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    stringResource(R.string.unsaved_changes_message),
                    fontFamily = PlexArabic,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmClose = false
                        navController.popBackStack()
                    },
                ) {
                    Text(stringResource(R.string.unsaved_changes_discard), fontFamily = PlexArabic)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClose = false }) {
                    Text(
                        stringResource(R.string.unsaved_changes_keep_editing),
                        fontFamily = PlexArabic,
                    )
                }
            },
        )
    }

    val draftLine = state.draftSavedAt?.let { savedAt ->
        stringResource(
            R.string.editor_draft_status,
            draftTimeLabel(
                java.time.Instant.ofEpochMilli(savedAt)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalTime(),
            ),
        )
    } ?: stringResource(R.string.editor_draft_saved)
    Column(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Round close + the two-line header. "Today's note" stays as the
            // small eyebrow so the existing screen label is not lost.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = CaregiverColors.AvatarContainer,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("editor_close"),
                ) {
                    IconButton(onClick = ::requestClose, modifier = Modifier.size(48.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.editor_close),
                            tint = CaregiverColors.Ink,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        stringResource(R.string.editor_title),
                        fontFamily = PlexArabic,
                        fontSize = 13.sp,
                        color = CaregiverColors.Muted,
                    )
                    Text(
                        stringResource(
                            R.string.editor_note_for,
                            Bidi.isolate(recipientName ?: stringResource(R.string.editor_title)),
                        ),
                        fontFamily = PlexArabic,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = CaregiverColors.Ink,
                        modifier = Modifier.semantics { heading() },
                    )
                }
            }
            Text(
                draftLine,
                fontFamily = PlexArabic,
                fontSize = 13.sp,
                color = CaregiverColors.Muted,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )

            // Progress bar: seven answered questions; the free-text field is
            // intentionally not counted (see EditorState.answeredCount()).
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.editor_progress_done, state.answeredCount()),
                    fontFamily = PlexArabic,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = CaregiverColors.Ink,
                )
                LinearProgressIndicator(
                    progress = { editorProgressFraction(state.answeredCount()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .testTag("editor_progress"),
                )
                Text(
                    stringResource(R.string.editor_nothing_required),
                    fontFamily = PlexArabic,
                    fontSize = 12.sp,
                    color = CaregiverColors.Muted,
                )
            }

            // The seven existing groups, one SectionCard each, in one column.
            SectionCard {
                GroupLabel(OptionGroup.Mood)
                // #902: three headline tiles plus a disclosure for the
                // clinically meaningful values. All six wire codes stay
                // writable; the editor's toggling select clears on re-tap.
                MoodTiles(
                    selected = state.mood,
                    onSelect = { value -> vm.select(OptionGroup.Mood, value) },
                )
            }
            SectionCard {
                GroupLabel(OptionGroup.Appetite)
                // #903: one row, three segments. The legacy `poor` code is
                // folded to "A little" for display only — stored notes are
                // not migrated, and tapping that segment writes `fair`.
                AppetiteSegments(
                    selected = state.appetite,
                    onSelect = { value -> vm.select(OptionGroup.Appetite, value) },
                )
            }
            SectionCard {
                GroupLabel(OptionGroup.Sleep)
                ChipWrap {
                    NoteOptions.values(OptionGroup.Sleep).forEach { value ->
                        HtmlChip(
                            label = optionLabel(OptionGroup.Sleep, value),
                            selected = state.sleep == value,
                            onClick = { vm.select(OptionGroup.Sleep, value) },
                        )
                    }
                }
            }
            SectionCard {
                GroupLabel(OptionGroup.Mobility)
                ChipWrap {
                    NoteOptions.values(OptionGroup.Mobility).forEach { value ->
                        HtmlChip(
                            label = optionLabel(OptionGroup.Mobility, value),
                            selected = state.mobility == value,
                            onClick = { vm.select(OptionGroup.Mobility, value) },
                        )
                    }
                }
            }
            SectionCard {
                GroupLabel(OptionGroup.Medication)
                // #904: one row, three segments. No per-medication
                // "which one was missed" expansion — that would require a
                // new note-model field, which this UI issue must not add.
                MedicationSegments(
                    selected = state.medication,
                    onSelect = { value -> vm.select(OptionGroup.Medication, value) },
                )
            }
            SectionCard {
                GroupLabelText(stringResource(R.string.editor_fall_question))
                ChipWrap {
                    HtmlChip(
                        label = stringResource(R.string.editor_no),
                        selected = state.fall == false,
                        onClick = { vm.setFall(false) },
                    )
                    HtmlChip(
                        label = stringResource(R.string.editor_yes),
                        selected = state.fall == true,
                        onClick = { vm.setFall(true) },
                    )
                }
            }
            SectionCard {
                GroupLabelText(stringResource(R.string.editor_pain))
                ChipWrap {
                    (0..10).forEach { level ->
                        val selected = state.pain == level
                        val painLevelDescription =
                            stringResource(R.string.editor_pain_level_description, level)
                        OutlinedButton(
                            onClick = { vm.setPain(level) },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                if (selected) 2.dp else 1.dp,
                                if (selected) CaregiverColors.Primary else CaregiverColors.Border,
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (selected) CaregiverColors.PrimarySoft else CaregiverColors.Surface,
                                contentColor = if (selected) CaregiverColors.Primary else CaregiverColors.Ink,
                            ),
                            modifier = Modifier.size(48.dp).semantics {
                                role = Role.RadioButton
                                this.selected = selected
                                contentDescription = painLevelDescription
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        ) {
                            Text(
                                if (selected) "✓ $level" else "$level",
                                fontFamily = PlexArabic,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
                HelperCaption(stringResource(R.string.editor_pain_caption))
            }
            // #905: always-visible optional free-text card with a dictate
            // button. It writes to the same EditorState.text as before, so
            // draft autosave, validation and the stored note are unchanged.
            OptionalNotesCard(
                value = state.text,
                onValueChange = vm::onText,
                modifier = Modifier.fillMaxWidth(),
            )

            state.formError?.let { error ->
                FieldError(
                    when (error) {
                        is EditorError.Rejected -> stringResource(EditorErrorText.res(error.code))
                        EditorError.Unreachable -> stringResource(R.string.auth_error_unreachable)
                        EditorError.ArchivedRecipient ->
                            stringResource(R.string.people_archived_recipient_unavailable)
                        EditorError.NothingAnswered ->
                            stringResource(R.string.editor_error_nothing_answered)
                        is EditorError.DuplicateDay ->
                            stringResource(R.string.editor_error_duplicate_day)
                        else -> ""
                    },
                )
                if (error is EditorError.DuplicateDay) {
                    SecondaryButton(
                        label = stringResource(R.string.editor_duplicate_view),
                        onClick = {
                            navController.navigate("note/${error.existingNoteId}") {
                                popUpTo("note-editor/$recipientId") { inclusive = true }
                            }
                        },
                        height = 52.dp,
                    )
                    SecondaryButton(
                        label = stringResource(R.string.detail_add_correction),
                        onClick = {
                            navController.navigate("addendum/${error.existingNoteId}") {
                                popUpTo("note-editor/$recipientId") { inclusive = true }
                            }
                        },
                        height = 52.dp,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        // Footer stays pinned while the content above scrolls; imePadding
        // lifts it above the keyboard so Save is always reachable.
        Column(modifier = Modifier.imePadding()) {
            BottomActionBar {
                PrimaryButton(
                    label = stringResource(R.string.editor_save),
                    onClick = vm::submit,
                    enabled = !state.busy,
                    height = 56.dp,
                    large = true,
                    modifier = Modifier.testTag("editor_save"),
                )
                HelperCaption(
                    stringResource(R.string.editor_save_note),
                    align = TextAlign.Center,
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChipWrap(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().selectableGroup(),
    ) {
        content()
    }
}

@Composable
private fun HtmlChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = Modifier.semantics { role = Role.RadioButton },
        label = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selected) {
                    Text(
                        "✓",
                        fontFamily = PlexArabic,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clearAndSetSemantics { },
                    )
                }
                Text(label, fontFamily = PlexArabic, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        },
        shape = RoundedCornerShape(AppDimens.CardRadius),
        border = BorderStroke(
            1.dp,
            if (selected) CaregiverColors.Primary else CaregiverColors.Border,
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = CaregiverColors.Surface,
            labelColor = CaregiverColors.Ink,
            selectedContainerColor = CaregiverColors.PrimarySoft,
            selectedLabelColor = CaregiverColors.Primary,
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
        modifier = Modifier.semantics { heading() },
    )
}

/**
 * Saved confirmation (#907): a clear success message naming the person, one
 * primary "Back to Home" action, and a secondary link to the saved note. The
 * existing summary destination is kept as another secondary action.
 *
 * Home and Recipient Detail already refresh on ON_RESUME (#896 / existing
 * detail reload), so returning here updates their "Done" state without a
 * manual refresh — no extra wiring is needed in those screens.
 */
@Composable
fun NoteSavedScreen(noteId: String, graph: AppGraph, navController: NavController) {
    val activity = LocalContext.current as ComponentActivity
    val vm: NoteDetailViewModel = assistedViewModel("note-$noteId", activity) {
        NoteDetailViewModel(noteId, graph.apis, graph.auth)
    }
    val detail by vm.state.collectAsStateWithLifecycle()
    val recipientId = (detail as? NoteDetailState.Content)?.content?.note?.recipientId

    // Best-effort name lookup: the confirmation is still useful if it fails.
    var recipientName by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(recipientId) {
        val id = recipientId ?: return@LaunchedEffect
        recipientName = runCatching { graph.apis.recipients().get(id).name }.getOrNull()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppBackButton(onBack = { navController.popBackStack() })
        }
        Spacer(Modifier.height(80.dp))
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
            text = stringResource(
                R.string.saved_success_message,
                Bidi.isolate(recipientName ?: stringResource(R.string.saved_title)),
            ),
            fontFamily = PlexArabic,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            textAlign = TextAlign.Center,
            color = CaregiverColors.Ink,
            modifier = Modifier.testTag("saved_success_message"),
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        ) {
            PrimaryButton(
                label = stringResource(R.string.saved_back_home),
                onClick = {
                    // Pop everything above Home so it resumes and refreshes
                    // its "Done" state immediately after saving.
                    val home = AppRoutes.tab(MainTab.Home)
                    navController.navigate(home) {
                        popUpTo(home) { inclusive = false }
                        launchSingleTop = true
                    }
                },
                height = 56.dp,
                large = true,
                modifier = Modifier.testTag("saved_back_home"),
            )
            SecondaryButton(
                label = stringResource(R.string.saved_view),
                onClick = { navController.navigate(AppRoutes.noteDetail(noteId)) },
                height = 56.dp,
                modifier = Modifier.testTag("saved_view"),
            )
            SecondaryButton(
                label = stringResource(R.string.saved_summarize),
                onClick = { recipientId?.let { navController.navigate(AppRoutes.summary(it)) } },
                enabled = recipientId != null,
                height = 56.dp,
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
    val state by vm.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        if (state !is NoteDetailState.Content) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                AppTopBar(
                    title = stringResource(R.string.editor_title),
                    onBack = { navController.popBackStack() },
                )
            }
        }
        when (val s = state) {
            NoteDetailState.Loading -> LoadingRow()
            NoteDetailState.Error -> LoadFailed(onRetry = vm::refresh)
            is NoteDetailState.Content -> {
                val arabic = isAppArabic()
                // Stored note and correction text are free-form and may mix
                // Arabic with Latin names, URLs, or numbers. Pin the paragraph
                // direction to the app language so RTL copy keeps its reading
                // order instead of being resolved from the first strong run.
                val textDirection = if (arabic) TextDirection.Rtl else TextDirection.Ltr
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
                    // Only the fields the caregiver actually answered; an
                    // unanswered pain or fall must not render as 0/false.
                    val note = s.content.note
                    val stats = visibleStats(
                        NoteDetailDto(
                            id = note.id,
                            recipientId = note.recipientId,
                            date = note.date,
                            mood = note.mood,
                            appetite = note.appetite,
                            sleep = note.sleep,
                            mobility = note.mobility,
                            medicationTaken = note.medicationTaken,
                            pain = note.pain,
                            fall = note.fall,
                            text = note.text,
                            addenda = s.content.addenda,
                            painRecorded = note.painRecorded,
                            fallRecorded = note.fallRecorded,
                        ),
                    )
                    if (stats.isEmpty()) {
                        HelperCaption(stringResource(R.string.note_nothing_recorded))
                    } else {
                        androidx.compose.foundation.layout.FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            maxItemsInEachRow = 2,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            stats.forEach { field ->
                                when (field) {
                                    StatField.Mood -> StatMini(
                                        stringResource(R.string.editor_mood),
                                        optionLabel(OptionGroup.Mood, s.content.note.mood),
                                    )
                                    StatField.Appetite -> StatMini(
                                        stringResource(R.string.editor_appetite),
                                        optionLabel(OptionGroup.Appetite, s.content.note.appetite),
                                    )
                                    StatField.Sleep -> StatMini(
                                        stringResource(R.string.editor_sleep),
                                        optionLabel(OptionGroup.Sleep, s.content.note.sleep),
                                    )
                                    StatField.Mobility -> StatMini(
                                        stringResource(R.string.editor_mobility),
                                        optionLabel(OptionGroup.Mobility, s.content.note.mobility),
                                    )
                                    StatField.Medication -> StatMini(
                                        stringResource(R.string.editor_medication),
                                        optionLabel(
                                            OptionGroup.Medication,
                                            s.content.note.medicationTaken,
                                        ),
                                    )
                                    StatField.Pain -> StatMini(
                                        stringResource(R.string.editor_pain),
                                        // A bare "5 / 10" flips to "10 / 5" in an RTL
                                        // paragraph, which misreports the pain score.
                                        Bidi.isolate("${s.content.note.pain} / 10"),
                                    )
                                    StatField.Fall -> StatMini(
                                        stringResource(R.string.editor_fall),
                                        stringResource(
                                            if (s.content.note.fall) R.string.detail_fall_yes
                                            else R.string.detail_fall_no,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                    // Caregiver notes + lock row.
                    SectionTitle(stringResource(R.string.editor_text))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = CaregiverColors.Muted,
                            modifier = Modifier.size(AppDimens.IconSm),
                        )
                        Text(
                            stringResource(R.string.note_original_locked),
                            fontFamily = PlexArabic,
                            fontSize = 13.sp,
                            color = CaregiverColors.Muted,
                        )
                    }
                    if (s.content.note.text.isNotBlank()) {
                        Text(
                            s.content.note.text,
                            style = TextStyle(textDirection = textDirection),
                            fontFamily = PlexArabic,
                            fontSize = 16.sp,
                            lineHeight = 29.sp,
                            color = CaregiverColors.Ink,
                        )
                    }
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
                                    // Creation date when the backend value
                                    // parses; omitted otherwise so no
                                    // misleading date is ever shown.
                                    DateFormats.addendumDate(addendum.createdAt, arabic)?.let { dated ->
                                        Text(
                                            dated,
                                            fontFamily = PlexArabic,
                                            fontSize = 13.sp,
                                            color = CaregiverColors.Muted,
                                        )
                                    }
                                    Text(
                                        addendum.text,
                                        style = TextStyle(textDirection = textDirection),
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
    val form by vm.addendum.collectAsStateWithLifecycle()
    if (form.appended) {
        LaunchedEffect(Unit) {
            vm.consumeAppended()
            navController.popBackStack()
        }
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
                shape = RoundedCornerShape(AppDimens.ControlRadius),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CaregiverColors.Primary,
                    unfocusedBorderColor = CaregiverColors.Border,
                    focusedContainerColor = CaregiverColors.Surface,
                    unfocusedContainerColor = CaregiverColors.Surface,
                ),
                modifier = Modifier.fillMaxWidth().height(150.dp),
            )
            if (form.archivedRecipient) {
                FieldError(stringResource(R.string.people_archived_recipient_unavailable))
            } else if (form.sendFailed) {
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
