package com.caregiver.mobile.presentation.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AlternateEmail
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.caregiver.mobile.BuildConfig
import com.caregiver.mobile.R
import com.caregiver.mobile.data.update.UpdateChecker
import com.caregiver.mobile.presentation.common.AppTopBar
import com.caregiver.mobile.presentation.common.CaregiverSettingsGroup
import com.caregiver.mobile.presentation.common.CaregiverSettingsRow
import com.caregiver.mobile.presentation.common.assistedViewModel

/**
 * Project links. The developer card intentionally uses placeholder icons and
 * placeholder social buttons; only the GitHub destination is real for now.
 * Replace the placeholder slots when the remaining brand assets/links exist.
 */
private const val GITHUB_PROFILE_URL = "https://github.com/alaa157"
private const val REPOSITORY_URL = "https://github.com/alaa157/caregiver-daily-notes"
private const val ISSUES_URL = "$REPOSITORY_URL/issues"
private const val DOCUMENTATION_URL = "$REPOSITORY_URL#readme"

/**
 * One collaborator of the project.
 *
 * The lead developer has the hero card above; these are the people who built
 * the rest of it with them. Each row opens that person's GitHub profile.
 */
private data class Collaborator(
    val nameRes: Int,
    /** The part of the project this person owns. */
    val roleRes: Int,
    val githubHandle: String,
) {
    val githubUrl: String get() = "https://github.com/$githubHandle"
}

/**
 * The team behind the app, one row each, in the order the project was
 * built: the notes API, the accounts and plans, and the web client.
 */
private val collaborators = listOf(
    Collaborator(
        nameRes = R.string.about_collaborator_ebrahim_name,
        roleRes = R.string.about_role_daily_notes,
        githubHandle = "ebrahimahmedss47-commits",
    ),
    Collaborator(
        nameRes = R.string.about_collaborator_mohamed_name,
        roleRes = R.string.about_role_auth_people_plans,
        githubHandle = "mohamed-hemdan1",
    ),
    Collaborator(
        nameRes = R.string.about_collaborator_mahmoud_name,
        roleRes = R.string.about_role_web_app,
        githubHandle = "hewidyy",
    ),
)

/**
 * About page, laid out like the Metrolist about screen: app header card,
 * update check, lead-developer hero card with socials, a grouped
 * community/info list and a footer. The top bar stays fixed while the cards
 * scroll.
 */
@Composable
fun AboutScreen(
    updateChecker: UpdateChecker,
    onBack: (() -> Unit)? = null,
) {
    val uriHandler = LocalUriHandler.current
    val updateVm: AboutViewModel = assistedViewModel("about-updates") {
        AboutViewModel(updateChecker)
    }
    val updateState by updateVm.updateState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppTopBar(
            title = stringResource(R.string.settings_about),
            onBack = onBack,
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))

            AppHeaderCard()

            Spacer(Modifier.height(16.dp))

            UpdateSection(
                state = updateState,
                currentVersion = BuildConfig.VERSION_NAME,
                onCheck = updateVm::check,
                uriHandler = uriHandler,
            )

            Spacer(Modifier.height(24.dp))

            LeadDeveloperCard(uriHandler)

            Spacer(Modifier.height(32.dp))

            CollaboratorsSection(uriHandler)

            Spacer(Modifier.height(32.dp))

            CommunityAndInfo(uriHandler)

            Spacer(Modifier.height(48.dp))

            Text(
                text = stringResource(R.string.about_footer),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

/**
 * Update status lives beside the installed version. Checking happens once
 * when the page opens; tapping the row retries after a failure or checks
 * again later. The download action opens GitHub in the browser, so the app
 * stays a plain APK notice and never asks for install permission.
 */
@Composable
private fun UpdateSection(
    state: AboutUpdateState,
    currentVersion: String,
    onCheck: () -> Unit,
    uriHandler: UriHandler,
) {
    val checking = state is AboutUpdateState.Checking
    val progress: (@Composable () -> Unit)? = if (checking) {
        {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
            )
        }
    } else {
        null
    }

    CaregiverSettingsGroup(
        title = stringResource(R.string.about_updates),
        items = listOf(
            CaregiverSettingsRow(
                title = stringResource(R.string.about_check_updates),
                icon = Icons.Outlined.Refresh,
                description = when (state) {
                    AboutUpdateState.Checking ->
                        stringResource(R.string.about_update_checking)
                    AboutUpdateState.UpToDate ->
                        stringResource(R.string.about_update_up_to_date, currentVersion)
                    is AboutUpdateState.Available ->
                        stringResource(R.string.about_update_available, state.version, currentVersion)
                    AboutUpdateState.Failed ->
                        stringResource(R.string.about_update_failed)
                },
                trailingContent = progress,
                enabled = !checking,
                onClick = onCheck,
            ),
        ),
    )

    if (state is AboutUpdateState.Available) {
        Spacer(Modifier.height(12.dp))
        AvailableUpdateCard(state = state, uriHandler = uriHandler)
    }
}

@Composable
private fun AvailableUpdateCard(
    state: AboutUpdateState.Available,
    uriHandler: UriHandler,
) {
    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = stringResource(R.string.about_update_available_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = stringResource(
                    R.string.about_update_available_description,
                    state.version,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { uriHandler.openUri(state.downloadUrl) },
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Download,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.about_update_download),
                    fontWeight = FontWeight.SemiBold,
                )
            }

            TextButton(
                onClick = { uriHandler.openUri(state.releaseUrl) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.about_update_view_release))
            }
        }
    }
}

