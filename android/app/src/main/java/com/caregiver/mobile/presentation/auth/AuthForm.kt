package com.caregiver.mobile.presentation.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.AutofillNode
import androidx.compose.ui.autofill.AutofillType
import androidx.compose.ui.autofill.LocalAutofill
import androidx.compose.ui.autofill.LocalAutofillTree
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.BuildConfig
import com.caregiver.mobile.R
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.PlexArabic
import com.caregiver.mobile.data.SettingsStore
import com.caregiver.mobile.presentation.common.AppBackButton
import com.caregiver.mobile.presentation.common.AppDimens

/**
 * Shared login/register form in the design language (board 1): hero logo,
 * bordered 52dp inputs, teal 56dp primary button, inline field errors.
 * Renders [AuthUiState] only — all decisions live in [AuthViewModel].
 *
 * HTML: logo 52x52 radius 16 teal, h1 28sp Bold, subtitle 15sp muted,
 * labels 14sp Bold, inputs 52dp border #C5CDD1 radius 12, CTA 56dp 17sp.
 * The hero carries the single heading (#866): "Welcome back" / "Create your
 * account" with its matching subtitle. Callers that can supply [graph] get
 * the working EN | عربي switch; headless previews/tests fall back to a
 * static rendering of the same control.
 *
 * [onBack] adds the shared back control to the header. Register passes it
 * because it is reached from Login; Login is the root and leaves it null.
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
    onDisplayName: (String) -> Unit = {},
    showDisplayName: Boolean = false,
    modifier: Modifier = Modifier,
    onServerSettings: (() -> Unit)? = null,
    onDemo: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    showSwitchMode: Boolean = true,
    showSwitchAccount: Boolean = false,
    onSwitchAccount: (() -> Unit)? = null,
    // #871 wiring is done here in #866. Null keeps headless tests/previews
    // rendering; the real auth screens pass the graph from CaregiverRoot.
    graph: AppGraph? = null,
) {
    // #872: the offline demo entry is hidden in the footer until the logo is
    // long-pressed. rememberSaveable keeps the reveal across rotation.
    var demoRevealed by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(CaregiverColors.Background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        // Top language row — the working EN | عربي switch from #871.
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppDimens.GapForm),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
        ) {
            if (onBack != null) {
                AppBackButton(onBack = onBack)
            }
            if (graph != null) {
                AuthLanguageSwitch(graph = graph)
            } else {
                // Headless tests/previews have no graph; still render the
                // same stateless control so the header layout is unchanged.
                AuthLanguageSwitchRow(
                    language = SettingsStore.DEFAULT_LANGUAGE,
                    onSelect = {},
                )
            }
        }
        // Hero — HTML margin-top 40px, gap 10px. One heading per screen (#866).
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 40.dp),
        ) {
            // #872: the offline demo entry lives behind a long-press on the
            // logo. The tag sits on this wrapper because AuthLogo clears its
            // own semantics, so UI tests can reach the gesture target.
            Box(
                modifier = Modifier
                    .testTag("auth_logo")
                    .pointerInput(Unit) {
                        detectTapGestures(onLongPress = { demoRevealed = true })
                    },
            ) {
                AuthLogo()
            }
            Text(
                text = stringResource(
                    if (mode == AuthMode.Login) R.string.auth_title_login
                    else R.string.auth_title_register,
                ),
                fontFamily = PlexArabic,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 34.sp,
                color = CaregiverColors.Ink,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = stringResource(
                    if (mode == AuthMode.Login) R.string.auth_subtitle_login
                    else R.string.auth_subtitle_register,
                ),
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
            if (mode == AuthMode.Register && showDisplayName) {
                LabeledField(
                    label = stringResource(R.string.auth_name),
                    value = state.displayName,
                    onValueChange = onDisplayName,
                    isError = state.nameError != null,
                    supporting = state.nameError?.let { nameErrorText(it) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next,
                    ),
                    testTag = "auth_field_name",
                    // #870: next field → Next; name is autofillable.
                    modifier = Modifier.authAutofill(
                        listOf(AutofillType.PersonFullName),
                        onDisplayName,
                    ),
                )
            }

            LabeledField(
                label = stringResource(R.string.auth_email),
                value = state.email,
                onValueChange = onEmail,
                isError = state.emailError != null,
                supporting = state.emailError?.let { emailErrorText(it) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    // #870: email → password, so Next.
                    imeAction = ImeAction.Next,
                ),
                testTag = "auth_field_email",
                // #870: Login accepts a username or an email address;
                // Register is strictly an email address.
                modifier = Modifier.authAutofill(
                    if (mode == AuthMode.Login) {
                        listOf(AutofillType.Username, AutofillType.EmailAddress)
                    } else {
                        listOf(AutofillType.EmailAddress)
                    },
                    onEmail,
                ),
            )

            // #868: the eye toggle occupies AuthTextField's trailing slot.
            // rememberSaveable keeps the choice across rotation, and the
            // description always names the *next* action, in the active locale.
            var passwordVisible by rememberSaveable { mutableStateOf(false) }
            val showPasswordLabel = stringResource(R.string.auth_show_password)
            val hidePasswordLabel = stringResource(R.string.auth_hide_password)
            AuthTextField(
                label = stringResource(R.string.auth_password),
                value = state.password,
                onValueChange = onPassword,
                isError = state.passwordError != null,
                supporting = state.passwordError?.let { passwordErrorText(it) },
                visualTransformation = if (passwordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    // #870: after #869 removed the confirm field, password is
                    // the last field on both screens — Done submits.
                    imeAction = ImeAction.Done,
                ),
                onDone = onSubmit,
                testTag = "auth_field_password",
                // #870: sign-in credentials vs. a new password on Register.
                modifier = Modifier.authAutofill(
                    if (mode == AuthMode.Login) {
                        listOf(AutofillType.Password)
                    } else {
                        listOf(AutofillType.NewPassword)
                    },
                    onPassword,
                ),
                trailingIcon = {
                    IconButton(
                        onClick = { passwordVisible = !passwordVisible },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("auth_password_toggle")
                            .semantics {
                                contentDescription = if (passwordVisible) {
                                    hidePasswordLabel
                                } else {
                                    showPasswordLabel
                                }
                            },
                    ) {
                        Icon(
                            imageVector = if (passwordVisible) {
                                Icons.Filled.VisibilityOff
                            } else {
                                Icons.Filled.Visibility
                            },
                            contentDescription = null,
                            tint = CaregiverColors.AuthMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
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
                shape = RoundedCornerShape(AppDimens.ControlRadius),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CaregiverColors.Primary,
                    contentColor = CaregiverColors.Surface,
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

            // #873: the muted coming-soon row, inserted here by #872.
            SocialSignInRow(isRegister = mode == AuthMode.Register)

            if (mode == AuthMode.Login && showSwitchAccount && onSwitchAccount != null) {
                val switchLabel = stringResource(R.string.account_switch)
                TextButton(
                    onClick = onSwitchAccount,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("auth_switch_account")
                        .semantics { contentDescription = switchLabel },
                ) {
                    Text(
                        switchLabel,
                        fontFamily = PlexArabic,
                        color = CaregiverColors.Primary,
                    )
                }
            }

            if (showSwitchMode) {
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
            // Offline demo entry, gated on DEMO_BACKEND (debug + minified).
            // Release uses the real backend and never shows it. Since #872 it
            // is also hidden until the logo is long-pressed (demoRevealed).
            if (shouldShowDemoEntry(BuildConfig.DEMO_BACKEND, onDemo != null) && demoRevealed) {
                TextButton(
                    onClick = { onDemo?.invoke() },
                    modifier = Modifier.testTag("auth_demo_entry"),
                ) {
                    Text(
                        stringResource(R.string.auth_demo),
                        fontFamily = PlexArabic,
                        color = CaregiverColors.Primary,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/**
 * Demo-entry gate, kept pure so both branches are unit-testable: the entry
 * appears only when the build serves the offline demo backend (debug and
 * minified) and a demo handler is actually wired. Release builds use the
 * real backend and never show it, even if a handler were passed.
 */
