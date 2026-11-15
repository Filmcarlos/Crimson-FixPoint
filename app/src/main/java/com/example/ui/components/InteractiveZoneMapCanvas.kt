package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GeoMath
import com.example.model.SavedLocationItem
import com.example.model.ZoneMonitorStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonBorderGlow
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.DeepCrimsonGlow
import com.example.ui.theme.EmeraldSRank
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextHudMuted
import com.example.ui.theme.TextHudPrimary
import com.example.ui.theme.TextHudSecondary
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max

/**
 * Interactive Mercator Zone Map:
 * - Displays saved pins and highlights the active work-zone pin
 * - Renders a real-time translucent crimson radius circle (0.0 - 30.0 km) with smooth expand/shrink animation & glowing border
 * - Auto-zooms smoothly whenever the radius is adjusted so the entire circle fits cleanly in view
 * - Displays the user's real GPS position as a live dot with a connecting vector line and real-time distance readout
 * - Supports tapping directly anywhere on the map to place or move a pin ("แตะเลือกบนแผนที่")
 */
@Composable
fun InteractiveZoneMapCanvas(
    activePin: SavedLocationItem?,
    allPins: List<SavedLocationItem>,
    userLatitude: Double?,
    userLongitude: Double?,
    radiusKm: Float,
    gpsBufferMeters: Int,
    zoneStatus: ZoneMonitorStatus,
    distanceFromPinMeters: Double?,
    onMapTapPin: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
    mapHeightDp: Int = 270
) {
    var canvasSize by remember { mutableStateOf(IntSize(800, 600)) }
    var manualZoomMultiplier by remember { mutableFloatStateOf(1.0f) }

    // Reset manual zoom multiplier whenever radiusKm changes so map auto-zooms to fit the full circle!
    LaunchedEffect(radiusKm, activePin?.id) {
        manualZoomMultiplier = 1.0f
    }

    val centerLat = activePin?.latitude ?: userLatitude ?: 13.756331
    val centerLng = activePin?.longitude ?: userLongitude ?: 100.501762

    // Smoothly animate radius in meters
    val targetRadiusMeters = max(0f, radiusKm * 1000f)
    val animatedRadiusMeters by animateFloatAsState(
        targetValue = targetRadiusMeters,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "animated_zone_radius_meters"
    )

    // Calculate auto-zoom span in meters so the entire radius circle + buffer always fits inside the viewport
    val minSpanMeters = max((targetRadiusMeters + gpsBufferMeters) * 2.65f, 600f) * manualZoomMultiplier
    val animatedSpanMeters by animateFloatAsState(
        targetValue = minSpanMeters,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "animated_map_span_meters"
    )

    val isOutsidePulseActive = zoneStatus == ZoneMonitorStatus.OUTSIDE_ZONE
    val pulseFraction: Float = if (isOutsidePulseActive) {
        val infiniteTransition = rememberInfiniteTransition(label = "zone_map_pulse")
        val anim by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(550, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "outside_zone_pulse"
        )
        anim
    } else {
        0f
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(mapHeightDp.dp)
                .clip(PrecisionPanelShape)
                .background(Color(0xFF07080C))
                .border(
                    width = 1.5.dp,
                    color = when (zoneStatus) {
                        ZoneMonitorStatus.INSIDE_ZONE -> EmeraldSRank.copy(alpha = 0.85f)
                        ZoneMonitorStatus.OUTSIDE_ZONE -> CrimsonNeon
                        else -> CrimsonBorderGlow
                    },
                    shape = PrecisionPanelShape
                )
                .onSizeChanged { size ->
                    if (size.width > 0 && size.height > 0) {
                        canvasSize = size
                    }
                }
                .pointerInput(centerLat, centerLng, animatedSpanMeters, canvasSize) {
                    detectTapGestures { tapOffset ->
                        val minDimPx = max(1f, minOf(canvasSize.width, canvasSize.height).toFloat())
                        val metersPerPx = animatedSpanMeters / minDimPx
                        val dxMeters = (tapOffset.x - canvasSize.width / 2f) * metersPerPx
                        val dyMeters = (canvasSize.height / 2f - tapOffset.y) * metersPerPx

                        val deltaLat = dyMeters / 111_320.0
                        val cosLat = cos(Math.toRadians(centerLat)).coerceAtLeast(0.05)
                        val deltaLng = dxMeters / (111_320.0 * cosLat)

                        val tappedLat = (centerLat + deltaLat).coerceIn(-85.0, 85.0)
                        val tappedLng = (centerLng + deltaLng).coerceIn(-180.0, 180.0)
                        onMapTapPin(tappedLat, tappedLng)
                    }
                }
                .testTag("interactive_zone_map_canvas")
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val minDim = max(1f, size.minDimension)
                val metersPerPx = animatedSpanMeters / minDim
                val cosCenterLat = cos(Math.toRadians(centerLat)).coerceAtLeast(0.05)

                fun projectToScreen(lat: Double, lng: Double): Offset {
                    val dyMeters = (lat - centerLat) * 111_320.0
                    val dxMeters = (lng - centerLng) * 111_320.0 * cosCenterLat
                    val x = cx + (dxMeters / metersPerPx).toFloat()
                    val y = cy - (dyMeters / metersPerPx).toFloat()
                    return Offset(x, y)
                }

                // 1. Deep Obsidian Cartographic Background + Lat/Lon Graticule Grid
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF130B10),
                            Color(0xFF09080D),
                            ObsidianBlack
                        ),
                        center = Offset(cx, cy),
                        radius = size.maxDimension * 0.75f
                    )
                )

                val gridStepPx = size.minDimension / 6f
                val gridColor = Color(0xFF1F1720)
                var gx = cx % gridStepPx
                while (gx < size.width) {
                    drawLine(gridColor, Offset(gx, 0f), Offset(gx, size.height), 1.dp.toPx())
                    gx += gridStepPx
                }
                var gy = cy % gridStepPx
                while (gy < size.height) {
                    drawLine(gridColor, Offset(0f, gy), Offset(size.width, gy), 1.dp.toPx())
                    gy += gridStepPx
                }

                // Crosshair axes at map center
                drawLine(
                    color = CrimsonBorderGlow.copy(alpha = 0.45f),
                    start = Offset(0f, cy),
                    end = Offset(size.width, cy),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )
                drawLine(
                    color = CrimsonBorderGlow.copy(alpha = 0.45f),
                    start = Offset(cx, 0f),
                    end = Offset(cx, size.height),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )

                // 2. Active Pin & Real-Time Glowing Crimson Radius Circle
                if (activePin != null) {
                    val pinCenter = projectToScreen(activePin.latitude, activePin.longitude)
                    val radiusPx = (animatedRadiusMeters / metersPerPx).coerceAtLeast(6.dp.toPx())
                    val bufferRadiusPx = ((animatedRadiusMeters + gpsBufferMeters) / metersPerPx)
                        .coerceAtLeast(radiusPx)

                    // Outer soft radial glow around the zone circle
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                CrimsonRed.copy(alpha = 0.24f + pulseFraction * 0.18f),
                                DeepCrimsonGlow.copy(alpha = 0.14f),
                                Color.Transparent
                            ),
                            center = pinCenter,
                            radius = radiusPx * 1.18f
                        ),
                        radius = radiusPx * 1.18f,
                        center = pinCenter
                    )

                    // Translucent crimson fill inside the zone circle
                    drawCircle(
                        color = CrimsonRed.copy(alpha = 0.18f + pulseFraction * 0.12f),
                        radius = radiusPx,
                        center = pinCenter
                    )

                    // GPS Buffer ring (0-100m) outside the main radius
                    if (gpsBufferMeters > 0) {
                        drawCircle(
                            color = AmberWarning.copy(alpha = 0.55f),
                            radius = bufferRadiusPx,
                            center = pinCenter,
                            style = Stroke(
                                width = 1.2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)
                            )
                        )
                    }

                    // Glowing neon crimson border of the zone circle
                    drawCircle(
                        color = CrimsonNeon.copy(alpha = 0.4f),
                        radius = radiusPx,
                        center = pinCenter,
                        style = Stroke(width = 5.dp.toPx())
                    )
                    drawCircle(
                        color = CrimsonNeon,
                        radius = radiusPx,
                        center = pinCenter,
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // 3. Draw Secondary Saved Pins
                allPins.forEach { item ->
                    if (item.id != activePin?.id) {
                        val pos = projectToScreen(item.latitude, item.longitude)
                        if (pos.x in -20f..(size.width + 20f) && pos.y in -20f..(size.height + 20f)) {
                            drawCircle(
                                color = Color(0xFF52525B),
                                radius = 5.dp.toPx(),
                                center = pos
                            )
                            drawCircle(
                                color = TextHudSecondary,
                                radius = 5.dp.toPx(),
                                center = pos,
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                        }
                    }
                }

                // 4. Draw Geodesic Line & User's Real GPS Position Marker
                if (userLatitude != null && userLongitude != null) {
                    val rawUserOffset = projectToScreen(userLatitude, userLongitude)
                    val clampedUserOffset = Offset(
                        x = rawUserOffset.x.coerceIn(18.dp.toPx(), size.width - 18.dp.toPx()),
                        y = rawUserOffset.y.coerceIn(18.dp.toPx(), size.height - 18.dp.toPx())
                    )
                    val userMarkerColor = when (zoneStatus) {
                        ZoneMonitorStatus.OUTSIDE_ZONE -> CrimsonNeon
                        else -> EmeraldSRank
                    }

                    if (activePin != null) {
                        val pinOffset = projectToScreen(activePin.latitude, activePin.longitude)
                        drawLine(
                            color = userMarkerColor.copy(alpha = 0.8f),
                            start = pinOffset,
                            end = clampedUserOffset,
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                        )
                    }

                    // User real GPS outer halo + center dot
                    drawCircle(
                        color = userMarkerColor.copy(alpha = 0.28f),
                        radius = 13.dp.toPx(),
                        center = clampedUserOffset
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 6.5.dp.toPx(),
                        center = clampedUserOffset
                    )
                    drawCircle(
                        color = userMarkerColor,
                        radius = 4.5.dp.toPx(),
                        center = clampedUserOffset
                    )
                }

                // 5. Draw Active Pin Marker on top
                if (activePin != null) {
                    val pinPos = projectToScreen(activePin.latitude, activePin.longitude)
                    val pinPath = Path().apply {
                        moveTo(pinPos.x, pinPos.y)
                        lineTo(pinPos.x - 8.dp.toPx(), pinPos.y - 18.dp.toPx())
                        lineTo(pinPos.x, pinPos.y - 24.dp.toPx())
                        lineTo(pinPos.x + 8.dp.toPx(), pinPos.y - 18.dp.toPx())
                        close()
                    }
                    drawPath(pinPath, color = CrimsonNeon)
                    drawCircle(
                        color = Color.White,
                        radius = 3.5.dp.toPx(),
                        center = Offset(pinPos.x, pinPos.y - 17.dp.toPx())
                    )
                }
            }

            // Top-Left Map HUD Badge: Active Pin & Radius Summary
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(PrecisionButtonShape)
                    .background(Color(0xD90A090E))
                    .border(1.dp, CrimsonBorderGlow, PrecisionButtonShape)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = CrimsonNeon,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = activePin?.name ?: "ยังไม่ได้ปักหมุดโซน",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                        color = TextHudPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = String.format(
                        Locale.US,
                        "รัศมีโซน: %.1f กม. • เผื่อ GPS: %d ม.",
                        radiusKm,
                        gpsBufferMeters
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = CrimsonNeon
                )
            }

            // Top-Right Zoom / Recenter Controls
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(PrecisionButtonShape)
                        .background(Color(0xD9120D11))
                        .border(1.dp, CrimsonBorderGlow, PrecisionButtonShape)
                        .clickable {
                            manualZoomMultiplier = (manualZoomMultiplier * 0.75f).coerceIn(0.25f, 4.0f)
                        }
                        .testTag("btn_map_zoom_in")
                ) {
                    Text("+", color = TextHudPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(PrecisionButtonShape)
                        .background(Color(0xD9120D11))
                        .border(1.dp, CrimsonBorderGlow, PrecisionButtonShape)
                        .clickable {
                            manualZoomMultiplier = (manualZoomMultiplier * 1.35f).coerceIn(0.25f, 4.0f)
                        }
                        .testTag("btn_map_zoom_out")
                ) {
                    Text("−", color = TextHudPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(PrecisionButtonShape)
                        .background(Color(0xD9120D11))
                        .border(1.dp, CrimsonNeon, PrecisionButtonShape)
                        .clickable { manualZoomMultiplier = 1.0f }
                        .testTag("btn_map_auto_fit")
                ) {
                    Icon(
                        imageVector = Icons.Default.CenterFocusStrong,
                        contentDescription = "ซูมพอดีวงกลมอัตโนมัติ",
                        tint = CrimsonNeon,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Bottom Map Overlay: Live User GPS Position & Current Distance from Pin
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(10.dp)
                    .clip(PrecisionButtonShape)
                    .background(Color(0xE609080D))
                    .border(1.dp, CrimsonBorderGlow, PrecisionButtonShape)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        tint = if (userLatitude != null) EmeraldSRank else TextHudMuted,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = if (userLatitude != null && userLongitude != null) {
                            String.format(Locale.US, "คุณ: %.5f, %.5f", userLatitude, userLongitude)
                        } else {
                            "ตำแหน่งคุณ: รอสัญญาณ GPS จริง"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = TextHudSecondary
                    )
                }

                Text(
                    text = "ห่างหมุด: ${GeoMath.formatShortZoneDistanceTh(distanceFromPinMeters)}",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                    color = when (zoneStatus) {
                        ZoneMonitorStatus.INSIDE_ZONE -> EmeraldSRank
                        ZoneMonitorStatus.OUTSIDE_ZONE -> CrimsonNeon
                        else -> TextHudPrimary
                    },
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("map_live_distance_readout")
                )
            }
        }

        // Map Legend Bar (Formal Thai, no tutorial copy)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.TouchApp,
                    contentDescription = null,
                    tint = CrimsonNeon,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = if (activePin != null) {
                        String.format(
                            Locale.US,
                            "พิกัดหมุด: %.6f, %.6f",
                            activePin.latitude,
                            activePin.longitude
                        )
                    } else {
                        "พิกัดหมุด: —"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextHudMuted
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = String.format(Locale.US, "มุมมองอัตโนมัติ %.1f กม.", animatedSpanMeters / 1000f),
                style = MaterialTheme.typography.labelSmall,
                color = TextHudSecondary
            )
        }
    }
}
