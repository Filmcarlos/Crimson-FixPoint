package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppScreen
import com.example.model.GpsLockState
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CrimsonBorderGlow
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.DeepCrimsonGlow
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextHudMuted
import com.example.ui.theme.TextHudPrimary
import com.example.ui.theme.TextHudSecondary

val PrecisionPanelShape = CutCornerShape(
    topStart = 14.dp,
    topEnd = 4.dp,
    bottomEnd = 14.dp,
    bottomStart = 4.dp
)

val PrecisionButtonShape = CutCornerShape(
    topStart = 10.dp,
    topEnd = 2.dp,
    bottomEnd = 10.dp,
    bottomStart = 2.dp
)

/**
 * Designer-crafted precision beveled panel with soft ambient crimson glow and corner bracket accents.
 * Avoids generic rounded-rectangle card templates.
 */
@Composable
fun CrimsonPrecisionPanel(
    modifier: Modifier = Modifier,
    borderColor: Color = CrimsonBorderGlow,
    glowAlpha: Float = 0.14f,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            CrimsonRed.copy(alpha = glowAlpha),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.5f),
                        radius = size.maxDimension * 0.7f
                    )
                )
            }
            .clip(PrecisionPanelShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF130E12),
                        Color(0xFF0B0A0E),
                        Color(0xFF08080B)
                    )
                )
            )
            .border(1.dp, borderColor, PrecisionPanelShape)
            .drawWithContent {
                drawContent()
                val stroke = 2.dp.toPx()
                val tick = 10.dp.toPx()
                val accent = CrimsonNeon.copy(alpha = 0.65f)
                // Top-Right corner bracket
                drawLine(accent, Offset(size.width - tick, stroke), Offset(size.width - stroke, stroke), stroke)
                drawLine(accent, Offset(size.width - stroke, stroke), Offset(size.width - stroke, tick), stroke)
                // Bottom-Left corner bracket
                drawLine(accent, Offset(stroke, size.height - tick), Offset(stroke, size.height - stroke), stroke)
                drawLine(accent, Offset(stroke, size.height - stroke), Offset(tick, size.height - stroke), stroke)
            }
            .padding(16.dp),
        content = content
    )
}

/**
 * Custom chamfered octagonal action button (replaces default Material rounded button).
 */
@Composable
fun ChamferedActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPrimaryCrimson: Boolean = false,
    testTag: String? = null
) {
    val bgBrush = when {
        !enabled -> Brush.linearGradient(listOf(Color(0xFF141419), Color(0xFF0E0E12)))
        isPrimaryCrimson -> Brush.linearGradient(listOf(CrimsonRed, DeepCrimsonGlow))
        else -> Brush.linearGradient(listOf(Color(0xFF1B1217), Color(0xFF110D10)))
    }
    val borderCol = when {
        !enabled -> Color(0xFF27272F)
        isPrimaryCrimson -> CrimsonNeon
        else -> CrimsonBorderGlow
    }
    val contentCol = when {
        !enabled -> Color(0xFF52525B)
        isPrimaryCrimson -> Color.White
        else -> TextHudPrimary
    }

    val taggedModifier = if (testTag != null) modifier.testTag(testTag) else modifier

    Box(
        contentAlignment = Alignment.Center,
        modifier = taggedModifier
            .minimumInteractiveComponentSize()
            .height(48.dp)
            .clip(PrecisionButtonShape)
            .background(bgBrush)
            .border(1.dp, borderCol, PrecisionButtonShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (enabled && !isPrimaryCrimson) CrimsonNeon else contentCol,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = contentCol,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Top Application Header Bar (Formal Thai, Crimson-Black theme).
 */
@Composable
fun PrecisionTopBar(
    lockState: GpsLockState,
    satellitesUsed: Int,
    satellitesVisible: Int,
    modifier: Modifier = Modifier
) {
    val statusColor by animateColorAsState(
        targetValue = when (lockState) {
            GpsLockState.OFF -> Color(0xFF71717A)
            GpsLockState.SEARCHING -> AmberWarning
            GpsLockState.LOCKED -> CrimsonNeon
        },
        animationSpec = tween(350),
        label = "header_status_color"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF14080C), ObsidianBlack)
                )
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CutCornerShape(8.dp))
                    .background(Brush.linearGradient(listOf(CrimsonRed, DeepCrimsonGlow)))
                    .border(1.dp, CrimsonNeon, CutCornerShape(8.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.GpsFixed,
                    contentDescription = "ไอคอนระบบพิกัด",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Column {
                Text(
                    text = "CRIMSON FIXPOINT",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 16.5.sp,
                        letterSpacing = 1.2.sp
                    ),
                    color = TextHudPrimary
                )
                Text(
                    text = lockState.labelTh,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    color = statusColor
                )
            }
        }

        Box(
            modifier = Modifier
                .clip(PrecisionButtonShape)
                .background(Color(0xFF120C10))
                .border(1.dp, statusColor.copy(alpha = 0.55f), PrecisionButtonShape)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Text(
                text = "ดาวเทียม $satellitesUsed/$satellitesVisible",
                style = MaterialTheme.typography.labelMedium,
                color = TextHudSecondary
            )
        }
    }
}

/**
 * Custom Precision Bottom Navigation Bar with 3 Thai tabs: หน้าหลัก, พิกัดที่บันทึก, การตั้งค่า.
 */
@Composable
fun PrecisionBottomBar(
    currentScreen: AppScreen,
    savedCount: Int,
    onSelectScreen: (AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF09080C))
            .border(1.dp, CrimsonBorderGlow)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AppScreen.entries.forEach { screen ->
            val selected = currentScreen == screen
            val icon = when (screen) {
                AppScreen.HOME -> Icons.Default.GpsFixed
                AppScreen.SAVED_LOCATIONS -> Icons.Default.Bookmarks
                AppScreen.SETTINGS -> Icons.Default.Settings
            }
            val labelText = if (screen == AppScreen.SAVED_LOCATIONS && savedCount > 0) {
                "${screen.titleTh} ($savedCount)"
            } else {
                screen.titleTh
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .height(48.dp)
                    .clip(PrecisionButtonShape)
                    .background(
                        if (selected) {
                            Brush.linearGradient(listOf(CrimsonRed.copy(alpha = 0.32f), DeepCrimsonGlow.copy(alpha = 0.22f)))
                        } else {
                            Brush.linearGradient(listOf(Color(0xFF111015), Color(0xFF0C0B0F)))
                        }
                    )
                    .border(
                        width = if (selected) 1.5.dp else 1.dp,
                        color = if (selected) CrimsonNeon else Color(0xFF241D22),
                        shape = PrecisionButtonShape
                    )
                    .clickable { onSelectScreen(screen) }
                    .testTag("nav_tab_${screen.name.lowercase()}")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 6.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = screen.titleTh,
                        tint = if (selected) CrimsonNeon else TextHudMuted,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = labelText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 12.5.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (selected) TextHudPrimary else TextHudMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
