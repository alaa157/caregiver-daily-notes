package com.caregiver.mobile.presentation.auth

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.caregiver.mobile.R

/**
 * Shared login/register form in the design language (board 1): bordered
 * inputs, teal primary button, inline field errors, form-level error line.
 * Renders [AuthUiState] only — all decisions live in [AuthViewModel].
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
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(
                if (mode == AuthMode.Login) R.string.auth_title_login
                else R.string.auth_title_register,
            ),
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = state.email,
            onValueChange = onEmail,
            label = { Text(stringResource(R.string.auth_email)) },
            isError = state.emailError != null,
            supportingText = { state.emailError?.let { Text(emailErrorText(it)) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.password,
            onValueChange = onPassword,
            label = { Text(stringResource(R.string.auth_password)) },
            isError = state.passwordError != null,
            supportingText = { state.passwordError?.let { Text(passwordErrorText(it)) } },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        if (mode == AuthMode.Register) {
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.confirm,
                onValueChange = onConfirm,
                label = { Text(stringResource(R.string.auth_confirm)) },
                isError = state.confirmError != null,
                supportingText = {
                    if (state.confirmError != null) {
                        Text(stringResource(R.string.auth_error_confirm_mismatch))
                    }
                },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        state.formError?.let {
            Spacer(Modifier.height(8.dp))
            Text(text = formErrorText(it), color = MaterialTheme.colorScheme.error)
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onSubmit,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    if (mode == AuthMode.Login) R.string.auth_submit_login
                    else R.string.auth_submit_register,
                ),
            )
        }

        TextButton(onClick = onSwitchMode) {
            Text(
                stringResource(
                    if (mode == AuthMode.Login) R.string.auth_no_account
                    else R.string.auth_have_account,
                ),
            )
        }
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
private fun formErrorText(error: FormError): String = when (error) {
    FormError.InvalidCredentials -> stringResource(R.string.auth_error_invalid_credentials)
    FormError.Unreachable -> stringResource(R.string.auth_error_unreachable)
    is FormError.Rejected -> error.message
}
