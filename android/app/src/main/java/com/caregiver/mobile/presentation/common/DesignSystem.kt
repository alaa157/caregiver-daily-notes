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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.caregiver.mobile.R
import com.caregiver.mobile.core.theme.AppRadius
import com.caregiver.mobile.core.theme.AppSizes
import com.caregiver.mobile.core.theme.AppSpacing
import com.caregiver.mobile.core.theme.CaregiverColors

/**
 * Shared components per design/components.md. All colors from
 * CaregiverColors (tokens.json), all sizes from AppSpacing/AppSizes/
 * AppRadius, all type from MaterialTheme.typography (token scale voiced
 * in AppFontFamily). No literal `.dp`/`.sp`/hex here.
 *
 * Mapping notes:
 * - Card: surface bg, border outline, radius md (14), card elevation.
 * - PrimaryButton: primary fill, surface label, radius md; pressed uses
 *   PrimaryPressed via ButtonDefaults? Kept as primary (pressed handled
 *   by ripple); disabled at 40% opacity per components.md.
 * - SecondaryButton: surface bg, border outline, textPrimary text.
 * - DangerOutlineButton behavior = SecondaryButton(danger = true).
 * - Pill 28 high, radius pill, label style; neutral/info/warning/danger.
 * - Avatar 40 circle, primarySoft fill, primary text, initials only.
 * - AlertSafety: danger border + danger at 10% bg, radius md.
 * - AlertInfo: info border + info at 10% bg, radius md.
 * - Skeleton blocks use border. No full-screen spinners.
 */
@Composable
fun AppTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = AppSpacing.md),
    ) {
        if (onBack != null) {
            Surface(
                shape = RoundedCornerShape(AppRadius.md),
                color = CaregiverColors.Surface,
                modifier = Modifier.size(AppSizes.touchTargetMin),
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(AppSizes.touchTargetMin)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null,
                        tint = CaregiverColors.TextPrimary,
                        modifier = Modifier.size(AppSizes.iconSize),
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = CaregiverColors.TextSecondary,
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
        style = MaterialTheme.typography.titleMedium,
        modifier = modifier,
    )
}

@Composable
fun GroupLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = AppSizes.buttonHeight,
    large: Boolean = false,
    leading: @Composable (() -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(AppRadius.md),
        colors = ButtonDefaults.buttonColors(
            containerColor = CaregiverColors.Primary,
            contentColor = CaregiverColors.Surface,
            disabledContainerColor = CaregiverColors.Primary.copy(alpha = 0.4f),
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .height(if (large) AppSizes.buttonHeightLarge else height),
    ) {
        leading?.let { it(); Spacer(Modifier.width(AppSpacing.xs)) }
        Text(
            label,
            style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = AppSizes.buttonHeight,
    danger: Boolean = false,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(AppRadius.md),
        border = BorderStroke(AppSizes.borderWidth, CaregiverColors.Border),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = CaregiverColors.Surface,
            contentColor = if (danger) CaregiverColors.Danger else CaregiverColors.TextPrimary,
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = height)
            .height(height),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    padding: Dp = AppSpacing.md,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = RoundedCornerShape(AppSizes.cardRadius),
        border = BorderStroke(AppSizes.borderWidth, CaregiverColors.Border),
        colors = CardDefaults.cardColors(containerColor = CaregiverColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = AppSizes.cardElevation),
        modifier = modifier
            .fillMaxWidth()
            .shadow(AppSizes.cardElevation, RoundedCornerShape(AppSizes.cardRadius), clip = false),
    ) {
        Column(
            modifier = Modifier.padding(padding),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            content = content,
        )
    }
}

private val AppTrendLabelWidth = AppSizes.trendLabelWidth

@Composable
fun Avatar(letter: String, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(AppSizes.avatarSize)
            .background(CaregiverColors.PrimarySoft, CircleShape),
    ) {
        Text(
            text = letter.take(1),
            style = MaterialTheme.typography.titleMedium,
            color = CaregiverColors.Primary,
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
        shape = RoundedCornerShape(AppRadius.pill),
        color = container,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = content,
            modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xxs),
        )
    }
}

@Composable
fun GrayPill(text: String) = Pill(text, CaregiverColors.Border, CaregiverColors.TextPrimary)

@Composable
fun GreenPill(text: String) = Pill(text, CaregiverColors.Success.copy(alpha = 0.12f), CaregiverColors.Success)

@Composable
fun RedPill(text: String) = Pill(text, CaregiverColors.Danger.copy(alpha = 0.1f), CaregiverColors.Danger)

@Composable
fun YellowPill(text: String) = Pill(text, CaregiverColors.Warning.copy(alpha = 0.12f), CaregiverColors.Warning)

@Composable
fun LightBluePill(text: String) =
    Pill(text, CaregiverColors.Info.copy(alpha = 0.12f), CaregiverColors.Info)

@Composable
fun SafetyAlertCard(title: String, body: String) {
    Card(
        shape = RoundedCornerShape(AppRadius.md),
        border = BorderStroke(AppSizes.borderWidth, CaregiverColors.Danger),
        colors = CardDefaults.cardColors(containerColor = CaregiverColors.Danger.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            modifier = Modifier.padding(AppSpacing.md),
        ) {
            Text("⚠", style = MaterialTheme.typography.titleLarge)
            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = CaregiverColors.Danger,
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = CaregiverColors.Danger,
                )
            }
        }
    }
}