internal fun shouldShowDemoEntry(demoBackendEnabled: Boolean, hasDemoHandler: Boolean): Boolean =
    demoBackendEnabled && hasDemoHandler

/**
 * #870: register each field with the platform's autofill framework so a
 * password manager can offer to save and fill credentials. Call sites pass
 * the roles that fit the screen (Login uses Username/EmailAddress + Password,
 * Register uses EmailAddress + NewPassword, the name field uses
 * PersonFullName). The filled String is forwarded to the field's existing
 * change handler.
 *
 * Compose 1.7 (BOM 2024.10.00) has neither `ContentType` nor the public
 * `Modifier.autofill` (both landed in 1.8), so this uses the long-standing
 * AutofillNode/LocalAutofill primitives that are present in 1.7. They are
 * deprecated in favour of the newer API, but they are the correct choice for
 * this project's Compose version. The node is registered on composition,
 * positioned so the framework can anchor its popup, and asked to fill when
 * the field takes or loses focus. Purely additive — no visual structure
 * changes.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun Modifier.authAutofill(
    types: List<AutofillType>,
    onValue: (String) -> Unit,
): Modifier {
    val autofill = LocalAutofill.current
    val tree = LocalAutofillTree.current
    val node = remember(types, onValue) {
        AutofillNode(autofillTypes = types, onFill = onValue)
    }
    DisposableEffect(tree, node) {
        tree += node
        onDispose { tree -= node }
    }
    return this
        .onGloballyPositioned { node.boundingBox = it.boundsInWindow() }
        .focusable()
        .onFocusChanged { state ->
            if (state.isFocused) {
                autofill?.requestAutofillForNode(node)
            } else {
                autofill?.cancelAutofillForNode(node)
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
    modifier: Modifier = Modifier,
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
            shape = RoundedCornerShape(AppDimens.ControlRadius),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CaregiverColors.Primary,
                unfocusedBorderColor = CaregiverColors.Border,
                errorBorderColor = CaregiverColors.Danger,
                focusedContainerColor = CaregiverColors.Surface,
                unfocusedContainerColor = CaregiverColors.Surface,
            ),
            modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
        )
    }
}

@Composable
private fun nameErrorText(error: NameError): String = stringResource(
    when (error) {
        NameError.Required -> R.string.auth_error_name_required
        NameError.TooLong -> R.string.auth_error_name_long
    },
)

@Composable
private fun emailErrorText(error: EmailError): String = stringResource(
    when (error) {
        EmailError.Required -> R.string.auth_error_email_required
        EmailError.Invalid -> R.string.auth_error_email_invalid
    },
)

@Composable
private fun passwordErrorText(error: PasswordError): String = when (error) {
    PasswordError.Required -> stringResource(R.string.auth_error_password_required)
    PasswordError.TooLong -> stringResource(R.string.auth_error_password_long)
    is PasswordError.Policy -> stringResource(
        passwordProblemRes(error.problem),
        *passwordProblemArgs(error.problem),
    )
}

@Composable
private fun formErrorText(error: FormError): String = stringResource(
    when (error) {
        FormError.InvalidCredentials -> R.string.auth_error_invalid_credentials
        FormError.DuplicateEmail -> R.string.auth_error_duplicate_email
        FormError.Unreachable -> R.string.auth_error_unreachable
        FormError.Generic -> R.string.auth_error_generic
    },
)
