package com.caregiver.mobile.presentation.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.caregiver.mobile.R
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.presentation.common.AppDimens

// Auth redesign logo geometry (#865). No design token covers the square or
// mark size, so these stay local to the mark; the radius (16 dp) and colour
// (teal) come from the #864 tokens below. Keeping them here makes AuthLogo
// self-contained, with no reference to AuthForm.
private val AuthLogoSquare = 52.dp
private val AuthLogoMark = 38.dp

/**
 * Reusable auth hero mark: the Rafeek "Gather" symbol (four white arc-fragments
 * with a clay centre dot) on a 52 dp teal rounded square (16 dp radius). The
 * teal is the shared primary role — it is identical in light and dark themes —
 * and the symbol is baked white in the drawable, so the mark renders the same
 * on every device and font.
 *
 * Purely decorative: the brand meaning is carried by the screen's heading, so
 * the whole mark is cleared from the semantics tree rather than given a
 * non-localized content description. Wiring it into the hero is done in #866.
 */
@Composable
fun AuthLogo(modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(AuthLogoSquare)
            .background(CaregiverColors.Primary, RoundedCornerShape(AppDimens.AuthFieldRadius))
            .clearAndSetSemantics { },
    ) {
        Image(
            painter = painterResource(R.drawable.ic_auth_mark),
            contentDescription = null,
            modifier = Modifier.size(AuthLogoMark),
        )
    }
}