/** App identity card: icon, localized name, version and build badge. */
@Composable
private fun AppHeaderCard() {
    ElevatedCard(
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(24.dp),
        ) {
            // Placeholder app mark: the launcher foreground tinted to the
            // active accent. Swap for a dedicated about artwork later.
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                colorFilter = ColorFilter.tint(
                    color = MaterialTheme.colorScheme.primary,
                    blendMode = BlendMode.SrcIn,
                ),
                modifier = Modifier.size(64.dp),
            )

            Spacer(Modifier.width(20.dp))

            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 34.sp,
                    letterSpacing = (-0.5).sp,
                )

                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    InfoChip(
                        text = BuildConfig.VERSION_NAME,
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        contentColor = MaterialTheme.colorScheme.primary,
                    )
                    if (BuildConfig.LOCAL_MODE) {
                        InfoChip(
                            text = "LOCAL",
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                    if (BuildConfig.DEBUG) {
                        InfoChip(
                            text = "DEBUG",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoChip(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

/** Lead-developer hero card, mirroring Metrolist's contributor card. */
@Composable
private fun LeadDeveloperCard(uriHandler: UriHandler) {
    ElevatedCard(
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Placeholder avatar. Metrolist uses GitHub profile photos
                // with cookie shapes; this build has no image loader, so the
                // slot shows a person glyph until artwork is supplied.
                Surface(
                    modifier = Modifier.size(110.dp),
                    shape = RoundedCornerShape(36.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    tonalElevation = 4.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(56.dp),
                        )
                    }
                }

                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = stringResource(R.string.about_lead_developer_name),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 34.sp,
                        letterSpacing = (-0.5).sp,
                    )
                    Text(
                        text = stringResource(R.string.about_lead_developer_role),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            DeveloperSocials(uriHandler)

            Spacer(Modifier.height(16.dp))

            // Placeholder support button: disabled until a real support
            // destination exists, so it never opens a dead/fake page.
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocalCafe,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.about_buy_coffee),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                )
            }
        }
    }
}

/**
 * Three equal social buttons in the Metrolist style. Only GitHub is wired;
 * the website and Instagram slots are disabled placeholders for now.
 */
@Composable
private fun DeveloperSocials(uriHandler: UriHandler) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilledTonalButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.weight(1f).height(48.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Language,
                contentDescription = stringResource(R.string.about_website),
            )
        }
        FilledTonalButton(
            onClick = { uriHandler.openUri(GITHUB_PROFILE_URL) },
            modifier = Modifier.weight(1f).height(48.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Code,
                contentDescription = stringResource(R.string.about_github),
            )
        }
        FilledTonalButton(
            onClick = {},
            enabled = false,
            modifier = Modifier.weight(1f).height(48.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.AlternateEmail,
                contentDescription = stringResource(R.string.about_instagram),
            )
        }
    }
}

/**
 * The people who built the app with the lead developer, in Metrolist's
 * style: a grouped card, one row per person with a round mark, the name, the
 * part they own, and a GitHub mark that opens their profile.
 */
@Composable
private fun CollaboratorsSection(uriHandler: UriHandler) {
    CaregiverSettingsGroup(
        title = stringResource(R.string.about_collaborators),
        items = collaborators.map { collaborator ->
            val name = stringResource(collaborator.nameRes)
            CaregiverSettingsRow(
                title = name,
                description = stringResource(collaborator.roleRes),
                leadingContent = { CollaboratorMark(name) },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Outlined.Code,
                        contentDescription = stringResource(R.string.about_open_github_profile),
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = { uriHandler.openUri(collaborator.githubUrl) },
            )
        },
    )
}

/**
 * The round mark that stands in for a profile photo.
 *
 * This build has no image loader, so a GitHub avatar would be a broken
 * image; the lead developer card above uses a placeholder for the same
 * reason. The person's initial identifies them without one.
 */
@Composable
private fun CollaboratorMark(name: String) {
    Surface(
        modifier = Modifier.size(44.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 4.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = name.trim().take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** GitHub, repository, issues and documentation links. */
@Composable
private fun CommunityAndInfo(uriHandler: UriHandler) {
    CaregiverSettingsGroup(
        title = stringResource(R.string.about_community_info),
        items = listOf(
            CaregiverSettingsRow(
                title = stringResource(R.string.about_github),
                icon = Icons.Outlined.Code,
                description = stringResource(R.string.about_github_description),
                onClick = { uriHandler.openUri(GITHUB_PROFILE_URL) },
            ),
            CaregiverSettingsRow(
                title = stringResource(R.string.about_repository),
                icon = Icons.Outlined.Folder,
                description = stringResource(R.string.about_repository_description),
                onClick = { uriHandler.openUri(REPOSITORY_URL) },
            ),
            CaregiverSettingsRow(
                title = stringResource(R.string.about_report_issue),
                icon = Icons.Outlined.BugReport,
                description = stringResource(R.string.about_report_issue_description),
                onClick = { uriHandler.openUri(ISSUES_URL) },
            ),
            CaregiverSettingsRow(
                title = stringResource(R.string.about_documentation),
                icon = Icons.Outlined.MenuBook,
                description = stringResource(R.string.about_documentation_description),
                onClick = { uriHandler.openUri(DOCUMENTATION_URL) },
            ),
        ),
    )
}
