package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CrimsonTacticalColorScheme = darkColorScheme(
    primary = CrimsonRed,
    onPrimary = Color.White,
    primaryContainer = DeepCrimsonGlow,
    onPrimaryContainer = TextHudPrimary,
    secondary = EmeraldSRank,
    onSecondary = ObsidianBlack,
    secondaryContainer = Color(0xFF064E3B),
    onSecondaryContainer = Color(0xFFD1FAE5),
    tertiary = AmberWarning,
    onTertiary = ObsidianBlack,
    background = ObsidianBlack,
    onBackground = TextHudPrimary,
    surface = CarbonDark,
    onSurface = TextHudPrimary,
    surfaceVariant = CardGlassSurface,
    onSurfaceVariant = TextHudSecondary,
    error = BloodRed,
    onError = Color.White,
    outline = CrimsonBorderGlow
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CrimsonTacticalColorScheme,
        typography = Typography,
        content = content
    )
}
