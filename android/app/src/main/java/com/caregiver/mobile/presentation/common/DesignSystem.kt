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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caregiver.mobile.R
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
    val ScreenHPadding = 16.dp
    val SectionGap = 14.dp
    val CardPadding = 14.dp
    val SectionPadding = 20.dp
    val CardRadius = 14.dp
    val ControlRadius = 12.dp
    val BackSize = 48.dp
    val InputHeight = 52.dp
    val ButtonH = 48.dp
    val ButtonLargeH = 56.dp
    val BottomNavH = 72.dp
    val LogoSize = 56.dp
    val LogoRadius = 16.dp
    val AvatarSize = 48.dp
    val SuccessCircle = 72.dp
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
        if (onBack != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                modifier = Modifier.size(48.dp),
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = CaregiverColors.Ink,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
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
    height: Dp = 48.dp,
    large: Boolean = false,
    leading: @Composable (() -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = CaregiverColors.Primary,
            contentColor = Color.White,
            disabledContainerColor = CaregiverColors.Primary.copy(alpha = 0.5f),
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .height(height),
    ) {
        leading?.let { it(); Spacer(Modifier.width(8.dp)) }
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
    height: Dp = 48.dp,
    danger: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CaregiverColors.Border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = Color.White,
            contentColor = if (danger) CaregiverColors.Danger else CaregiverColors.Ink,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .height(height),
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
    padding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CaregiverColors.BorderSoft),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, RoundedCornerShape(14.dp), clip = false),
    ) {
        Column(
            modifier = Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
fun Avatar(letter: String, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(48.dp)
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
        shape = RoundedCornerShape(999.dp),
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
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CaregiverColors.DangerBorder),
        colors = CardDefaults.cardColors(containerColor = CaregiverColors.DangerContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Text("⚠", fontSize = 24.sp)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CaregiverColors.InfoBorder),
        colors = CardDefaults.cardColors(containerColor = CaregiverColors.InfoContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Text("ⓘ", fontSize = 24.sp)
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
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(CaregiverColors.LockBar, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Text("🔒", fontSize = 20.sp)
        Text(text, fontFamily = PlexArabic, fontSize = 14.sp, color = CaregiverColors.Ink)
    }
}

@Composable
fun UnclearBox(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, CaregiverColors.Border, RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Text(text, fontFamily = PlexArabic, fontSize = 14.sp, color = CaregiverColors.Ink)
    }
}

@Composable
fun AppEmptyState(
    icon: String,
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CaregiverColors.BorderSoft),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
        ) {
            Text(icon, fontSize = 30.sp, color = CaregiverColors.Primary)
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
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CaregiverColors.DangerBorder),
        colors = CardDefaults.cardColors(containerColor = CaregiverColors.DangerContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(16.dp),
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
            Spacer(Modifier.height(8.dp))
            SecondaryButton(label = stringResource(R.string.common_retry), onClick = onRetry)
        }
    }
}

@Composable
fun AppWarningState(message: String, detail: String? = null) {
    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, CaregiverColors.WarningContainer),
        colors = CardDefaults.cardColors(containerColor = CaregiverColors.WarningContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(16.dp),
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        SkeletonBar(0.6f, 20.dp)
        SkeletonBar(0.9f)
        SkeletonBar(0.75f)
    }
}

@Composable
fun BusyBar(label: String, progress: Float = 0.6f) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(label, fontFamily = PlexArabic, fontSize = 15.sp, color = CaregiverColors.Ink)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(CaregiverColors.Skeleton, RoundedCornerShape(3.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(6.dp)
                    .background(CaregiverColors.Primary, RoundedCornerShape(3.dp)),
            )
        }
    }
}

@Composable
fun BottomActionBar(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Color.White,
        border = BorderStroke(1.dp, CaregiverColors.BorderSoft),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
fun TrendRow(label: String, value: String, icon: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        Text(icon, fontSize = 20.sp)
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
