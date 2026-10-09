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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.navigation.AppDestinations
import com.caregiver.mobile.core.navigation.AppRoutes
import com.caregiver.mobile.core.navigation.MainTab
import com.caregiver.mobile.core.theme.AppSizes
import com.caregiver.mobile.core.theme.AppSpacing
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.AppFontFamily
import com.caregiver.mobile.data.api.RecipientDto
import com.caregiver.mobile.presentation.common.AppCard
import com.caregiver.mobile.presentation.common.AppEmptyState
import com.caregiver.mobile.presentation.common.AppTopBar
import com.caregiver.mobile.presentation.common.Avatar
import com.caregiver.mobile.presentation.common.GrayPill
import com.caregiver.mobile.presentation.common.GreenPill
import com.caregiver.mobile.presentation.common.PrimaryButton
import com.caregiver.mobile.presentation.common.SecondaryButton
import com.caregiver.mobile.presentation.common.SectionTitle
import com.caregiver.mobile.presentation.common.assistedViewModel
import com.caregiver.mobile.presentation.home.LoadFailed
import com.caregiver.mobile.presentation.home.LoadingRow
import com.caregiver.mobile.presentation.notes.OptionGroup
import com.caregiver.mobile.presentation.notes.OptionLabels
import java.util.Locale

/**
 * Recipients list (board 3) with the add-person entry point. The list shares
 * one activity-scoped [RecipientsViewModel] with the add form below, so a
 * creation refreshes exactly the list the back stack returns to; entry
 * refreshes cover sign-out/sign-in turnover on the same activity.
 *
 * HTML: h1 22sp + subtitle 13sp, full-width secondary add button,
 * person cards (avatar 48 + name + last-note 13sp), bottom nav الأشخاص.
 */
