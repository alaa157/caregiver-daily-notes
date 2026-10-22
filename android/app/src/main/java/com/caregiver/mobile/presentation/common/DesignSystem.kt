package com.caregiver.mobile.presentation.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caregiver.mobile.R
import com.caregiver.mobile.core.theme.AppSpacing
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.PlexArabic

/**
 * 1:1 HTML design system.
 *
 * Reference: `android_rtl.html` (15 embedded pages, 390px width).
 * - Screen: bg #F7F6F3, padding 0 16px, gap 14px
 * - H1 22sp Bold (login 28, saved 24), H2 19sp Bold, subtitle 13sp #5B6B70
 * - Header: row gap 8px, padding-top 16px; back 48x48 white radius 12
 * - Card: white, 1px #E3E7EA, radius 14, shadow 0 1px 2px, padding 14px
 * - Primary: 48-56dp, radius 12, #0E6B66/white, 15sp Bold (large 17sp)
 * - Secondary: white, 1px #C5CDD1, ink text
 * - Pills: 3x10 padding, radius 999, 13sp Bold
 * - Inputs: 52dp, 1px #C5CDD1, radius 12
 */
object AppDimens {
    val ScreenHPadding = AppSpacing.md
    val SectionGap = 14.dp
    val CardPadding = 14.dp
    val SectionPadding = 20.dp
    val CardRadius = 14.dp
    val ControlRadius = 12.dp
    val BackSize = AppSpacing.xxl
    val InputHeight = 52.dp
    val ButtonH = AppSpacing.xxl
    val ButtonLargeH = 56.dp
    val BottomNavH = 64.dp
    val BottomNavIcon = 22.dp
    val TopBarH = 56.dp
    val TopBarAvatar = 36.dp
    val LogoSize = 56.dp
    val LogoRadius = AppSpacing.md
    val AvatarSize = AppSpacing.xxl
    val NoteRowAvatar = 40.dp
    val FabRadius = 28.dp
    val FabSize = 56.dp
    val SuccessCircle = 72.dp
    val GapTight = AppSpacing.xs
    val GapForm = AppSpacing.sm
    val GapCard = 12.dp
    val AlertPad = AppSpacing.md
    val PagePad = AppSpacing.lg
    val BarSm = 6.dp
    val Hairline = 1.dp
    val IconSm = 20.dp
    val IconMd = AppSpacing.lg
    val IconLg = 30.dp
    val PillRadius = 999.dp
}

/**
 * The shared back control: a 48-dp rounded button with the auto-mirrored
 * arrow and a localized content description. [AppTopBar] uses it for titled
 * detail screens; title-less screens (the saved confirmation) use it alone.
 */
@Composable
fun AppBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(AppDimens.ControlRadius),
        color = CaregiverColors.AvatarContainer,
        modifier = modifier.size(AppDimens.BackSize),
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(AppDimens.BackSize)) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.action_back),
                // The paired token — a white icon on the light-blue
                // avatar container was invisible in light mode.
                tint = CaregiverColors.AvatarContent,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
fun AppTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.GapForm),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppSpacing.md),
    ) {
        if (onBack != null) {
            AppBackButton(onBack = onBack)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontFamily = PlexArabic,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                color = CaregiverColors.Ink,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontFamily = PlexArabic,
                    fontSize = 13.sp,
                    color = CaregiverColors.Muted,
                )
            }
        }
        actions()
    }
}

