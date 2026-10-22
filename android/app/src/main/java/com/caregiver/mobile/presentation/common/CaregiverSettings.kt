package com.caregiver.mobile.presentation.common

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

// NOTE: never declare a class or data class named `CaregiverSettingsItem`
// here. When a class and a function share a name, Kotlin resolves every
// `CaregiverSettingsItem(...)` call to the class constructor: the object is
// built, discarded, and the row renders nothing. That silently emptied the
// Settings list (AI, Theme & Colors, all four Data & Backup actions, About)
// and the AI settings screen in the v0.3.0 demo. The composable below is the
// only declaration with this name in the app.

/**
 * One row of a settings group.
 *
 * The rows are data rather than composable calls because the group has to
 * know how many of them there are: the first and the last row of a group are
 * rounded at the group's ends, the rows in between are squared off against
 * their neighbours, exactly as Metrolist draws them.
 */
data class CaregiverSettingsRow(
    val title: String,
    val icon: ImageVector? = null,
    /** Drawn instead of [icon] — a profile photo, a badge. */
    val leadingContent: (@Composable () -> Unit)? = null,
    val description: String? = null,
    val trailingContent: (@Composable () -> Unit)? = null,
    val showBadge: Boolean = false,
    val isHighlighted: Boolean = false,
    val enabled: Boolean = true,
    val onClick: (() -> Unit)? = null,
)

/**
 * Metrolist-style settings group: one card per row, 4.dp apart, the group
 * rounded at both ends and the rows between them squared off.
 *
 * Shape rules (computed by item index; pass the full list for correct
 * segmentation):
 * - 1 item only → RoundedCornerShape(24.dp)
 * - first item → topStart/topEnd 24.dp, bottomStart/bottomEnd 6.dp
 * - middle items → RoundedCornerShape(6.dp)
 * - last item → topStart/topEnd 6.dp, bottomStart/bottomEnd 24.dp
 */
@Composable
fun CaregiverSettingsGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    items: List<CaregiverSettingsRow>,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items.forEachIndexed { index, row ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                    shape = groupRowShape(index, items.size),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                ) {
                    CaregiverSettingsItem(
                        title = row.title,
                        icon = row.icon,
                        leadingContent = row.leadingContent,
                        description = row.description,
                        trailingContent = row.trailingContent,
                        showBadge = row.showBadge,
                        isHighlighted = row.isHighlighted,
                        enabled = row.enabled,
                        onClick = row.onClick,
                    )
                }
            }
        }
    }
}

/**
 * A group whose content is not a list of rows — a row of chips, a custom
 * control. It is drawn as a single card, which is what Metrolist's groups
 * look like when they hold one thing.
 */
@Composable
fun CaregiverSettingsGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )
        }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                content = content,
            )
        }
    }
}

/**
 * The shape of one row's card: the group's ends are rounded, the rows in
 * between are squared off against their neighbours.
 */
private fun groupRowShape(index: Int, count: Int): RoundedCornerShape = when {
    count <= 1 -> RoundedCornerShape(24.dp)
    index == 0 -> RoundedCornerShape(
        topStart = 24.dp,
        topEnd = 24.dp,
        bottomStart = 6.dp,
        bottomEnd = 6.dp,
    )
    index == count - 1 -> RoundedCornerShape(
        topStart = 6.dp,
        topEnd = 6.dp,
        bottomStart = 24.dp,
        bottomEnd = 24.dp,
    )
    else -> RoundedCornerShape(6.dp)
}

/**
 * Single settings row rendered inside a [CaregiverSettingsGroup].
 * Disabled items do not fire [onClick] and use reduced-opacity tints.
 */
@Composable
fun CaregiverSettingsItem(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    leadingContent: (@Composable () -> Unit)? = null,
    description: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    showBadge: Boolean = false,
    isHighlighted: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag(title)
            .clickable(enabled = enabled && onClick != null) { onClick?.invoke() }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leadingContent != null) {
            leadingContent.invoke()
            Spacer(Modifier.width(16.dp))
        } else if (icon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        MaterialTheme.colorScheme.primary.copy(
                            alpha = if (isHighlighted) 0.15f else 0.1f,
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (showBadge) {
                    BadgedBox(
                        badge = {
                            Badge(containerColor = MaterialTheme.colorScheme.error)
                        },
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            else MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                            modifier = Modifier.size(24.dp),
                        )
                    }
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                else MaterialTheme.colorScheme.onSurface,
            )
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailingContent?.let { trailing ->
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}
