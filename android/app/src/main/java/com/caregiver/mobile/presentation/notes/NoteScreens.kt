package com.caregiver.mobile.presentation.notes

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.presentation.common.assistedViewModel
import com.caregiver.mobile.presentation.home.LoadFailed
import com.caregiver.mobile.presentation.home.LoadingRow
import java.util.Locale

/** Note editor (board 5): chip groups, fall switch, pain stepper, free text. */
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
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = stringResource(R.string.editor_title), style = MaterialTheme.typography.headlineSmall)
        OptionGroup.entries.forEach { group ->
            GroupLabel(group)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NoteOptions.values(group).forEach { value ->
                    val selected = when (group) {
                        OptionGroup.Mood -> state.mood == value
                        OptionGroup.Appetite -> state.appetite == value
                        OptionGroup.Sleep -> state.sleep == value
                        OptionGroup.Mobility -> state.mobility == value
                        OptionGroup.Medication -> state.medication == value
                    }
                    FilterChip(
                        selected = selected,
                        onClick = { vm.select(group, value) },
                        label = { Text(OptionLabels.label(group, value, arabic)) },
                    )
                }
            }
            if (group == OptionGroup.Mood && state.moodError != null) {
                Text(
                    text = stringResource(R.string.editor_error_mood),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.editor_fall), modifier = Modifier.weight(1f))
            Switch(checked = state.fall, onCheckedChange = vm::setFall)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = stringResource(R.string.editor_pain), modifier = Modifier.weight(1f))
            OutlinedButton(onClick = { vm.setPain(state.pain - 1) }) { Text("−") }
            Text(text = state.pain.toString(), modifier = Modifier.padding(horizontal = 12.dp))
            OutlinedButton(onClick = { vm.setPain(state.pain + 1) }) { Text("+") }
        }
        Text(
            text = stringResource(R.string.editor_pain_caption),
            style = MaterialTheme.typography.bodySmall,
        )
        OutlinedTextField(
            value = state.text,
            onValueChange = vm::onText,
            label = { Text(stringResource(R.string.editor_text)) },
            isError = state.textError != null,
            supportingText = {
                if (state.textError != null) {
                    Text(stringResource(R.string.editor_error_text))
                }
            },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        state.formError?.let {
            Text(
                text = when (it) {
                    is EditorError.Rejected -> it.message
                    EditorError.Unreachable -> stringResource(R.string.auth_error_unreachable)
                    else -> ""
                },
                color = MaterialTheme.colorScheme.error,
            )
        }
        Button(onClick = vm::submit, enabled = !state.busy, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.editor_save))
        }
        Text(
            text = stringResource(R.string.editor_save_note),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun GroupLabel(group: OptionGroup) {
    Text(
        text = stringResource(
            when (group) {
                OptionGroup.Mood -> R.string.editor_mood
                OptionGroup.Appetite -> R.string.editor_appetite
                OptionGroup.Sleep -> R.string.editor_sleep
                OptionGroup.Mobility -> R.string.editor_mobility
                OptionGroup.Medication -> R.string.editor_medication
            },
        ),
        style = MaterialTheme.typography.titleMedium,
    )
}

/** Saved confirmation (board 6): view the note or jump to a summary. */
@Composable
fun NoteSavedScreen(noteId: String, graph: AppGraph, navController: NavController) {
    val activity = LocalContext.current as ComponentActivity
    val vm: NoteDetailViewModel = assistedViewModel("note-$noteId", activity) {
        NoteDetailViewModel(noteId, graph.apis, graph.auth)
    }
    val detail by vm.state.collectAsState()
    val recipientId = (detail as? NoteDetailState.Content)?.content?.note?.recipientId
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(text = stringResource(R.string.saved_title), style = MaterialTheme.typography.headlineSmall)
        Button(
            onClick = { navController.navigate("note/$noteId") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.saved_view))
        }
        Button(
            onClick = { recipientId?.let { navController.navigate("summary/$it") } },
            enabled = recipientId != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.saved_summarize))
        }
    }
}

/** Note detail (board 7): immutable original card plus appended corrections. */
@Composable
fun NoteDetailScreen(noteId: String, graph: AppGraph, navController: NavController) {
    val activity = LocalContext.current as ComponentActivity
    val vm: NoteDetailViewModel = assistedViewModel("note-$noteId", activity) {
        NoteDetailViewModel(noteId, graph.apis, graph.auth)
    }
    val state by vm.state.collectAsState()
    when (val s = state) {
        NoteDetailState.Loading -> LoadingRow()
        NoteDetailState.Error -> LoadFailed(onRetry = vm::refresh)
        is NoteDetailState.Content -> {
            val arabic = Locale.getDefault().language == "ar"
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(text = s.content.note.text, style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = listOf(
                                OptionLabels.label(OptionGroup.Mood, s.content.note.mood, arabic),
                                OptionLabels.label(OptionGroup.Appetite, s.content.note.appetite, arabic),
                                OptionLabels.label(OptionGroup.Sleep, s.content.note.sleep, arabic),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.detail_corrections),
                    style = MaterialTheme.typography.titleMedium,
                )
                s.content.addenda.forEach { addendum ->
                    Card(Modifier.fillMaxWidth()) {
                        Text(text = addendum.text, modifier = Modifier.padding(12.dp))
                    }
                }
                OutlinedButton(
                    onClick = { navController.navigate("addendum/$noteId") },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.detail_add_correction))
                }
            }
        }
    }
}

/** Addendum form (board 8): shares the detail VM so the list refreshes below. */
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
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = stringResource(R.string.addendum_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = form.text,
            onValueChange = vm::onAddendumText,
            label = { Text(stringResource(R.string.addendum_text)) },
            isError = form.addendumError,
            supportingText = {
                if (form.addendumError) {
                    Text(stringResource(R.string.addendum_error))
                }
            },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = vm::submitAddendum,
            enabled = !form.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.addendum_save))
        }
    }
}
