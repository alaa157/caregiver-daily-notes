package com.caregiver.mobile.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.caregiver.mobile.R
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.PlexArabic
import com.caregiver.mobile.data.local.MedicationEntity
import com.caregiver.mobile.data.local.MedicationStore
import com.caregiver.mobile.presentation.common.AppCard
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The active medication list plus which of them are marked taken today. */
data class MedicationsState(
    val meds: List<MedicationEntity> = emptyList(),
    val takenToday: Set<String> = emptySet(),
)

/** A person row: feeds the card recipient names and, when needed, the add picker. */
data class MedicationsPerson(val id: String, val name: String)

/**
 * Backs the home medication card: every recipient's tracked medications
 * plus today's taken marks. Write paths resolve the date per action, so a
 * device open past midnight marks the day the tap happened on.
 */
class MedicationViewModel(private val store: MedicationStore) : ViewModel() {

    val state: StateFlow<MedicationsState> =
        combine(
            store.observeActive(),
            store.observeIntakes(MedicationStore.today().toString()),
        ) { meds, intakes ->
            MedicationsState(
                meds = meds,
                takenToday = intakes.asSequence().filter { it.times > 0 }.map { it.medicationId }.toSet(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MedicationsState())

    fun add(recipientId: String, name: String, dose: String?, schedule: String?) =
        viewModelScope.launch { store.add(recipientId, name, dose, schedule) }

    fun update(med: MedicationEntity, name: String, dose: String?, schedule: String?) =
        viewModelScope.launch {
            store.update(
                med.copy(
                    name = name.trim(),
                    dose = dose?.trim()?.takeIf { it.isNotEmpty() },
                    schedule = schedule?.trim()?.takeIf { it.isNotEmpty() },
                ),
            )
        }

    fun delete(id: String) = viewModelScope.launch { store.delete(id) }

    fun setTaken(id: String, taken: Boolean) =
        viewModelScope.launch { store.setTaken(id, MedicationStore.today(), taken) }
}

/**
 * The home medication card: one row per tracked medication with a daily
 * taken/un-taken mark that the store keys to today's local date, plus the
 * editor dialog behind the + and per-row menus. Person names appear only
 * when the household tracks more than one; the add picker too.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicationsCard(
    state: MedicationsState,
    people: List<MedicationsPerson>,
    onSetTaken: (id: String, taken: Boolean) -> Unit,
    onAdd: (recipientId: String, name: String, dose: String?, schedule: String?) -> Unit,
    onUpdate: (entity: MedicationEntity, name: String, dose: String?, schedule: String?) -> Unit,
    onDelete: (id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MedicationEntity?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }

    AppCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_medications_header"),
        ) {
            Text(
                text = stringResource(R.string.home_medications),
                fontFamily = PlexArabic,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = CaregiverColors.Ink,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { showAdd = true }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.home_medications_add),
                    modifier = Modifier.testTag("home_medications_add"),
                )
            }
        }

        if (state.meds.isEmpty()) {
            Text(
                text = stringResource(R.string.home_medications_empty),
                fontSize = 12.sp,
                color = CaregiverColors.Muted,
            )
        } else {
            val showPerson = people.size > 1
            state.meds.forEach { med ->
                val taken = med.id in state.takenToday
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = med.name,
                            fontSize = 14.sp,
                            color = CaregiverColors.Ink,
                        )
                        val detail = listOfNotNull(med.dose, med.schedule).joinToString(" · ")
                        if (detail.isNotEmpty()) {
                            Text(
                                text = detail,
                                fontSize = 12.sp,
                                color = CaregiverColors.Muted,
                            )
                        }
                        if (showPerson) {
                            val personName = people.firstOrNull { it.id == med.recipientId }?.name
                            if (personName != null) {
                                Text(
                                    text = personName,
                                    fontSize = 12.sp,
                                    color = CaregiverColors.Muted,
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { onSetTaken(med.id, !taken) },
                        modifier = Modifier.testTag("home_medications_take_${med.id}"),
                    ) {
                        Icon(
                            imageVector = if (taken) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                            contentDescription = stringResource(
                                if (taken) R.string.home_medications_taken else R.string.home_medications_not_taken,
                            ),
                            tint = if (taken) MaterialTheme.colorScheme.primary else CaregiverColors.Muted,
                        )
                    }
                    Box {
                        IconButton(
                            onClick = { menuFor = med.id },
                            modifier = Modifier.testTag("home_medications_menu_${med.id}"),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.home_medications_menu),
                            )
                        }
                        DropdownMenu(
                            expanded = menuFor == med.id,
                            onDismissRequest = { menuFor = null },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home_medications_edit)) },
                                onClick = {
                                    menuFor = null
                                    editing = med
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.home_medications_delete)) },
                                onClick = {
                                    menuFor = null
                                    onDelete(med.id)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAdd || editing != null) {
        val initial = editing
        MedEditorDialog(
            title = stringResource(if (initial == null) R.string.home_medications_add else R.string.home_medications_edit),
            initialName = initial?.name.orEmpty(),
            initialDose = initial?.dose.orEmpty(),
            initialSchedule = initial?.schedule.orEmpty(),
            personOptions = if (initial == null) people else emptyList(),
            defaultPersonId = people.firstOrNull()?.id.orEmpty(),
            onDismiss = {
                showAdd = false
                editing = null
            },
            onSave = { personId, name, dose, schedule ->
                if (initial == null) {
                    onAdd(personId, name, dose, schedule)
                } else {
                    onUpdate(initial, name, dose, schedule)
                }
                showAdd = false
                editing = null
            },
        )
    }
}

/**
 * Add/edit dialog: name is required, dose and schedule are optional, and the
 * recipient picker only renders when the household tracks several people.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedEditorDialog(
    title: String,
    initialName: String,
    initialDose: String,
    initialSchedule: String,
    personOptions: List<MedicationsPerson>,
    defaultPersonId: String,
    onDismiss: () -> Unit,
    onSave: (recipientId: String, name: String, dose: String?, schedule: String?) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var dose by remember { mutableStateOf(initialDose) }
    var whenTaken by remember { mutableStateOf(initialSchedule) }
    var personId by remember { mutableStateOf(defaultPersonId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.home_medications_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_medications_name_input"),
                )
                OutlinedTextField(
                    value = dose,
                    onValueChange = { dose = it },
                    label = { Text(stringResource(R.string.home_medications_dose)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_medications_dose_input"),
                )
                OutlinedTextField(
                    value = whenTaken,
                    onValueChange = { whenTaken = it },
                    label = { Text(stringResource(R.string.home_medications_when)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("home_medications_when_input"),
                )
                if (personOptions.size > 1) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        personOptions.forEach { person ->
                            FilterChip(
                                selected = person.id == personId,
                                onClick = { personId = person.id },
                                label = { Text(person.name) },
                                modifier = Modifier.testTag("home_medications_person_${person.id}"),
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(personId, name.trim(), dose.trim().takeIf { it.isNotEmpty() }, whenTaken.trim().takeIf { it.isNotEmpty() })
                },
                enabled = name.trim().isNotEmpty(),
            ) {
                Text(stringResource(R.string.add_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}
