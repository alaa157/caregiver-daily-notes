package com.caregiver.mobile.presentation.auth

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.PlexArabic
import com.caregiver.mobile.presentation.common.AppDimens

private val AuthFieldMinHeight = 60.dp

/**
 * Shared input for the redesigned Login and Register screens.
 *
 * Unlike the old [LabeledField], the label sits inside the outlined
 * container (small and muted), the outline uses the new 16-dp auth radius,
 * the field is at least 60 dp tall, and focus paints the teal primary.
 * Errors keep the form's danger colours and render inline as supporting text.
 *
 * The parameter list mirrors [LabeledField] so call sites can be swapped by
 * renaming the composable; [trailingIcon] and [visualTransformation] are
 * additive slots for the password toggle, and [modifier] carries callers'
 * autofill/keyboard additives.
 */
@Composable
fun AuthTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    supporting: String?,
    keyboardOptions: KeyboardOptions,
    testTag: String,
    visual: Boolean = false,
    onDone: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = {
            Text(
                text = label,
                fontFamily = PlexArabic,
                fontSize = 13.sp,
            )
        },
        supportingText = { supporting?.let { Text(it, fontFamily = PlexArabic) } },
        isError = isError,
        visualTransformation = visualTransformation
            ?: if (visual) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = keyboardOptions,
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        trailingIcon = trailingIcon,
        singleLine = true,
        shape = RoundedCornerShape(AppDimens.AuthFieldRadius),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = CaregiverColors.Primary,
            unfocusedBorderColor = CaregiverColors.AuthFieldBorder,
            errorBorderColor = CaregiverColors.Danger,
            focusedContainerColor = CaregiverColors.Surface,
            unfocusedContainerColor = CaregiverColors.Surface,
            errorContainerColor = CaregiverColors.Surface,
            focusedTextColor = CaregiverColors.Ink,
            unfocusedTextColor = CaregiverColors.Ink,
            errorTextColor = CaregiverColors.Danger,
            cursorColor = CaregiverColors.Primary,
            errorCursorColor = CaregiverColors.Danger,
            focusedLabelColor = CaregiverColors.AuthMuted,
            unfocusedLabelColor = CaregiverColors.AuthMuted,
            errorLabelColor = CaregiverColors.Danger,
            focusedSupportingTextColor = CaregiverColors.AuthMuted,
            unfocusedSupportingTextColor = CaregiverColors.AuthMuted,
            errorSupportingTextColor = CaregiverColors.Danger,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = AuthFieldMinHeight)
            .testTag(testTag),
    )
}
