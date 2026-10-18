package com.caregiver.mobile.ui.component

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.caregiver.mobile.core.theme.CaregiverColors

data class CaregiverSettingsItem(
    val icon: ImageVector? = null,
    val leadingContent: (@Composable () -> Unit)? = null,
    val title: @Composable () -> Unit,
    val description: (@Composable () -> Unit)? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val showBadge: Boolean = false,
    val isHighlighted: Boolean = false,
    val enabled: Boolean = true,
    val onClick: (() -> Unit)? = null,
)

@Composable
fun CaregiverSettingsGroup(
    title: String? = null,
    items: List<CaregiverSettingsItem>,
    modifier: Modifier = Modifier,
    useLowContrast: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = CaregiverColors.Primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEachIndexed { index, item ->
                val shape = when {
                    items.size == 1 -> RoundedCornerShape(24.dp)
                    index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 6.dp, bottomEnd = 6.dp)
                    index == items.size - 1 -> RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                    else -> RoundedCornerShape(6.dp)
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    shape = shape,
                    colors = CardDefaults.cardColors(
                        containerColor = if (!useLowContrast) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        }
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    CaregiverSettingsItemRow(item = item)
                }
            }
        }
    }
}

@Composable
private fun CaregiverSettingsItemRow(item: CaregiverSettingsItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.enabled && item.onClick != null) { item.onClick?.invoke() }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (item.leadingContent != null) {
            item.leadingContent.invoke()
            Spacer(Modifier.width(16.dp))
        } else if (item.icon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        CaregiverColors.Primary.copy(alpha = if (item.isHighlighted) 0.15f else 0.1f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (item.showBadge) {
                    BadgedBox(badge = { Badge(containerColor = CaregiverColors.Danger) }) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = null,
                            tint = if (!item.enabled) CaregiverColors.Ink.copy(alpha = 0.38f)
                            else CaregiverColors.Primary.copy(alpha = 0.9f),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                } else {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        tint = if (!item.enabled) CaregiverColors.Ink.copy(alpha = 0.38f)
                        else CaregiverColors.Primary.copy(alpha = 0.9f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            ProvideTextStyle(MaterialTheme.typography.titleMedium.copy(color = if (!item.enabled) CaregiverColors.Ink.copy(alpha = 0.38f) else CaregiverColors.Ink)) {
                item.title()
            }
            item.description?.let { desc ->
                Spacer(Modifier.height(2.dp))
                ProvideTextStyle(MaterialTheme.typography.bodyMedium.copy(color = if (!item.enabled) CaregiverColors.Ink.copy(alpha = 0.38f) else CaregiverColors.Muted)) {
                    desc()
                }
            }
        }
        item.trailingContent?.let { trailing ->
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}
