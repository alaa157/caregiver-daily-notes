package com.caregiver.mobile.presentation.common

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.i18n.Bidi
import com.caregiver.mobile.core.navigation.AppDestinations
import com.caregiver.mobile.core.navigation.MainTab
import com.caregiver.mobile.core.theme.CaregiverColors
import com.caregiver.mobile.core.theme.PlexArabic
import com.caregiver.mobile.data.SettingsStore
import kotlinx.coroutines.launch

/**
 * X-style side drawer (Task 7), the avatar drawer's sheet content: session
 * profile header, People / Plans / Settings, the language switch, and a sync
 * status line.
 *
 * The drawer does not own navigation, sync or identity: the caller passes the
 * session display name/initial, the sync status text (Task 8d wires it to the
 * real state — never hardcode it here) and receives navigation requests.
 * The language switch reuses exactly the Settings behavior (persist through
 * [SettingsStore.setLanguage], then recreate the activity to apply the
 * direction); a null activity is tolerated because the drawer also composes
 * under headless tests.
 */
@Composable
fun AppDrawer(
    graph: AppGraph,
    profileName: String?,
    avatarLetter: String,
    syncStatus: com.caregiver.mobile.presentation.common.SyncStatus,
    onRetry: (() -> Unit)? = null,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val language by graph.settings.language.collectAsState(initial = SettingsStore.DEFAULT_LANGUAGE)
    // Null-safe (unlike Settings, which is only ever hosted by an activity):
    // the drawer content also composes in headless compose tests.
    val activity = LocalContext.current as? Activity

    ModalDrawerSheet(
        modifier = modifier,
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        // Profile header: the session identity. Same email→name derivation
        // the top-bar avatar uses, so the bar, drawer and greeting agree.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppDimens.GapForm),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppDimens.ScreenHPadding, vertical = AppDimens.CardPadding),
        ) {
            Avatar(avatarLetter, Modifier.size(AppDimens.AvatarSize))
            if (!profileName.isNullOrBlank()) {
                Text(
                    text = Bidi.isolate(profileName),
                    fontFamily = PlexArabic,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        HorizontalDivider(thickness = AppDimens.Hairline, color = CaregiverColors.BorderSoft)
        DrawerItem(
            icon = Icons.Outlined.People,
            label = stringResource(R.string.tab_people),
            tag = "drawer_item_people",
        ) {
            onNavigate(MainTab.People.route)
        }
        DrawerItem(
            icon = Icons.AutoMirrored.Outlined.Assignment,
            label = stringResource(R.string.plans_title),
            tag = "drawer_item_plans",
        ) {
            onNavigate(AppDestinations.Plans.base)
        }
        DrawerItem(
            icon = Icons.Outlined.Settings,
            label = stringResource(R.string.settings_title),
            tag = "drawer_item_settings",
        ) {
            onNavigate(AppDestinations.Settings.base)
        }
        HorizontalDivider(thickness = AppDimens.Hairline, color = CaregiverColors.BorderSoft)
        Spacer(Modifier.height(AppDimens.GapCard))
        Text(
            text = stringResource(R.string.settings_language),
            fontFamily = PlexArabic,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = AppDimens.ScreenHPadding),
        )
        Spacer(Modifier.height(AppDimens.GapForm))
        Row(
            horizontalArrangement = Arrangement.spacedBy(AppDimens.GapForm),
            modifier = Modifier.padding(horizontal = AppDimens.ScreenHPadding),
        ) {
            // Language names stay literal exactly like the Settings chips:
            // each language must be recognizable under any active locale.
            LanguageChip(label = "العربية", selected = language == "ar") {
                if (language != "ar") {
                    scope.launch {
                        graph.settings.setLanguage("ar")
                        activity?.recreate()
                    }
                }
            }
            LanguageChip(label = "English", selected = language == "en") {
                if (language != "en") {
                    scope.launch {
                        graph.settings.setLanguage("en")
                        activity?.recreate()
                    }
                }
            }
        }
        Spacer(Modifier.height(AppDimens.GapCard))
        HorizontalDivider(thickness = AppDimens.Hairline, color = CaregiverColors.BorderSoft)
        Text(
            text = stringResource(
                when (syncStatus) {
                    SyncStatus.SYNCED -> R.string.sync_status_synced
                    SyncStatus.SYNCING -> R.string.sync_status_syncing
                    SyncStatus.OFFLINE -> R.string.sync_status_offline
                    SyncStatus.FAILED -> R.string.sync_status_failed
                },
            ),
            fontFamily = PlexArabic,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(
                horizontal = AppDimens.ScreenHPadding,
                vertical = AppDimens.GapForm,
            ),
        )
        if (syncStatus == SyncStatus.FAILED && onRetry != null) {
            androidx.compose.material3.TextButton(onClick = onRetry) {
                Text(stringResource(R.string.common_retry))
            }
        }
    }
}

/** One drawer destination row; the tag keeps UI tests offline-safe. */
@Composable
private fun DrawerItem(
    icon: ImageVector,
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    NavigationDrawerItem(
        label = {
            Text(
                text = label,
                fontFamily = PlexArabic,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )
        },
        selected = false,
        onClick = onClick,
        icon = {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(AppDimens.IconMd),
            )
        },
        // None of the items is persistently "current", but the selected
        // styling keeps the app palette if selection is ever added.
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = CaregiverColors.PrimarySoft,
            selectedIconColor = MaterialTheme.colorScheme.primary,
            selectedTextColor = MaterialTheme.colorScheme.primary,
            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            unselectedTextColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.testTag(tag),
    )
}

/** Language chip with the exact Settings styling. */
@Composable
private fun LanguageChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        shape = RoundedCornerShape(AppDimens.CardRadius),
        border = BorderStroke(
            AppDimens.Hairline,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = CaregiverColors.PrimarySoft,
            selectedLabelColor = MaterialTheme.colorScheme.primary,
        ),
    )
}
