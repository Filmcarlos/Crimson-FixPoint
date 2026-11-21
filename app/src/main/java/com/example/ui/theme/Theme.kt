package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val CrimsonFixPoint2ColorScheme = darkColorScheme(
    primary = CrimsonRed,
    onPrimary = Color.White,
    primaryContainer = DeepCrimson,
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
    surfaceVariant = SurfaceLevel2,
    onSurfaceVariant = TextHudSecondary,
    surfaceContainerLowest = ObsidianBlack,
    surfaceContainerLow = SurfaceLevel1,
    surfaceContainer = SurfaceLevel2,
    surfaceContainerHigh = SurfaceElevated,
    error = BloodRed,
    onError = Color.White,
    outline = HairlineBorder,
    outlineVariant = CrimsonBorderGlow
)

val CrimsonShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CrimsonFixPoint2ColorScheme,
        typography = Typography,
        shapes = CrimsonShapes,
        content = content
    )
}
