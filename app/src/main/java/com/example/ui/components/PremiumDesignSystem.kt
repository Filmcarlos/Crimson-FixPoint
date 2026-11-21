package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AppScreen
import com.example.model.EarningsChartBucket
import com.example.model.ExpenseCategoryBreakdown
import com.example.model.GpsLockState
import com.example.model.OrderGrade
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CrimsonBorderGlow
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.CrimsonSubtleBg
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.DeepCrimson
import com.example.ui.theme.DeepCrimsonGlow
import com.example.ui.theme.EmeraldSRank
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.SurfaceLevel1
import com.example.ui.theme.SurfaceLevel2
import com.example.ui.theme.TextHudMuted
import com.example.ui.theme.TextHudPrimary
import com.example.ui.theme.TextHudSecondary
import com.example.ui.theme.VioletInsight
import java.util.Locale

private val CardShape20 = RoundedCornerShape(20.dp)
private val HeroShape24 = RoundedCornerShape(24.dp)
private val ButtonShape14 = RoundedCornerShape(14.dp)

@Composable
fun PremiumCard(
    modifier: Modifier = Modifier,
    accentBorder: Color = HairlineBorder,
    containerColor: Color = SurfaceLevel1,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape20,
        color = containerColor,
        border = BorderStroke(1.dp, accentBorder)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun CrimsonHeroCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val brush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF2B0B17),
            Color(0xFF170D14),
            Color(0xFF100E15)
        ),
        start = Offset(0f, 0f),
        end = Offset(900f, 600f)
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(HeroShape24)
            .background(brush)
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        CrimsonNeon.copy(alpha = 0.65f),
                        CrimsonRed.copy(alpha = 0.25f),
                        HairlineBorder
                    )
                ),
                shape = HeroShape24
            )
            .padding(contentPadding)
    ) {
        Column(content = content)
    }
}

