package com.caregiver.mobile.presentation.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.caregiver.mobile.R
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.PlexArabic
import com.caregiver.mobile.data.api.RecipientDto
import com.caregiver.mobile.presentation.common.AppDimens
import com.caregiver.mobile.presentation.common.AppEmptyState
import com.caregiver.mobile.presentation.common.AppLoadingSkeleton
import com.caregiver.mobile.presentation.common.Avatar
import com.caregiver.mobile.presentation.common.ForwardChevron
import com.caregiver.mobile.presentation.common.SectionTitle

/**
 * X-style recipient picker in a modal bottom sheet: the round FAB's "Add
 * note" opens it unless the care list has exactly one active person, in
 * which case MainScaffold goes straight to the editor.
 *
 * The rows mirror the People tab: [Avatar] + name (Bidi-isolated exactly as
 * the People list does) + a forward chevron. Layout is start/end based, so
 * RTL mirrors automatically. Loading shows [AppLoadingSkeleton] (skeleton
 * bars) and a loaded-but-empty list shows [AppEmptyState] with the existing
 * add-person action.
 *
 * Instrumentation-friendly: sheet tag `recipient_picker_sheet`, rows
 * `picker_recipient_${id}`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipientPickerSheet(
    recipients: List<RecipientDto>,
    loading: Boolean,
    onPick: (String) -> Unit,
    onAddPerson: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CaregiverColors.Surface,
        modifier = Modifier.testTag("recipient_picker_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.ScreenHPadding),
            verticalArrangement = Arrangement.spacedBy(AppDimens.GapForm),
        ) {
            SectionTitle(stringResource(R.string.pick_recipient_title))
            when {
                loading -> AppLoadingSkeleton()
                recipients.isEmpty() -> AppEmptyState(
                    icon = Icons.Filled.PersonAdd,
                    title = stringResource(R.string.pick_recipient_empty_title),
                    subtitle = stringResource(R.string.pick_recipient_empty_body),
                    actionLabel = stringResource(R.string.people_add),
                    onAction = onAddPerson,
                )
                else -> recipients.forEach { recipient ->
                    RecipientOption(recipient) { onPick(recipient.id) }
                }
            }
            Spacer(Modifier.height(AppDimens.GapCard))
        }
    }
}

@Composable
private fun RecipientOption(recipient: RecipientDto, onPick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.GapCard),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPick)
            .padding(vertical = AppDimens.GapForm)
            .testTag("picker_recipient_${recipient.id}"),
    ) {
        Avatar(Bidi.isolate(recipient.name).take(1))
        Text(
            text = Bidi.isolate(recipient.name),
            fontFamily = PlexArabic,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = CaregiverColors.Ink,
            modifier = Modifier.weight(1f),
        )
        ForwardChevron()
    }
}