@Composable
fun RecipientsScreen(graph: AppGraph, navController: NavController) {
    val activity = LocalContext.current as androidx.activity.ComponentActivity
    val vm: RecipientsViewModel = assistedViewModel("people-shared", activity) {
        RecipientsViewModel(graph.apis, graph.auth)
    }
    LaunchedEffect(Unit) { vm.refresh() }
    val state by vm.state.collectAsState()
    Column(
        Modifier.fillMaxSize().padding(horizontal = AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        val count = (state as? PeopleState.Content)?.recipients?.size
        Column(modifier = Modifier.padding(top = AppSpacing.md)) {
            Text(
                text = stringResource(R.string.tab_people),
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = CaregiverColors.TextPrimary,
            )
            if (count != null) {
                Text(
                    text = "$count",
                    fontFamily = AppFontFamily,
                    fontSize = 13.sp,
                    color = CaregiverColors.TextSecondary,
                )
            }
        }
        SecondaryButton(
            label = stringResource(R.string.add_title),
            onClick = { navController.navigate(AppDestinations.AddRecipient.base) },
        )
        when (val s = state) {
            PeopleState.Loading -> LoadingRow()
            PeopleState.Error -> LoadFailed(onRetry = vm::refresh)
            is PeopleState.Content -> {
                if (s.recipients.isEmpty()) {
                    AppEmptyState(
                        icon = "👤",
                        title = stringResource(R.string.people_empty),
                        subtitle = stringResource(R.string.tab_people),
                        actionLabel = stringResource(R.string.people_add),
                        onAction = { navController.navigate(AppDestinations.AddRecipient.base) },
                    )
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        items(s.recipients, key = { it.id }) { recipient ->
                            PersonRow(recipient) {
                                navController.navigate(AppRoutes.recipientDetail(recipient.id))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PersonRow(recipient: RecipientDto, onOpen: () -> Unit) {
    AppCard(modifier = Modifier.clickable(onClick = onOpen)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(Bidi.isolate(recipient.name).take(1))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
            ) {
                Text(
                    text = Bidi.isolate(recipient.name),
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = CaregiverColors.TextPrimary,
                )
                Text(
                    text = stringResource(R.string.person_last_note),
                    fontFamily = AppFontFamily,
                    fontSize = 13.sp,
                    color = CaregiverColors.TextSecondary,
                )
            }
            Icon(
                Icons.Filled.ChevronLeft,
                contentDescription = null,
                tint = CaregiverColors.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** Add-person form (missing-page spec): name only, inline required error. */
@Composable
fun AddRecipientScreen(graph: AppGraph, navController: NavController) {
    val activity = LocalContext.current as androidx.activity.ComponentActivity
    val vm: RecipientsViewModel = assistedViewModel("people-shared", activity) {
        RecipientsViewModel(graph.apis, graph.auth)
    }
    val addState by vm.addState.collectAsState()
    addState.addedId?.let {
        LaunchedEffect(it) {
            vm.consumeAdded()
            navController.popBackStack()
        }
    }
    Column(
        Modifier.fillMaxSize().padding(horizontal = AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppTopBar(
            title = stringResource(R.string.add_title),
            onBack = { navController.popBackStack() },
        )
        Text(
            stringResource(R.string.add_name),
            fontFamily = AppFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = CaregiverColors.TextPrimary,
        )
        OutlinedTextField(
            value = addState.name,
            onValueChange = vm::onName,
            isError = addState.nameError != null,
            supportingText = {
                if (addState.nameError != null) {
                    Text(
                        stringResource(R.string.add_error_name),
                        fontFamily = AppFontFamily,
                        color = CaregiverColors.Danger,
                    )
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(AppSpacing.sm),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CaregiverColors.Primary,
                unfocusedBorderColor = CaregiverColors.Border,
                focusedContainerColor = CaregiverColors.Surface,
                unfocusedContainerColor = CaregiverColors.Surface,
            ),
            modifier = Modifier.fillMaxWidth().height(AppSizes.inputHeight).testTag("add_person_name"),
        )
        PrimaryButton(
            label = stringResource(R.string.add_save),
            onClick = vm::add,
            enabled = !addState.busy,
            modifier = Modifier.testTag("add_person_save"),
        )
    }
}

/**
 * Recipient detail (board 4): status card + latest note + 3 design actions.
 * HTML: back + name + age, status card (pill + primary 52dp), latest-note
 * section, 2-col stats (plan/summary), 3 secondary space-between buttons.
 */
@Composable
fun RecipientDetailScreen(recipientId: String, graph: AppGraph, navController: NavController) {
    val vm: RecipientDetailViewModel = assistedViewModel("detail-$recipientId") {
        RecipientDetailViewModel(recipientId, graph.apis, graph.auth)
    }
    val state by vm.state.collectAsState()
    Column(Modifier.fillMaxSize().padding(horizontal = AppSpacing.md)) {
        when (val s = state) {
            DetailState.Loading -> LoadingRow()
            DetailState.Error -> LoadFailed(onRetry = vm::refresh)
            is DetailState.Content -> DetailContent(s.detail, navController)
        }
    }
}

@Composable
private fun DetailContent(detail: RecipientDetail, navController: NavController) {
    val arabic = Locale.getDefault().language == "ar"
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        AppTopBar(
            title = Bidi.isolate(detail.name),
            onBack = { navController.popBackStack() },
        )
        // Status card: pill + primary add-note 52dp.
        AppCard {
            Row(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (detail.lastNote == null) {
                    GrayPill(stringResource(R.string.chip_no_note))
                } else {
                    GreenPill(stringResource(R.string.chip_done))
                }
            }
            PrimaryButton(
                label = stringResource(R.string.detail_add_note),
                onClick = { navController.navigate(AppRoutes.noteEditor(detail.id)) },
                height = AppSizes.inputHeight,
            )
        }
        // Latest note section.
        detail.lastNote?.let { note ->
            SectionTitle(stringResource(R.string.person_last_note))
            Text(
                text = OptionLabels.label(OptionGroup.Mood, note.mood, arabic) + " · " +
                    OptionLabels.label(OptionGroup.Appetite, note.appetite, arabic) + " · " +
                    OptionLabels.label(OptionGroup.Mobility, note.mobility, arabic),
                fontFamily = AppFontFamily,
                fontSize = 15.sp,
                color = CaregiverColors.TextPrimary,
            )
        }
        // 2-col stats: plan + summary.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AppCard(modifier = Modifier.weight(1f), padding = AppSpacing.sm) {
                Text(
                    stringResource(R.string.detail_plan),
                    fontFamily = AppFontFamily,
                    fontSize = 13.sp,
                    color = CaregiverColors.TextSecondary,
                )
                GreenPill(stringResource(R.string.plan_status_accepted))
            }
            AppCard(modifier = Modifier.weight(1f), padding = AppSpacing.sm) {
                Text(
                    stringResource(R.string.detail_summary),
                    fontFamily = AppFontFamily,
                    fontSize = 13.sp,
                    color = CaregiverColors.TextSecondary,
                )
                Text(
                    "✓",
                    fontFamily = AppFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = CaregiverColors.Primary,
                )
            }
        }
        // 3 design actions: history, summary, plan — secondary space-between.
        ActionButton(R.string.detail_history) {
            navController.navigate(AppRoutes.tab(MainTab.History))
        }
        ActionButton(R.string.detail_summary) {
            navController.navigate(AppRoutes.summary(detail.id))
        }
        ActionButton(R.string.detail_plan) {
            navController.navigate(AppDestinations.Plans.base)
        }
        Spacer(Modifier.height(AppSpacing.xs))
    }
}

@Composable
private fun ActionButton(label: Int, onClick: () -> Unit) {
    androidx.compose.material3.OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(AppSpacing.sm),
        border = androidx.compose.foundation.BorderStroke(AppSizes.borderWidth, CaregiverColors.Border),
        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
            containerColor = CaregiverColors.Surface,
            contentColor = CaregiverColors.TextPrimary,
        ),
        modifier = Modifier.fillMaxWidth().height(AppSpacing.xxl),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(label),
                fontFamily = AppFontFamily,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.ChevronLeft,
                contentDescription = null,
                tint = CaregiverColors.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
