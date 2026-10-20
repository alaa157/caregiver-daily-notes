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
import com.caregiver.mobile.data.local.doseIndex
import com.caregiver.mobile.presentation.common.AppCard
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The active medication list plus which doses are marked taken today.
 *
 * [takenToday] is the medications with at least one dose taken;
 * [takenDoses] says which dose of which medication, so a twice-daily
 * medication can have its morning dose taken and its evening dose due.
 */
data class MedicationsState(
    val meds: List<MedicationEntity> = emptyList(),
    val takenToday: Set<String> = emptySet(),
    val takenDoses: Map<String, Set<Int>> = emptyMap(),
)

/** A person row: feeds the card recipient names and, when needed, the add picker. */
data class MedicationsPerson(val id: String, val name: String)

/**
 * Backs the home medication card: every recipient's tracked medications
 * plus today's taken marks.
 *
 * "Today" is a state, not a snapshot: a ticker flow re-emits the local date
 * toward midnight and the intake observation re-subscribes to the new date,
 * so an app open past midnight shows the new day's marks without a restart.
 * Taps write to the same resolved date, and the ticker restarts with a
 * fresh read whenever the card is collected again after the 5s grace — a
 * backgrounded app catches up on return. The ticker lives only while the
 * card is visible.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MedicationViewModel(
    private val store: MedicationStore,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private fun today(): LocalDate = LocalDate.now(clock)

    private fun millisPastMidnight(): Long =
        Duration.between(
            clock.instant(),
            today().plusDays(1).atStartOfDay(clock.zone),
        ).toMillis().coerceAtLeast(0) + 1_000

    private val todayFlow: Flow<LocalDate> = flow {
        while (true) {
            emit(today())
            delay(millisPastMidnight())
        }
    }

    val state: StateFlow<MedicationsState> =
        combine(
            store.observeActive(),
            todayFlow.flatMapLatest { day -> store.observeIntakes(day.toString()) },
            todayFlow,
        ) { meds, intakes, _ ->
            val doses = mutableMapOf<String, MutableSet<Int>>()
            for (intake in intakes) {
                if (intake.times <= 0) continue
                val dose = intake.doseIndex()
                if (dose != null) doses.getOrPut(intake.medicationId) { mutableSetOf() } += dose
            }
            MedicationsState(
                meds = meds,
                takenToday = doses.keys.toSet(),
                takenDoses = doses.mapValues { it.value.toSet() },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MedicationsState())

    fun add(
        recipientId: String,
        name: String,
        dose: String?,
        schedule: String?,
        dosesPerDay: Int = 1,
    ) {
        // No person, no medication: a blank recipient would orphan the row
        // (the medications table carries no foreign key by design). The UI
        // disables adding without people; this is the last-resort net.
        if (recipientId.isBlank()) return
        viewModelScope.launch { store.add(recipientId, name, dose, schedule, dosesPerDay) }
    }

    fun update(
        med: MedicationEntity,
        name: String,
        dose: String?,
        schedule: String?,
        dosesPerDay: Int = 1,
    ) =
        viewModelScope.launch {
            store.update(
                med.copy(
                    name = name.trim(),
                    dose = dose?.trim()?.takeIf { it.isNotEmpty() },
                    schedule = schedule?.trim()?.takeIf { it.isNotEmpty() },
                    dosesPerDay = dosesPerDay,
                ),
            )
        }

    fun delete(id: String) = viewModelScope.launch { store.delete(id) }

    fun setTaken(id: String, taken: Boolean) = setTaken(id, 0, taken)

    /** Marks one dose of a medication; each dose of the day is its own mark. */
    fun setTaken(id: String, doseIndex: Int, taken: Boolean) =
        viewModelScope.launch { store.setTaken(id, todayFlow.first(), doseIndex, taken) }
}

