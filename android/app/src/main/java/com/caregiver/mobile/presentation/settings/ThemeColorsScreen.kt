package com.caregiver.mobile.presentation.settings

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.caregiver.mobile.AppGraph
import com.caregiver.mobile.R
import com.caregiver.mobile.core.theme.CaregiverTheme
import com.caregiver.mobile.core.theme.ColorPalettes
import com.caregiver.mobile.core.theme.PaletteColors
import com.caregiver.mobile.core.theme.ThemePalette
import com.caregiver.mobile.core.theme.palettePrimary
import com.caregiver.mobile.data.SettingsStore
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeColorsScreen(graph: AppGraph, navController: NavController) {
    val scope = rememberCoroutineScope()
    val themeMode by graph.settings.themeMode.collectAsState(initial = SettingsStore.DEFAULT_THEME_MODE)
    val colorPalette by graph.settings.colorPalette.collectAsState(initial = SettingsStore.DEFAULT_COLOR_PALETTE)
    val pureBlack by graph.settings.pureBlack.collectAsState(initial = false)
    val isSystemDark = isSystemInDarkTheme()
    val dark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemDark
    }
    val selectedColor = palettePrimary(colorPalette)

    CaregiverTheme(darkTheme = dark, palette = colorPalette, pureBlack = pureBlack) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        ) {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_theme_colors)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
            // Preview
            Box(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                ThemePreviewMockup(darkTheme = dark, themeColor = selectedColor)
            }
            // Theme mode
            Text(
                text = stringResource(R.string.theme_mode),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModeCircleOption(
                    label = stringResource(R.string.settings_mode_system),
                    selected = themeMode == "system",
                    onClick = { scope.launch { graph.settings.setThemeMode("system") } },
                )
                ModeCircleOption(
                    label = stringResource(R.string.settings_mode_light),
                    selected = themeMode == "light",
                    onClick = { scope.launch { graph.settings.setThemeMode("light") } },
                )
                ModeCircleOption(
                    label = stringResource(R.string.settings_mode_dark),
                    selected = themeMode == "dark",
                    onClick = { scope.launch { graph.settings.setThemeMode("dark") } },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.pure_black), modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(
                    checked = pureBlack,
                    onCheckedChange = { scope.launch { graph.settings.setPureBlack(it) } },
                )
            }
            // Color palette
            Text(
                text = stringResource(R.string.color_palette),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(PaletteColors) { palette ->
                    val isDynamic = palette.seedColor == androidx.compose.ui.graphics.Color.Transparent
                    val isSelected = if (isDynamic) colorPalette == ColorPalettes.DYNAMIC else colorPalette == paletteKey(palette)
                    PaletteSwatch(
                        palette = palette,
                        isSelected = isSelected,
                        onClick = {
                            val key = if (isDynamic) ColorPalettes.DYNAMIC else paletteKey(palette)
                            scope.launch { graph.settings.setColorPalette(key) }
                        },
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

private fun paletteKey(palette: ThemePalette): String = when (palette.nameRes) {
    R.string.palette_crimson -> ColorPalettes.CRIMSON
    R.string.palette_rose -> ColorPalettes.ROSE
    R.string.palette_purple -> ColorPalettes.PURPLE
    R.string.palette_deep_purple -> ColorPalettes.DEEP_PURPLE
    R.string.palette_indigo -> ColorPalettes.INDIGO
    R.string.palette_blue -> ColorPalettes.BLUE
    R.string.palette_sky_blue -> ColorPalettes.SKY_BLUE
    R.string.palette_cyan -> ColorPalettes.CYAN
    R.string.palette_teal -> ColorPalettes.TEAL
    R.string.palette_green -> ColorPalettes.GREEN
    R.string.palette_light_green -> ColorPalettes.LIGHT_GREEN
    R.string.palette_lime -> ColorPalettes.LIME
    R.string.palette_yellow -> ColorPalettes.YELLOW
    R.string.palette_amber -> ColorPalettes.AMBER
    R.string.palette_orange -> ColorPalettes.ORANGE
    R.string.palette_deep_orange -> ColorPalettes.DEEP_ORANGE
    R.string.palette_brown -> ColorPalettes.BROWN
    R.string.palette_grey -> ColorPalettes.GREY
    R.string.palette_blue_grey -> ColorPalettes.BLUE_GREY
    else -> ColorPalettes.DYNAMIC
}

@Composable
private fun ModeCircleOption(label: String, selected: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(64.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun PaletteSwatch(palette: ThemePalette, isSelected: Boolean, onClick: () -> Unit) {
    val seed = palette.seedColor
    val label = stringResource(palette.nameRes)
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = if (seed == androidx.compose.ui.graphics.Color.Transparent) MaterialTheme.colorScheme.surfaceVariant else seed,
        modifier = Modifier.size(48.dp).semantics { contentDescription = label },
        border = if (isSelected) androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (seed == androidx.compose.ui.graphics.Color.Transparent) {
                Text("D", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ThemePreviewMockup(darkTheme: Boolean, themeColor: Color) {
    Card(
        modifier = Modifier.width(120.dp).height(220.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier.fillMaxWidth().height(32.dp).background(MaterialTheme.colorScheme.surfaceContainer).padding(6.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(Modifier.size(12.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            }
            Column(
                modifier = Modifier.weight(1f).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(24.dp).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.weight(1f).height(32.dp).background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(4.dp)))
                    Box(Modifier.weight(1f).height(32.dp).background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(4.dp)))
                }
            }
            Box(
                modifier = Modifier.fillMaxWidth().padding(6.dp),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Box(Modifier.size(20.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape))
            }
        }
    }
}