/**
 * Shared top bar for the four tab routes (X-style): avatar at the start,
 * the app name centered, a status slot at the end. The content height is
 * fixed at [AppDimens.TopBarH] (the 56-dp header rule); the status-bar
 * inset is reserved ABOVE the bar, not squeezed into it — the same
 * pattern the bottom navigation strip uses.
 *
 * The bar hides on scroll-down and returns on scroll-up: the shell translates
 * it with the offset from [TopBarHideState] and connects that state's
 * `nestedScrollConnection` to the Scaffold. Material3's stock scroll behavior
 * is deliberately not used — it collapses by a hardcoded 64dp and crashes a
 * shorter bar (see [TopBarHideState]).
 *
 * The avatar is the bar's start action (in this app it opens the drawer):
 * [avatarContentDescription] is its TalkBack name and [avatarModifier]
 * carries caller additives such as the drawer test tag.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppCenterTopBar(
    title: String,
    avatarLetter: String,
    onAvatarClick: () -> Unit,
    avatarContentDescription: String,
    modifier: Modifier = Modifier,
    avatarModifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    CenterAlignedTopAppBar(
        title = {
            Text(
                text = title,
                fontFamily = PlexArabic,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = CaregiverColors.Ink,
            )
        },
        navigationIcon = {
            IconButton(
                onClick = onAvatarClick,
                modifier = avatarModifier.semantics {
                    contentDescription = avatarContentDescription
                },
            ) {
                Avatar(avatarLetter, Modifier.size(AppDimens.TopBarAvatar))
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = CaregiverColors.Surface,
        ),
        windowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(AppDimens.TopBarH),
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        fontFamily = PlexArabic,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        color = CaregiverColors.Ink,
        modifier = modifier,
    )
}

@Composable
fun GroupLabel(text: String) {
    Text(
        text = text,
        fontFamily = PlexArabic,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = CaregiverColors.Ink,
    )
}

@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = AppDimens.ButtonH,
    large: Boolean = false,
    leading: @Composable (() -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(AppDimens.ControlRadius),
        colors = ButtonDefaults.buttonColors(
            containerColor = CaregiverColors.Primary,
            contentColor = Color.White,
            disabledContainerColor = CaregiverColors.Primary.copy(alpha = 0.5f),
        ),
        modifier = modifier
            .fillMaxWidth()
            // Grow past the minimum instead of pinning it: at large font
            // scales a wrapping label needs more than ButtonH, and a fixed
            // height clips it (Finding 11).
            .heightIn(min = height),
    ) {
        leading?.let { it(); Spacer(Modifier.width(AppDimens.GapForm)) }
        Text(
            label,
            fontFamily = PlexArabic,
            fontWeight = FontWeight.Bold,
            fontSize = if (large) 17.sp else 15.sp,
        )
    }
}

@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = AppDimens.ButtonH,
    danger: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(AppDimens.ControlRadius),
        border = BorderStroke(1.dp, CaregiverColors.Border),
        colors = ButtonDefaults.outlinedButtonColors(
            // Theme surface, not fixed white: on dark backgrounds a white
            // button read as a glowing block.
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = if (danger) CaregiverColors.Danger else CaregiverColors.Ink,
        ),
        modifier = modifier
            .fillMaxWidth()
            // Same growth rule as PrimaryButton (Finding 11).
            .heightIn(min = height),
    ) {
        Text(
            label,
            fontFamily = PlexArabic,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
        )
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    padding: Dp = AppDimens.CardPadding,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = RoundedCornerShape(AppDimens.CardRadius),
        border = BorderStroke(1.dp, CaregiverColors.BorderSoft),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(AppDimens.CardRadius), clip = false),
    ) {
        Column(
            modifier = Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(AppDimens.GapForm),
            content = content,
        )
    }
}

@Composable
fun ForwardChevron(
    tint: Color = CaregiverColors.Muted,
    size: Dp = 18.dp,
) {
    // Forward means left in RTL, right in LTR — a fixed chevron glyph
    // points the wrong way in one of the two locales.
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Icon(
        if (rtl) Icons.Filled.ChevronLeft else Icons.Filled.ChevronRight,
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(size),
    )
}

@Composable
fun Avatar(letter: String, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(AppDimens.AvatarSize)
            .background(CaregiverColors.AvatarContainer, CircleShape),
    ) {
        Text(
            text = letter.take(1),
            fontFamily = PlexArabic,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = CaregiverColors.AvatarContent,
        )
    }
}

@Composable
fun Pill(
    text: String,
    container: Color,
    content: Color,
) {
    Surface(
        shape = RoundedCornerShape(AppDimens.PillRadius),
        color = container,
    ) {
        Text(
            text = text,
            fontFamily = PlexArabic,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = content,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

@Composable
fun GrayPill(text: String) = Pill(text, CaregiverColors.PillGrayContainer, CaregiverColors.PillGrayContent)

@Composable
fun GreenPill(text: String) = Pill(text, CaregiverColors.SuccessContainer, CaregiverColors.Success)

@Composable
fun RedPill(text: String) = Pill(text, CaregiverColors.DangerContainer, CaregiverColors.Danger)

@Composable
fun YellowPill(text: String) = Pill(text, CaregiverColors.WarningContainer, CaregiverColors.Warning)

@Composable
fun LightBluePill(text: String) =
    Pill(text, CaregiverColors.PillLightBlueContainer, CaregiverColors.PillLightBlueContent)

@Composable
fun SafetyAlertCard(title: String, body: String) {
    Card(
        shape = RoundedCornerShape(AppDimens.CardRadius),
        border = BorderStroke(1.dp, CaregiverColors.DangerBorder),
        colors = CardDefaults.cardColors(containerColor = CaregiverColors.DangerContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppDimens.GapCard),
            modifier = Modifier.padding(AppDimens.AlertPad),
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = CaregiverColors.Danger,
                modifier = Modifier.size(AppDimens.IconMd),
            )
            Column(verticalArrangement = Arrangement.spacedBy(AppDimens.GapTight)) {
                Text(
                    title,
                    fontFamily = PlexArabic,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = CaregiverColors.Danger,
                )
                Text(
                    body,
                    fontFamily = PlexArabic,
                    fontSize = 15.sp,
                    color = CaregiverColors.Danger,
                )
            }
        }
    }
}

@Composable
fun InfoAlertCard(title: String, body: String? = null) {
    Card(
        shape = RoundedCornerShape(AppDimens.CardRadius),
        border = BorderStroke(1.dp, CaregiverColors.InfoBorder),
        colors = CardDefaults.cardColors(containerColor = CaregiverColors.InfoContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppDimens.GapCard),
            modifier = Modifier.padding(AppDimens.AlertPad),
        ) {
            Icon(
                Icons.Filled.Info,
                contentDescription = null,
                tint = CaregiverColors.Info,
                modifier = Modifier.size(AppDimens.IconMd),
            )
            Column {
                Text(
                    title,
                    fontFamily = PlexArabic,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = CaregiverColors.Info,
                )
                if (body != null) {
                    Text(
                        body,
                        fontFamily = PlexArabic,
                        fontSize = 15.sp,
                        color = CaregiverColors.Info,
                    )
                }
            }
        }
    }
}

@Composable
fun LockBar(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(AppDimens.GapForm),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(CaregiverColors.LockBar, RoundedCornerShape(AppDimens.ControlRadius))
            .padding(AppDimens.CardPadding),
    ) {
        Icon(
            Icons.Filled.Lock,
            contentDescription = null,
            tint = CaregiverColors.Ink,
            modifier = Modifier.size(AppDimens.IconSm),
        )
        Text(text, fontFamily = PlexArabic, fontSize = 14.sp, color = CaregiverColors.Ink)
    }
}

@Composable
fun UnclearBox(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, CaregiverColors.Border, RoundedCornerShape(AppDimens.ControlRadius))
            .padding(AppDimens.CardPadding),
    ) {
        Text(text, fontFamily = PlexArabic, fontSize = 14.sp, color = CaregiverColors.Ink)
    }
}

@Composable
fun AppEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(AppDimens.CardRadius),
        border = BorderStroke(1.dp, CaregiverColors.BorderSoft),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppDimens.GapForm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppDimens.PagePad),
        ) {
            Icon(icon, contentDescription = null, tint = CaregiverColors.Primary,
                modifier = Modifier.size(AppDimens.IconLg))
            Text(
                title,
                fontFamily = PlexArabic,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                color = CaregiverColors.Ink,
            )
            Text(
                subtitle,
                fontFamily = PlexArabic,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = CaregiverColors.Muted,
            )
            SecondaryButton(label = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun AppErrorState(message: String, detail: String? = null, onRetry: () -> Unit) {
    Card(
        shape = RoundedCornerShape(AppDimens.CardRadius),
        border = BorderStroke(1.dp, CaregiverColors.DangerBorder),
        colors = CardDefaults.cardColors(containerColor = CaregiverColors.DangerContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(AppDimens.GapTight),
            modifier = Modifier.padding(AppDimens.AlertPad),
        ) {
            Text(
                message,
                fontFamily = PlexArabic,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = CaregiverColors.Danger,
            )
            if (detail != null) {
                Text(detail, fontFamily = PlexArabic, fontSize = 14.sp, color = CaregiverColors.Danger)
            }
            Spacer(Modifier.height(AppSpacing.sm))
            SecondaryButton(label = stringResource(R.string.common_retry), onClick = onRetry)
        }
    }
}

@Composable
fun AppWarningState(message: String, detail: String? = null) {
    Card(
        shape = RoundedCornerShape(AppDimens.CardRadius),
        border = BorderStroke(1.dp, CaregiverColors.WarningContainer),
        colors = CardDefaults.cardColors(containerColor = CaregiverColors.WarningContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(AppDimens.GapTight),
            modifier = Modifier.padding(AppDimens.AlertPad),
        ) {
            Text(
                message,
                fontFamily = PlexArabic,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = CaregiverColors.Warning,
            )
            if (detail != null) {
                Text(detail, fontFamily = PlexArabic, fontSize = 14.sp, color = CaregiverColors.Warning)
            }
        }
    }
}

@Composable
fun SkeletonBar(widthFraction: Float, height: Dp = 14.dp) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .background(CaregiverColors.Skeleton, RoundedCornerShape(8.dp)),
    )
}

@Composable
fun AppLoadingSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.GapForm), modifier = Modifier.fillMaxWidth()) {
        SkeletonBar(0.6f, 20.dp)
        SkeletonBar(0.9f)
        SkeletonBar(0.75f)
    }
}

@Composable
fun BusyBar(label: String, progress: Float = 0.6f) {
    Column(verticalArrangement = Arrangement.spacedBy(AppDimens.GapForm), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppDimens.GapForm)) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(label, fontFamily = PlexArabic, fontSize = 15.sp, color = CaregiverColors.Ink)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AppDimens.BarSm)
                .background(CaregiverColors.Skeleton, RoundedCornerShape(3.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(AppDimens.BarSm)
                    .background(CaregiverColors.Primary, RoundedCornerShape(3.dp)),
            )
        }
    }
}

@Composable
fun BottomActionBar(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, CaregiverColors.BorderSoft),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(AppDimens.GapForm),
            modifier = Modifier.padding(horizontal = AppDimens.AlertPad, vertical = AppDimens.GapCard),
            content = content,
        )
    }
}

@Composable
fun FieldError(text: String) {
    Text(
        text,
        fontFamily = PlexArabic,
        fontSize = 14.sp,
        color = CaregiverColors.Danger,
    )
}

@Composable
fun HelperCaption(text: String, align: TextAlign = TextAlign.Start) {
    Text(
        text,
        fontFamily = PlexArabic,
        fontSize = 12.sp,
        color = CaregiverColors.Muted,
        textAlign = align,
        modifier = if (align == TextAlign.Center) Modifier.fillMaxWidth() else Modifier,
    )
}

@Composable
fun TrendRow(label: String, value: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppDimens.GapForm),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppDimens.GapForm),
    ) {
        Icon(icon, contentDescription = null, tint = CaregiverColors.Ink,
            modifier = Modifier.size(AppDimens.IconSm))
        Text(
            label,
            fontFamily = PlexArabic,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.width(80.dp),
            color = CaregiverColors.Ink,
        )
        Text(value, fontFamily = PlexArabic, fontSize = 15.sp, color = CaregiverColors.Ink)
    }
}
