package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkEscapeColorScheme = darkColorScheme(
    primary = ArcaneGold,
    onPrimary = ObsidianDark,
    primaryContainer = SlateElevated,
    onPrimaryContainer = ArcaneGoldLight,
    secondary = CyanPortal,
    onSecondary = ObsidianDark,
    secondaryContainer = SlateElevated,
    onSecondaryContainer = TextPrimary,
    tertiary = EmeraldGlow,
    onTertiary = ObsidianDark,
    background = ObsidianDark,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateElevated,
    onSurfaceVariant = TextSecondary,
    error = CrimsonHazard,
    onError = TextPrimary
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkEscapeColorScheme,
        typography = Typography,
        content = content
    )
}
