package com.caregiver.mobile.presentation.recipients

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.navigation.AppDestinations
import com.caregiver.mobile.data.api.RecipientDto
import com.caregiver.mobile.presentation.common.assistedViewModel
import com.caregiver.mobile.presentation.home.LoadFailed
import com.caregiver.mobile.presentation.home.LoadingRow

/** Recipients list (board 3) with the add-person entry point. */
@Composable
fun RecipientsScreen(graph: AppGraph, navController: NavController) {
    val vm: RecipientsViewModel = assistedViewModel("people") {
        RecipientsViewModel(graph.apis, graph.auth)
    }
    val state by vm.state.collectAsState()
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = { navController.navigate(AppDestinations.AddRecipient.base) }) {
            Text(stringResource(R.string.people_add))
        }
        Spacer(Modifier.height(12.dp))
        when (val s = state) {
            PeopleState.Loading -> LoadingRow()
            PeopleState.Error -> LoadFailed(onRetry = vm::refresh)
            is PeopleState.Content -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(s.recipients, key = { it.id }) { recipient ->
                    PersonRow(recipient) {
                        navController.navigate("recipient/${recipient.id}")
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonRow(recipient: RecipientDto, onOpen: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Text(
            text = recipient.name,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(12.dp),
        )
    }
}

/** Add-person form (missing-page spec): name only, inline required error. */
@Composable
fun AddRecipientScreen(graph: AppGraph, navController: NavController) {
    val vm: RecipientsViewModel = assistedViewModel("add-recipient") {
        RecipientsViewModel(graph.apis, graph.auth)
    }
    val addState by vm.addState.collectAsState()
    addState.addedId?.let {
        LaunchedEffect(it) {
            vm.consumeAdded()
            navController.popBackStack()
        }
    }
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text(
            text = stringResource(R.string.add_title),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = addState.name,
            onValueChange = vm::onName,
            label = { Text(stringResource(R.string.add_name)) },
            isError = addState.nameError != null,
            supportingText = {
                if (addState.nameError != null) {
                    Text(stringResource(R.string.add_error_name))
                }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = vm::add,
            enabled = !addState.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.add_save))
        }
    }
}

/** Recipient detail (board 4): header, latest note, four design actions. */
@Composable
fun RecipientDetailScreen(recipientId: String, graph: AppGraph, navController: NavController) {
    val vm: RecipientDetailViewModel = assistedViewModel("detail-$recipientId") {
        RecipientDetailViewModel(recipientId, graph.apis, graph.auth)
    }
    val state by vm.state.collectAsState()
    when (val s = state) {
        DetailState.Loading -> LoadingRow()
        DetailState.Error -> LoadFailed(onRetry = { navController.popBackStack() })
        is DetailState.Content -> DetailContent(s.detail, navController)
    }
}

@Composable
private fun DetailContent(detail: RecipientDetail, navController: NavController) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = detail.name, style = MaterialTheme.typography.headlineSmall)
        detail.lastNote?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.person_last_note) +
                    ": ${it.appetite} · ${it.sleep}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(16.dp))
        // History lands on the tab for now; per-recipient filtering arrives in Task 6.
        ActionButton(R.string.detail_add_note) {
            navController.navigate("note-editor/${detail.id}")
        }
        ActionButton(R.string.detail_history) { navController.navigate("history") }
        ActionButton(R.string.detail_plan) {
            navController.navigate(AppDestinations.Plans.base)
        }
        ActionButton(R.string.detail_summary) {
            navController.navigate("summary/${detail.id}")
        }
    }
}

@Composable
private fun ActionButton(label: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(label))
        }
    }
}