@Composable
fun PremiumPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    containerColor: Color = CrimsonRed,
    testTagName: String = ""
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape14,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = Color.White,
            disabledContainerColor = SurfaceElevated,
            disabledContentColor = TextHudMuted
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(if (testTagName.isNotEmpty()) Modifier.testTag(testTagName) else Modifier)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PremiumSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accentColor: Color = CrimsonNeon,
    enabled: Boolean = true,
    testTagName: String = ""
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape14,
        border = BorderStroke(1.dp, if (enabled) accentColor.copy(alpha = 0.65f) else HairlineBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = SurfaceLevel2,
            contentColor = TextHudPrimary,
            disabledContentColor = TextHudMuted
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        modifier = modifier
            .heightIn(min = 48.dp)
            .then(if (testTagName.isNotEmpty()) Modifier.testTag(testTagName) else Modifier)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) accentColor else TextHudMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun StatusPillBadge(
    text: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accentColor.copy(alpha = 0.16f))
            .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(13.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun SectionHeaderRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = TextHudPrimary,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextHudMuted
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

@Composable
fun MetricStatCard(
    label: String,
    value: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    subtext: String? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = SurfaceLevel1,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.16f))
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextHudMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = TextHudPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtext != null) {
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextHudSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun CrimsonTopHeader(
    currentScreen: AppScreen,
    lockState: GpsLockState,
    satellitesUsed: Int,
    isWorkSessionActive: Boolean,
    onGpsPillClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gpsColor by animateColorAsState(
        targetValue = when (lockState) {
            GpsLockState.LOCKED -> EmeraldSRank
            GpsLockState.SEARCHING -> AmberWarning
            GpsLockState.OFF -> TextHudMuted
        },
        label = "gpsPillColor"
    )
    val gpsIcon = when (lockState) {
        GpsLockState.LOCKED -> Icons.Default.GpsFixed
        GpsLockState.SEARCHING -> Icons.Default.GpsNotFixed
        GpsLockState.OFF -> Icons.Default.GpsOff
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(ObsidianBlack)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(CrimsonRed, DeepCrimsonGlow)
                        )
                    )
            ) {
                Text(
                    text = "CF",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Column {
                Text(
                    text = "CRIMSON FIXPOINT",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = CrimsonNeon
                )
                Text(
                    text = currentScreen.titleTh,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextHudPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isWorkSessionActive) {
                StatusPillBadge(
                    text = "กำลังวิ่งงาน",
                    accentColor = EmeraldSRank
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(SurfaceLevel2)
                    .border(1.dp, gpsColor.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                    .clickable(onClick = onGpsPillClick)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("top_bar_gps_status")
            ) {
                Icon(
                    imageVector = gpsIcon,
                    contentDescription = "สถานะ GPS",
                    tint = gpsColor,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = when (lockState) {
                        GpsLockState.LOCKED -> if (satellitesUsed > 0) "GPS $satellitesUsed" else "GPS ON"
                        GpsLockState.SEARCHING -> "ค้นหา"
                        GpsLockState.OFF -> "GPS ปิด"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = gpsColor,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun navIconFor(screen: AppScreen, selected: Boolean): ImageVector = when (screen) {
    AppScreen.DASHBOARD -> if (selected) Icons.Filled.Dashboard else Icons.Outlined.Dashboard
    AppScreen.AI_ASSISTANT -> if (selected) Icons.Filled.AutoAwesome else Icons.Outlined.AutoAwesome
    AppScreen.SMART_MAP -> if (selected) Icons.Filled.Map else Icons.Outlined.Map
    AppScreen.EARNINGS -> if (selected) Icons.Filled.AccountBalanceWallet else Icons.Outlined.AccountBalanceWallet
    AppScreen.PROFILE -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
}

@Composable
fun CrimsonBottomNavBar(
    currentScreen: AppScreen,
    onSelectScreen: (AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = CarbonDark,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppScreen.entries.forEach { screen ->
                val selected = screen == currentScreen
                val contentColor by animateColorAsState(
                    targetValue = if (selected) CrimsonNeon else TextHudMuted,
                    label = "bottomNavColor_${screen.name}"
                )
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onSelectScreen(screen) }
                        .padding(vertical = 4.dp)
                        .testTag("nav_tab_${screen.name.lowercase(Locale.ROOT)}")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(if (selected) CrimsonSubtleBg else Color.Transparent)
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = navIconFor(screen, selected),
                            contentDescription = screen.titleTh,
                            tint = contentColor,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Text(
                        text = screen.titleTh,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 10.5.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun CrimsonNavRail(
    currentScreen: AppScreen,
    onSelectScreen: (AppScreen) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier.fillMaxHeight(),
        containerColor = CarbonDark,
        contentColor = TextHudPrimary
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        AppScreen.entries.forEach { screen ->
            val selected = screen == currentScreen
            NavigationRailItem(
                selected = selected,
                onClick = { onSelectScreen(screen) },
                icon = {
                    Icon(
                        imageVector = navIconFor(screen, selected),
                        contentDescription = screen.titleTh
                    )
                },
                label = {
                    Text(
                        text = screen.titleTh,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = CrimsonNeon,
                    selectedTextColor = CrimsonNeon,
                    indicatorColor = CrimsonSubtleBg,
                    unselectedIconColor = TextHudMuted,
                    unselectedTextColor = TextHudMuted
                ),
                modifier = Modifier.testTag("nav_tab_${screen.name.lowercase(Locale.ROOT)}")
            )
        }
    }
}

@Composable
fun SmartScoreRingCanvas(
    score: Int,
    grade: OrderGrade,
    modifier: Modifier = Modifier,
    ringSize: Dp = 108.dp
) {
    val gradeColor = when (grade) {
        OrderGrade.S -> EmeraldSRank
        OrderGrade.A -> CyanTelemetry
        OrderGrade.B -> AmberWarning
        OrderGrade.C -> Color(0xFFFB923C)
        OrderGrade.D -> CrimsonNeon
    }
    val animatedSweep by animateFloatAsState(
        targetValue = (score.coerceIn(0, 100) / 100f) * 360f,
        animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing),
        label = "smartScoreSweep"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(ringSize)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = 10.dp.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)
            drawArc(
                color = SurfaceElevated,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
            drawArc(
                color = gradeColor,
                startAngle = -90f,
                sweepAngle = animatedSweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = grade.label,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold
                ),
                color = gradeColor
            )
            Text(
                text = "$score/100",
                style = MaterialTheme.typography.labelSmall,
                color = TextHudSecondary
            )
        }
    }
}

@Composable
fun IncomeExpenseChartCard(
    buckets: List<EarningsChartBucket>,
    modifier: Modifier = Modifier
) {
    PremiumCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "กราฟรายได้ vs ค่าใช้จ่าย",
                style = MaterialTheme.typography.titleMedium,
                color = TextHudPrimary,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LegendDot(label = "รายได้", color = EmeraldSRank)
                LegendDot(label = "ค่าใช้จ่าย", color = CrimsonNeon)
            }
        }
        Spacer(modifier = Modifier.height(14.dp))

        val maxValue = buckets.maxOfOrNull { maxOf(it.incomeBaht, it.expenseBaht) }
            ?.takeIf { it > 0.0 } ?: 100.0

        if (buckets.isEmpty() || buckets.all { it.incomeBaht == 0.0 && it.expenseBaht == 0.0 }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceLevel2),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "ยังไม่มีรายการในช่วงเวลานี้",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextHudMuted
                )
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .testTag("earnings_bar_chart_canvas")
            ) {
                val h = size.height
                val w = size.width
                // Grid lines
                for (i in 0..3) {
                    val y = h * (i / 3f)
                    drawLine(
                        color = HairlineBorder,
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1f
                    )
                }
                val groupWidth = w / buckets.size.coerceAtLeast(1)
                val barW = (groupWidth * 0.28f).coerceIn(8.dp.toPx(), 18.dp.toPx())
                val gap = 4.dp.toPx()

                buckets.forEachIndexed { idx, bucket ->
                    val centerX = groupWidth * idx + groupWidth / 2f
                    if (bucket.isHighlighted) {
                        drawRoundRect(
                            color = CrimsonSubtleBg,
                            topLeft = Offset(groupWidth * idx + 4.dp.toPx(), 0f),
                            size = Size(groupWidth - 8.dp.toPx(), h),
                            cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                        )
                    }
                    val incH = ((bucket.incomeBaht / maxValue) * (h - 10.dp.toPx())).toFloat().coerceAtLeast(0f)
                    val expH = ((bucket.expenseBaht / maxValue) * (h - 10.dp.toPx())).toFloat().coerceAtLeast(0f)

                    if (incH > 1f) {
                        drawRoundRect(
                            color = EmeraldSRank,
                            topLeft = Offset(centerX - barW - gap / 2f, h - incH),
                            size = Size(barW, incH),
                            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                        )
                    }
                    if (expH > 1f) {
                        drawRoundRect(
                            color = CrimsonNeon,
                            topLeft = Offset(centerX + gap / 2f, h - expH),
                            size = Size(barW, expH),
                            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx())
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                buckets.forEach { bucket ->
                    Text(
                        text = bucket.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (bucket.isHighlighted) CrimsonNeon else TextHudMuted,
                        fontWeight = if (bucket.isHighlighted) FontWeight.Bold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendDot(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextHudSecondary
        )
    }
}

@Composable
fun ExpenseBreakdownCard(
    breakdown: List<ExpenseCategoryBreakdown>,
    modifier: Modifier = Modifier
) {
    val palette = listOf(CrimsonNeon, AmberWarning, VioletInsight, CyanTelemetry, EmeraldSRank)
    PremiumCard(modifier = modifier) {
        Text(
            text = "สัดส่วนค่าใช้จ่ายตามหมวดหมู่",
            style = MaterialTheme.typography.titleMedium,
            color = TextHudPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (breakdown.isEmpty()) {
            Text(
                text = "ยังไม่มีรายการค่าใช้จ่ายในช่วงเวลานี้",
                style = MaterialTheme.typography.bodySmall,
                color = TextHudMuted
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                breakdown.forEachIndexed { idx, item ->
                    val color = palette[idx % palette.size]
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = item.category,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextHudPrimary
                            )
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "฿%.0f (%.0f%%)",
                                    item.amountBaht,
                                    item.fraction * 100f
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = color
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(7.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(SurfaceElevated)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(item.fraction.coerceIn(0.03f, 1f))
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(color)
                            )
                        }
                    }
                }
            }
        }
    }
}