/**
 * The home medication card: one row per tracked medication with a daily
 * taken/un-taken mark, plus the editor dialog behind the + and per-row
 * menus. Person names appear only when the household tracks more than one;
 * the add picker too. Adding needs at least one person to belong to, and
 * deleting asks first — a delete also erases the medication's whole taken
 * history.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MedicationsCard(
    state: MedicationsState,
    people: List<MedicationsPerson>,
    onSetTaken: (id: String, doseIndex: Int, taken: Boolean) -> Unit,
    onAdd: (recipientId: String, name: String, dose: String?, schedule: String?, dosesPerDay: Int) -> Unit,
    onUpdate: (entity: MedicationEntity, name: String, dose: String?, schedule: String?, dosesPerDay: Int) -> Unit,
    onDelete: (id: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<MedicationEntity?>(null) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<MedicationEntity?>(null) }

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
            IconButton(onClick = { showAdd = true }, enabled = people.isNotEmpty()) {
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
                    // One mark per dose: a twice-daily medication shows two,
                    // so the morning dose can be taken and the evening dose
                    // still due.
                    repeat(med.dosesPerDay.coerceAtLeast(1)) { doseIndex ->
                        val doseTaken = doseIndex in (state.takenDoses[med.id] ?: emptySet())
                        IconButton(
                            onClick = { onSetTaken(med.id, doseIndex, !doseTaken) },
                            modifier = Modifier.testTag("home_medications_take_${med.id}_$doseIndex"),
                        ) {
                            Icon(
                                imageVector = if (doseTaken) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                                contentDescription = if (med.dosesPerDay <= 1) {
                                    // One dose a day: the plain wording is
                                    // the clearest thing a screen reader
                                    // can say.
                                    stringResource(
                                        if (doseTaken) {
                                            R.string.home_medications_taken
                                        } else {
                                            R.string.home_medications_not_taken
                                        },
                                    )
                                } else {
                                    stringResource(
                                        if (doseTaken) {
                                            R.string.home_medications_dose_taken
                                        } else {
                                            R.string.home_medications_dose_not_taken
                                        },
                                        doseIndex + 1,
                                        med.dosesPerDay,
                                    )
                                },
                                tint = if (doseTaken) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    CaregiverColors.Muted
                                },
                            )
                        }
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
                                    deleting = med
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
            initialDosesPerDay = initial?.dosesPerDay ?: 1,
            personOptions = if (initial == null) people else emptyList(),
            defaultPersonId = people.firstOrNull()?.id.orEmpty(),
            onDismiss = {
                showAdd = false
                editing = null
            },
            onSave = { personId, name, dose, schedule, dosesPerDay ->
                if (initial == null) {
                    onAdd(personId, name, dose, schedule, dosesPerDay)
                } else {
                    onUpdate(initial, name, dose, schedule, dosesPerDay)
                }
                showAdd = false
                editing = null
            },
        )
    }

    deleting?.let { med ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.home_medications_delete_title)) },
            text = {
                Text(stringResource(R.string.home_medications_delete_body, med.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleting = null
                        onDelete(med.id)
                    },
                    modifier = Modifier.testTag("home_medications_delete_confirm"),
                ) {
                    Text(stringResource(R.string.home_medications_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

/**
 * Add/edit dialog: name is required, dose and schedule are optional, and the
 * recipient picker only renders when the household tracks several people.
 * Saving also needs a resolved recipient — with no person in the household
 * the button stays off rather than writing an orphan row.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MedEditorDialog(
    title: String,
    initialName: String,
    initialDose: String,
    initialSchedule: String,
    initialDosesPerDay: Int,
    personOptions: List<MedicationsPerson>,
    defaultPersonId: String,
    onDismiss: () -> Unit,
    onSave: (recipientId: String, name: String, dose: String?, schedule: String?, dosesPerDay: Int) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var dose by remember { mutableStateOf(initialDose) }
    var whenTaken by remember { mutableStateOf(initialSchedule) }
    var dosesPerDay by remember { mutableStateOf(initialDosesPerDay.coerceIn(1, MedicationStore.MAX_DOSES_PER_DAY)) }
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
                // How many doses a day: a twice-daily medication needs two
                // marks, so the caregiver says so once, when adding it.
                Text(
                    text = stringResource(R.string.home_medications_doses_per_day),
                    fontSize = 12.sp,
                    color = CaregiverColors.Muted,
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (1..MedicationStore.MAX_DOSES_PER_DAY).forEach { count ->
                        FilterChip(
                            selected = dosesPerDay == count,
                            onClick = { dosesPerDay = count },
                            label = { Text(stringResource(R.string.home_medications_doses_count, count)) },
                            modifier = Modifier.testTag("home_medications_doses_$count"),
                        )
                    }
                }
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
                    onSave(
                        personId,
                        name.trim(),
                        dose.trim().takeIf { it.isNotEmpty() },
                        whenTaken.trim().takeIf { it.isNotEmpty() },
                        dosesPerDay,
                    )
                },
                enabled = name.trim().isNotEmpty() && personId.isNotEmpty(),
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
