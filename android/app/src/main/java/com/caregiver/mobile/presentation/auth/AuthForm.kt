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
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.PlexArabic

/**
 * Shared login/register form in the design language (board 1): hero logo,
 * bordered 52dp inputs, teal 56dp primary button, inline field errors.
 * Renders [AuthUiState] only — all decisions live in [AuthViewModel].
 *
 * HTML: logo 56x56 radius 16 teal, h1 28sp Bold, subtitle 15sp muted,
 * labels 14sp Bold, inputs 52dp border #C5CDD1 radius 12, CTA 56dp 17sp.
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
            .padding(horizontal = 16.dp),
    ) {
        // Top language row — HTML secondary small 44dp button.
        // Language switching lives in Settings; this mirrors the board
        // affordance without hijacking the auth mode switch below.
        Row(
            horizontalArrangement = Arrangement.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            OutlinedButton(
                onClick = {},
                modifier = Modifier.heightIn(min = 44.dp).height(44.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("English", fontFamily = PlexArabic, fontWeight = FontWeight.Bold)
            }
        }
        // Hero — HTML margin-top 40px, gap 10px.
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(56.dp)
                    .background(CaregiverColors.Primary, RoundedCornerShape(16.dp)),
            ) {
                Text("♥", fontSize = 28.sp, color = Color.White)
            }
            Text(
                text = stringResource(R.string.login_hero_title),
                fontFamily = PlexArabic,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                color = CaregiverColors.Ink,
            )
            Text(
                text = stringResource(R.string.login_hero_subtitle),
                fontFamily = PlexArabic,
                fontSize = 15.sp,
                color = CaregiverColors.Muted,
            )
        }
        // Form — HTML margin-top 24px, gap 14px.
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp, bottom = 24.dp),
        ) {
            Text(
                text = stringResource(
                    if (mode == AuthMode.Login) R.string.auth_title_login
                    else R.string.auth_title_register,
                ),
                fontFamily = PlexArabic,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = CaregiverColors.Ink,
            )

            LabeledField(
                label = stringResource(R.string.auth_email),
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
                label = stringResource(R.string.auth_password),
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
                    label = stringResource(R.string.auth_confirm),
                    value = state.confirm,
                    onValueChange = onConfirm,
                    isError = state.confirmError != null,
                    supporting = if (state.confirmError != null) {
                        stringResource(R.string.auth_error_confirm_mismatch)
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
                    fontFamily = PlexArabic,
                    fontSize = 14.sp,
                    color = CaregiverColors.Danger,
                )
            }

            Button(
                onClick = onSubmit,
                enabled = !state.busy,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CaregiverColors.Primary,
                    contentColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).height(56.dp),
            ) {
                Text(
                    stringResource(
                        if (mode == AuthMode.Login) R.string.auth_submit_login
                        else R.string.auth_submit_register,
                    ),
                    fontFamily = PlexArabic,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                )
            }

            TextButton(onClick = onSwitchMode) {
                Text(
                    stringResource(
                        if (mode == AuthMode.Login) R.string.auth_no_account
                        else R.string.auth_have_account,
                    ),
                    fontFamily = PlexArabic,
                    color = CaregiverColors.Primary,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (onServerSettings != null) {
                TextButton(onClick = onServerSettings) {
                    Text(
                        stringResource(R.string.auth_server_link),
                        fontFamily = PlexArabic,
                        color = CaregiverColors.Primary,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            fontFamily = PlexArabic,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = CaregiverColors.Ink,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            isError = isError,
            supportingText = { supporting?.let { Text(it, fontFamily = PlexArabic) } },
            visualTransformation = if (visual) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            keyboardOptions = keyboardOptions,
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CaregiverColors.Primary,
                unfocusedBorderColor = CaregiverColors.Border,
                errorBorderColor = CaregiverColors.Danger,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
            ),
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
        )
    }
}

@Composable
private fun emailErrorText(error: EmailError): String = stringResource(
    when (error) {
        EmailError.Required -> R.string.auth_error_email_required
        EmailError.Invalid -> R.string.auth_error_email_invalid
    },
)

@Composable
private fun passwordErrorText(error: PasswordError): String = stringResource(
    when (error) {
        PasswordError.Required -> R.string.auth_error_password_required
        PasswordError.TooLong -> R.string.auth_error_password_long
    },
)

@Composable
private fun formErrorText(error: FormError): String = stringResource(
    when (error) {
        FormError.InvalidCredentials -> R.string.auth_error_invalid_credentials
        FormError.DuplicateEmail -> R.string.auth_error_duplicate_email
        FormError.Unreachable -> R.string.auth_error_unreachable
        FormError.Generic -> R.string.auth_error_generic
    },
)
