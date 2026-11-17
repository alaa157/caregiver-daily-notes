package com.caregiver.mobile.presentation.auth

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.mergeDescendants
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.PlexArabic
import com.caregiver.mobile.presentation.common.AppDimens

const val AuthPrimaryButtonTestTag = "auth_primary_button"
const val AuthSubmitProgressTestTag = "auth_submit_progress"

/**
 * Auth redesign shared submit button (#875): 56 dp tall, 16 dp radius, teal,
 * 17 sp bold. While [busy] it is disabled so a double tap cannot submit twice,
 * and a small indeterminate progress indicator is shown inside it. That
 * indicator exposes indeterminate-progress semantics, so screen readers
 * announce the busy state, and the label stays as the button's accessible
 * name. The white content colour is fixed so the button reads the same in
 * light and dark themes.
 *
 * [busy] is a parameter rather than a read of `AuthUiState`, keeping the
 * composable previewable and testable on its own. It is used by both Login and
 * Register; wiring it into the form is #874.
 */
@Composable
fun AuthPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
) {
    Button(
        onClick = onClick,
        enabled = !busy,
        shape = RoundedCornerShape(AppDimens.AuthButtonRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = CaregiverColors.Primary,
            contentColor = Color.White,
        ),
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .testTag(AuthPrimaryButtonTestTag),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (busy) {
                // mergeDescendants keeps the indicator a separate semantics
                // node: its indeterminate progress stays announceable and the
                // test tag remains findable despite the button merging its
                // other children.
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(20.dp)
                        .testTag(AuthSubmitProgressTestTag)
                        .semantics(mergeDescendants = true) { },
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
            }
            Text(
                text = text,
                fontFamily = PlexArabic,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
            )
        }
    }
}
