package com.caregiver.mobile.core.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Guards the role set that [appLightScheme] and [appDarkScheme] actually
 * configure: every one of those roles must resolve to a caregiver token,
 * never to a Material 3 baseline colour. The Metrolist schemes are a
 * deliberate exception and are out of scope (issue #979).
 */
class ColorSchemeCompletenessTest {

    private val lightTokens = caregiverScheme(dark = false, palette = "teal")
    private val darkTokens = caregiverScheme(dark = true, palette = "teal")

    /**
     * Every token value in the teal palettes. A configured role resolving to
     * something outside this set came from an M3 default instead. White is a
     * token already (light SectionCardSurface); the dark `onError` ink is the
     * one literal the scheme functions keep deliberately, on a line the
     * issue reserves for other work.
     */
    private val allowedValues: Set<Color> = tokenValues(lightTokens) + tokenValues(darkTokens) +
        setOf(Color(0xFF2E1412))

    private fun tokenValues(cs: CaregiverColorScheme) = setOf(
        cs.Primary,
        cs.PrimarySoft,
        cs.Ink,
        cs.Muted,
        cs.Background,
        cs.Surface,
        cs.Border,
        cs.BorderSoft,
        cs.Danger,
        cs.DangerContainer,
        cs.DangerBorder,
        cs.Success,
        cs.SuccessContainer,
        cs.Info,
        cs.InfoContainer,
        cs.InfoBorder,
        cs.Warning,
        cs.WarningContainer,
        cs.PillGrayContainer,
        cs.PillGrayContent,
        cs.PillLightBlueContainer,
        cs.PillLightBlueContent,
        cs.AvatarContainer,
        cs.AvatarContent,
        cs.LockBar,
        cs.Skeleton,
        cs.AuthBackground,
        cs.AuthMuted,
        cs.AuthFieldBorder,
        cs.AuthDivider,
        cs.AuthDisabledFill,
        cs.AuthDisabledIcon,
        cs.SectionCardSurface,
        cs.SectionCardBorder,
        cs.DoneChipContainer,
        cs.DoneChipContent,
        cs.NotYetChipContainer,
        cs.NotYetChipContent,
    )

    /** The roles explicitly set by the two app scheme functions. */
    private fun ColorScheme.configuredRoles() = listOf(
        "primary" to primary,
        "onPrimary" to onPrimary,
        "primaryContainer" to primaryContainer,
        "onPrimaryContainer" to onPrimaryContainer,
        "secondary" to secondary,
        "onSecondary" to onSecondary,
        "secondaryContainer" to secondaryContainer,
        "onSecondaryContainer" to onSecondaryContainer,
        "tertiary" to tertiary,
        "onTertiary" to onTertiary,
        "background" to background,
        "onBackground" to onBackground,
        "surface" to surface,
        "onSurface" to onSurface,
        "surfaceVariant" to surfaceVariant,
        "onSurfaceVariant" to onSurfaceVariant,
        "surfaceTint" to surfaceTint,
        "outline" to outline,
        "outlineVariant" to outlineVariant,
        "error" to error,
        "onError" to onError,
        "errorContainer" to errorContainer,
        "onErrorContainer" to onErrorContainer,
        "inverseSurface" to inverseSurface,
        "inverseOnSurface" to inverseOnSurface,
        "inversePrimary" to inversePrimary,
        "surfaceContainer" to surfaceContainer,
    )

    @Test
    fun lightSchemeEveryConfiguredRoleComesFromTokens() {
        val scheme = appLightScheme(lightTokens)
        scheme.configuredRoles().forEach { (role, value) ->
            assertFalse(
                "$role resolved to $value, which is not a teal caregiver token",
                value !in allowedValues,
            )
        }
    }

    @Test
    fun darkSchemeEveryConfiguredRoleComesFromTokens() {
        val scheme = appDarkScheme(darkTokens)
        scheme.configuredRoles().forEach { (role, value) ->
            assertFalse(
                "$role resolved to $value, which is not a teal caregiver token",
                value !in allowedValues,
            )
        }
    }

    @Test
    fun addedRolesMapToTheirSpecifiedTokensInBothModes() {
        val light = appLightScheme(lightTokens)
        assertEquals(lightTokens.PrimarySoft, light.secondary)
        assertEquals(lightTokens.Ink, light.onSecondary)
        assertEquals(lightTokens.Muted, light.tertiary)
        assertEquals(lightTokens.Surface, light.onTertiary)
        assertEquals(lightTokens.BorderSoft, light.outlineVariant)
        assertEquals(lightTokens.Primary, light.surfaceTint)
        assertEquals(lightTokens.Ink, light.inverseSurface)
        assertEquals(lightTokens.Surface, light.inverseOnSurface)
        assertEquals(lightTokens.PrimarySoft, light.inversePrimary)

        val dark = appDarkScheme(darkTokens)
        assertEquals(darkTokens.PrimarySoft, dark.secondary)
        assertEquals(darkTokens.Ink, dark.onSecondary)
        assertEquals(darkTokens.Muted, dark.tertiary)
        assertEquals(darkTokens.Surface, dark.onTertiary)
        assertEquals(darkTokens.BorderSoft, dark.outlineVariant)
        assertEquals(darkTokens.Primary, dark.surfaceTint)
        assertEquals(darkTokens.Ink, dark.inverseSurface)
        assertEquals(darkTokens.Surface, dark.inverseOnSurface)
        assertEquals(darkTokens.PrimarySoft, dark.inversePrimary)
    }

    @Test
    fun noMaterialPurpleDefaultIsReachable() {
        // M3 baseline roles that used to leak through the app schemes.
        val purpleDefaults = setOf(
            Color(0xFF625B71), // secondary
            Color(0xFF7D5260), // tertiary
            Color(0xFFCAC4D0), // outlineVariant / dark onSurfaceVariant
            Color(0xFFE7E0EC), // light surfaceVariant / primaryContainer
        )
        val roles = appLightScheme(lightTokens).configuredRoles() +
            appDarkScheme(darkTokens).configuredRoles()
        roles.forEach { (role, value) ->
            assertFalse("$role fell back to an M3 purple default", value in purpleDefaults)
        }
    }
}
