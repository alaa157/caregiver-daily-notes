package com.caregiver.mobile.presentation.auth

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caregiver.mobile.R
import com.caregiver.mobile.core.theme.AppSizes
import com.caregiver.mobile.core.theme.AppSpacing
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.AppFontFamily

/**
 * Shared login/register form in the design language (board 1): hero logo,
 * bordered 52dp inputs, teal 56dp primary button, inline field errors.
 * Renders [AuthUiState] only — all decisions live in [AuthViewModel].
 *
 * HTML: logo 56x56 radius 16 teal, h1 28sp Bold, subtitle 15sp muted,
 * labels/buttons/inputs follow the token type scale and sizes.
 * The auth heading (تسجيل الدخول / Sign in) is kept for the existing
 * navigation/tests; the hero carries the HTML board title.
 */
@Composable
fun AuthForm(
    state: AuthUiState,
    mode: AuthMode,
    onEmail: (String) -> Unit,
    onPassword: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onSubmit: () -> Unit,
    onSwitchMode: () -> Unit,
    modifier: Modifier = Modifier,
    onServerSettings: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CaregiverColors.Background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = AppSpacing.md),
    ) {
        // Top language row — HTML secondary small 44dp button.
        // Language switching lives in Settings; this mirrors the board
        // affordance without hijacking the auth mode switch below.
        Row(
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppSpacing.md),
        ) {
            OutlinedButton(
                onClick = {},
                modifier = Modifier.heightIn(min = AppSpacing.xxl).height(AppSpacing.xxl),
                shape = RoundedCornerShape(AppSpacing.sm),
            ) {
                Text("English", fontFamily = AppFontFamily, fontWeight = FontWeight.Bold)
            }
        }
        // Hero — HTML margin-top 40px, gap 10px.
        Column(
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppSizes.avatarSize),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(AppSizes.buttonHeightLarge)
                    .background(CaregiverColors.Primary, RoundedCornerShape(AppSpacing.md)),
            ) {
                Text("♥", style = MaterialTheme.typography.displayLarge, color = CaregiverColors.Surface)
            }
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.displayLarge,
                color = CaregiverColors.TextPrimary,
            )
            Text(
                text = stringResource(R.string.auth_login_title),
                style = MaterialTheme.typography.bodyLarge,
                color = CaregiverColors.TextSecondary,
            )
        }
        // Form — HTML margin-top 24px, gap 14px.
        Column(
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = AppSpacing.lg, bottom = AppSpacing.lg),
        ) {
            Text(
                text = stringResource(R.string.auth_login_title),
                style = MaterialTheme.typography.headlineSmall,
                color = CaregiverColors.TextPrimary,
            )

            LabeledField(
                label = stringResource(R.string.auth_login_email),
                value = state.email,
                onValueChange = onEmail,
                isError = state.emailError != null,
                supporting = state.emailError?.let { emailErrorText(it) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                testTag = "auth_field_email",
            )

            LabeledField(
                label = stringResource(R.string.auth_login_password),
                value = state.password,
                onValueChange = onPassword,
                isError = state.passwordError != null,
                supporting = state.passwordError?.let { passwordErrorText(it) },
                visual = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                onDone = onSubmit,
                testTag = "auth_field_password",
            )

            if (mode == AuthMode.Register) {
                LabeledField(
                    label = stringResource(R.string.auth_login_password),
                    value = state.confirm,
                    onValueChange = onConfirm,
                    isError = state.confirmError != null,
                    supporting = if (state.confirmError != null) {
                        stringResource(R.string.state_error)
                    } else {
                        null
                    },
                    visual = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    onDone = onSubmit,
                    testTag = "auth_field_confirm",
                )
            }

            state.formError?.let {
                Text(
                    text = formErrorText(it),
                    style = MaterialTheme.typography.labelLarge,
                    color = CaregiverColors.Danger,
                )
            }

            Button(
                onClick = onSubmit,
                enabled = !state.busy,
                shape = RoundedCornerShape(AppSpacing.sm),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CaregiverColors.Primary,
                    contentColor = CaregiverColors.Surface,
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = AppSizes.buttonHeightLarge).height(AppSizes.buttonHeightLarge).testTag("auth_submit"),
            ) {
                Text(
                    stringResource(R.string.auth_login_submit),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            TextButton(onClick = onSwitchMode, modifier = Modifier.testTag("auth_switch")) {
                Text(
                    stringResource(R.string.auth_login_submit),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = CaregiverColors.Primary,
                )
            }

            if (onServerSettings != null) {
                TextButton(onClick = onServerSettings) {
                    Text(
                        stringResource(R.string.nav_settings),
                        fontFamily = AppFontFamily,
                        color = CaregiverColors.Primary,
                    )
                }
            }
            Spacer(Modifier.height(AppSpacing.xs))
        }
    }
}

@Composable
private fun LabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    supporting: String?,
    keyboardOptions: KeyboardOptions,
    testTag: String,
    visual: Boolean = false,
    onDone: (() -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = CaregiverColors.TextPrimary,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            isError = isError,
            supportingText = { supporting?.let { Text(it, fontFamily = AppFontFamily) } },
            visualTransformation = if (visual) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            singleLine = true,
            shape = RoundedCornerShape(AppSpacing.sm),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CaregiverColors.Primary,
                unfocusedBorderColor = CaregiverColors.Border,
                errorBorderColor = CaregiverColors.Danger,
                focusedContainerColor = CaregiverColors.Surface,
                unfocusedContainerColor = CaregiverColors.Surface,
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = AppSizes.inputHeight),
        )
    }
}

@Composable
private fun emailErrorText(error: EmailError): String = stringResource(
    when (error) {
        EmailError.Required -> R.string.state_error
        EmailError.Invalid -> R.string.state_error
    },
)

@Composable
private fun passwordErrorText(error: PasswordError): String = stringResource(
    when (error) {
        PasswordError.Required -> R.string.state_error
        PasswordError.TooLong -> R.string.state_error
    },
)

@Composable
private fun formErrorText(error: FormError): String = stringResource(
    when (error) {
        FormError.InvalidCredentials -> R.string.state_error
        FormError.DuplicateEmail -> R.string.state_error
        FormError.Unreachable -> R.string.state_error
        FormError.Generic -> R.string.state_error
    },
)
