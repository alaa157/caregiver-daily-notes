package com.caregiver.mobile.presentation.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caregiver.mobile.R
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.PlexArabic
import com.caregiver.mobile.presentation.common.AppDimens

/**
 * The single switch for the whole social row (#873). All providers are
 * "coming soon": the buttons are disabled and carry no callbacks, so the row
 * is ignored by the ViewModel and never starts a network call or auth flow.
 *
 * When a provider really ships, flip this flag and wire its handler — that
 * is the one-line change the issue asks for. Keep the "coming soon" content
 * descriptions in sync at that point.
 */
internal const val SOCIAL_SIGN_IN_AVAILABLE = false

private data class SocialProvider(
    val iconRes: Int,
    val contentDescriptionRes: Int,
)

private val SOCIAL_PROVIDERS = listOf(
    SocialProvider(R.drawable.ic_auth_google, R.string.auth_social_google_cd),
    SocialProvider(R.drawable.ic_auth_github, R.string.auth_social_github_cd),
    SocialProvider(R.drawable.ic_auth_facebook, R.string.auth_social_facebook_cd),
)

/**
 * Muted "or continue with · coming soon" divider plus a row of three disabled
 * brand buttons. Purely presentational: it takes no callbacks and touches no
 * ViewModel state.
 *
 * Standalone on purpose — inserting it into Login/Register is #872's job,
 * which owns the footer region. Uses only the auth tokens added in #864.
 */
@Composable
fun SocialSignInRow(
    isRegister: Boolean,
    modifier: Modifier = Modifier,
) {
    val dividerLabel = stringResource(
        if (isRegister) R.string.auth_social_divider_register
        else R.string.auth_social_divider_login,
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                thickness = AppDimens.Hairline,
                color = CaregiverColors.AuthDivider,
            )
            Text(
                text = dividerLabel,
                fontFamily = PlexArabic,
                fontSize = 12.sp,
                color = CaregiverColors.AuthMuted,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            HorizontalDivider(
                modifier = Modifier.weight(1f),
                thickness = AppDimens.Hairline,
                color = CaregiverColors.AuthDivider,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SOCIAL_PROVIDERS.forEachIndexed { index, provider ->
                if (index > 0) Spacer(Modifier.width(12.dp))
                SocialProviderButton(provider)
            }
        }
    }
}

@Composable
private fun SocialProviderButton(provider: SocialProvider) {
    val description = stringResource(provider.contentDescriptionRes)
    Button(
        onClick = {},
        // Disabled: not clickable, not in the tab order, and not an action
        // for TalkBack — but its content description is still exposed.
        enabled = SOCIAL_SIGN_IN_AVAILABLE,
        shape = RoundedCornerShape(AppDimens.AuthSocialButtonRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = CaregiverColors.AuthDisabledFill,
            contentColor = CaregiverColors.AuthDisabledIcon,
            disabledContainerColor = CaregiverColors.AuthDisabledFill,
            disabledContentColor = CaregiverColors.AuthDisabledIcon,
        ),
        modifier = Modifier.size(width = 64.dp, height = 52.dp),
    ) {
        Image(
            painter = painterResource(provider.iconRes),
            contentDescription = description,
            colorFilter = ColorFilter.tint(CaregiverColors.AuthDisabledIcon),
            modifier = Modifier.size(24.dp),
        )
    }
}
