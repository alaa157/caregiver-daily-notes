package com.caregiver.mobile.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.theme.AppRadius
import com.caregiver.mobile.core.theme.AppSizes
import com.caregiver.mobile.core.theme.AppSpacing
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.presentation.common.AppErrorState
import com.caregiver.mobile.presentation.common.Pill
import com.caregiver.mobile.presentation.common.PrimaryButton
import com.caregiver.mobile.presentation.common.assistedViewModel

/**
 * Sign in (spec screen 1, demo variant per Step 6): app name display text,
 * two TextFields, PrimaryButton, inline ErrorState. Layout follows
 * screens.md; the button starts the local demo session (no credentials
 * check, no password stored). Success navigates to Home via the root gate.
 * States: idle, submitting (button spinner), error, success (go to Home).
 */
// DEMO-ONLY: "DEMO" and "Start demo" are non-translated demo chrome, not
// product copy (copy has no demo keys). Flagged in the report.
private const val DEMO_BADGE = "DEMO"
private const val START_DEMO = "Start demo"

@Composable
fun DemoSignInScreen(graph: AppGraph) {
    val vm: DemoSignInViewModel = assistedViewModel("demo-signin") {
        DemoSignInViewModel(graph)
    }
    val state by vm.state.collectAsState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("screen_signin")
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
    ) {
        Pill(DEMO_BADGE, CaregiverColors.PrimarySoft, CaregiverColors.Primary)
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.displayLarge,
            color = CaregiverColors.TextPrimary,
        )
        DemoField(
            label = stringResource(R.string.auth_login_email),
            value = state.email,
            onValueChange = vm::onEmail,
            testTag = "demo_field_email",
        )
        DemoField(
            label = stringResource(R.string.auth_login_password),
            value = state.password,
            onValueChange = vm::onPassword,
            password = true,
            testTag = "demo_field_password",
        )
        if (state.failed) {
            AppErrorState(
                message = stringResource(R.string.state_error),
                onRetry = vm::startDemo,
            )
        }
        Spacer(Modifier.height(AppSpacing.xs))
        PrimaryButton(
            label = START_DEMO,
            onClick = vm::startDemo,
            loading = state.busy,
            height = AppSizes.buttonHeightLarge,
            large = true,
            modifier = Modifier.testTag("demo_start"),
        )
    }
}

@Composable
private fun DemoField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    password: Boolean = false,
    testTag: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        horizontalAlignment = Alignment.Start,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = CaregiverColors.TextPrimary,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            shape = RoundedCornerShape(AppRadius.md),
            visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CaregiverColors.Primary,
                unfocusedBorderColor = CaregiverColors.Border,
                focusedContainerColor = CaregiverColors.Surface,
                unfocusedContainerColor = CaregiverColors.Surface,
            ),
            modifier = Modifier.fillMaxWidth().height(AppSizes.inputHeight).testTag(testTag),
        )
    }
}