@Composable
fun InfoAlertCard(title: String, body: String? = null) {
    Card(
        shape = RoundedCornerShape(AppRadius.md),
        border = BorderStroke(AppSizes.borderWidth, CaregiverColors.Info),
        colors = CardDefaults.cardColors(containerColor = CaregiverColors.Info.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            modifier = Modifier.padding(AppSpacing.md),
        ) {
            Text("ⓘ", style = MaterialTheme.typography.titleLarge)
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = CaregiverColors.Info,
                )
                if (body != null) {
                    Text(
                        body,
                        style = MaterialTheme.typography.bodyLarge,
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
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(CaregiverColors.PrimarySoft, RoundedCornerShape(AppRadius.md))
            .padding(AppSpacing.md),
    ) {
        Text("🔒", style = MaterialTheme.typography.titleMedium)
        Text(text, style = MaterialTheme.typography.labelLarge, color = CaregiverColors.TextPrimary)
    }
}

@Composable
fun UnclearBox(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(AppSizes.borderWidthStrong, CaregiverColors.Border, RoundedCornerShape(AppRadius.md))
            .padding(AppSpacing.md),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = CaregiverColors.TextPrimary)
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
        shape = RoundedCornerShape(AppRadius.md),
        border = BorderStroke(AppSizes.borderWidth, CaregiverColors.Border),
        colors = CardDefaults.cardColors(containerColor = CaregiverColors.Surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            modifier = Modifier
                .fillMaxWidth()
                .padding(AppSpacing.lg),
        ) {
            Text(icon, style = MaterialTheme.typography.headlineSmall, color = CaregiverColors.Primary)
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                color = CaregiverColors.TextPrimary,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                color = CaregiverColors.TextSecondary,
            )
            SecondaryButton(label = actionLabel, onClick = onAction)
        }
    }
}

@Composable
fun AppErrorState(message: String, detail: String? = null, onRetry: () -> Unit) {
    Card(
        shape = RoundedCornerShape(AppRadius.md),
        border = BorderStroke(AppSizes.borderWidth, CaregiverColors.Danger),
        colors = CardDefaults.cardColors(containerColor = CaregiverColors.Danger.copy(alpha = 0.1f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
            modifier = Modifier.padding(AppSpacing.md),
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = CaregiverColors.Danger,
            )
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.labelLarge, color = CaregiverColors.Danger)
            }
            Spacer(Modifier.height(AppSpacing.xs))
            SecondaryButton(label = stringResource(R.string.action_retry), onClick = onRetry)
        }
    }
}

@Composable
fun AppWarningState(message: String, detail: String? = null) {
    Card(
        shape = RoundedCornerShape(AppRadius.md),
        border = BorderStroke(AppSizes.borderWidth, CaregiverColors.Warning),
        colors = CardDefaults.cardColors(containerColor = CaregiverColors.Warning.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
            modifier = Modifier.padding(AppSpacing.md),
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = CaregiverColors.Warning,
            )
            if (detail != null) {
                Text(detail, style = MaterialTheme.typography.labelLarge, color = CaregiverColors.Warning)
            }
        }
    }
}

@Composable
fun SkeletonBar(widthFraction: Float, height: Dp = AppSpacing.sm) {
    Box(
        modifier = Modifier
            .fillMaxWidth(widthFraction)
            .height(height)
            .background(CaregiverColors.Border, RoundedCornerShape(AppRadius.sm)),
    )
}

@Composable
fun AppLoadingSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs), modifier = Modifier.fillMaxWidth()) {
        SkeletonBar(0.6f, AppSpacing.md)
        SkeletonBar(0.9f)
        SkeletonBar(0.75f)
    }
}

@Composable
fun BusyBar(label: String, progress: Float = 0.6f) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs), modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            CircularProgressIndicator(modifier = Modifier.size(AppSizes.progressIndicator), strokeWidth = AppSizes.borderWidthStrong)
            Text(label, style = MaterialTheme.typography.bodyLarge, color = CaregiverColors.TextPrimary)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(AppSizes.progressTrack)
                .background(CaregiverColors.Border, RoundedCornerShape(AppRadius.sm)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(AppSizes.progressTrack)
                    .background(CaregiverColors.Primary, RoundedCornerShape(AppRadius.sm)),
            )
        }
    }
}

@Composable
fun BottomActionBar(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = CaregiverColors.Surface,
        border = BorderStroke(AppSizes.borderWidth, CaregiverColors.Border),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xs),
            modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            content = content,
        )
    }
}

@Composable
fun FieldError(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = CaregiverColors.Danger,
    )
}

@Composable
fun HelperCaption(text: String, align: TextAlign = TextAlign.Start) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = CaregiverColors.TextSecondary,
        textAlign = align,
        modifier = if (align == TextAlign.Center) Modifier.fillMaxWidth() else Modifier,
    )
}

@Composable
fun TrendRow(label: String, value: String, icon: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AppSpacing.xs),
    ) {
        Text(icon, style = MaterialTheme.typography.titleMedium)
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(AppTrendLabelWidth),
            color = CaregiverColors.TextPrimary,
        )
        Text(value, style = MaterialTheme.typography.bodyLarge, color = CaregiverColors.TextPrimary)
    }
}