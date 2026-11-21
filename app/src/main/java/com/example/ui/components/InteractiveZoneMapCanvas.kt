package com.example.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Typeface
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.LruCache
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GeoMath
import com.example.model.HotspotRecommendation
import com.example.model.MapLayerFilters
import com.example.model.PinCategory
import com.example.model.SavedLocationItem
import com.example.model.StopPoiCategory
import com.example.model.StopPoiItem
import com.example.model.ZoneMonitorStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonBorderGlow
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.EmeraldSRank
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.SurfaceLevel2
import com.example.ui.theme.TextHudPrimary
import com.example.ui.theme.TextHudSecondary
import com.example.ui.theme.VioletInsight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.Locale
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

private object OsmTileRepository {
    private val memoryCache = object : LruCache<String, ImageBitmap>(200) {}
    private val inFlightKeys: MutableSet<String> = Collections.synchronizedSet(HashSet())
    private val downloadSemaphore = Semaphore(6)

    fun getCached(z: Int, x: Int, y: Int): ImageBitmap? = memoryCache.get("$z/$x/$y")

    suspend fun fetchTile(
        context: Context,
        z: Int,
        x: Int,
        y: Int
    ): Result<ImageBitmap> = withContext(Dispatchers.IO) {
        val key = "$z/$x/$y"
        memoryCache.get(key)?.let { return@withContext Result.success(it) }

        val cacheDir = File(context.cacheDir, "osm_tiles_v101/$z/$x")
        val tileFile = File(cacheDir, "$y.png")

        if (tileFile.exists() && tileFile.length() > 100L) {
            val ageMs = System.currentTimeMillis() - tileFile.lastModified()
            if (ageMs < 7L * 24L * 3600L * 1000L) {
                val diskBmp = BitmapFactory.decodeFile(tileFile.absolutePath)?.asImageBitmap()
                if (diskBmp != null) {
                    memoryCache.put(key, diskBmp)
                    return@withContext Result.success(diskBmp)
                }
            }
        }

        if (!inFlightKeys.add(key)) {
            return@withContext Result.failure(IllegalStateException("กำลังโหลดไทล์..."))
        }

        try {
            downloadSemaphore.withPermit {
                memoryCache.get(key)?.let { return@withContext Result.success(it) }
                val tileUrl = "https://tile.openstreetmap.org/$z/$x/$y.png"
                val conn = (URL(tileUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty(
                        "User-Agent",
                        "CrimsonFixPoint/2.0.0 (Android; https://www.openstreetmap.org)"
                    )
                    setRequestProperty("Accept", "image/png,image/*;q=0.8")
                }
                try {
                    val code = conn.responseCode
                    if (code in 200..299) {
                        val bytes = conn.inputStream.use { it.readBytes() }
                        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        if (bmp != null) {
                            memoryCache.put(key, bmp)
                            runCatching {
                                cacheDir.mkdirs()
                                tileFile.writeBytes(bytes)
                            }
                            Result.success(bmp)
                        } else {
                            Result.failure(IllegalStateException("ไม่สามารถถอดรหัสภาพแผนที่ OpenStreetMap ได้"))
                        }
                    } else {
                        Result.failure(IllegalStateException("โหลดไทล์แผนที่ OpenStreetMap ไม่สำเร็จ (HTTP $code)"))
                    }
                } finally {
                    conn.disconnect()
                }
            }
        } catch (e: Exception) {
            val netErr = checkNetworkStatusErrorTh(context)
            val msg = netErr ?: "โหลดไทล์แผนที่ OpenStreetMap ไม่สำเร็จ (${e.localizedMessage ?: "เครือข่ายขัดข้อง"})"
            Result.failure(IllegalStateException(msg))
        } finally {
            inFlightKeys.remove(key)
        }
    }
}

private fun checkNetworkStatusErrorTh(context: Context): String? {
    return runCatching {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@runCatching null
        val activeNetwork = cm.activeNetwork
            ?: return@runCatching "โหมดออฟไลน์: แสดงแผนที่จากแคชและหมุดในเครื่อง"
        val caps = cm.getNetworkCapabilities(activeNetwork)
            ?: return@runCatching "โหมดออฟไลน์: แสดงแผนที่จากแคชและหมุดในเครื่อง"
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            "โหมดออฟไลน์: แสดงแผนที่จากแคชและหมุดในเครื่อง"
        } else {
            null
        }
    }.getOrNull()
}

private object WebMercatorMath {
    private const val MAX_LAT = 85.05112878

    fun lngToWorldX(lng: Double, worldSizePx: Double): Double {
        val normalizedLng = (((lng + 180.0) % 360.0 + 360.0) % 360.0) / 360.0
        return normalizedLng * worldSizePx
    }

    fun latToWorldY(lat: Double, worldSizePx: Double): Double {
        val clampedLat = lat.coerceIn(-MAX_LAT, MAX_LAT)
        val latRad = Math.toRadians(clampedLat)
        val mercN = ln(tan(PI / 4.0 + latRad / 2.0))
        return (0.5 - mercN / (2.0 * PI)) * worldSizePx
    }

    fun worldXToLng(worldX: Double, worldSizePx: Double): Double =
        ((worldX / worldSizePx) * 360.0 - 180.0).coerceIn(-180.0, 180.0)

    fun worldYToLat(worldY: Double, worldSizePx: Double): Double {
        val clampedY = worldY.coerceIn(0.0, worldSizePx)
        val n = PI - (2.0 * PI * clampedY) / worldSizePx
        return Math.toDegrees(atan(sinh(n))).coerceIn(-MAX_LAT, MAX_LAT)
    }

    fun metersPerPixel(lat: Double, zoom: Double): Double {
        val cosLat = cos(Math.toRadians(lat.coerceIn(-80.0, 80.0))).coerceAtLeast(0.05)
        return (156543.03392 * cosLat) / 2.0.pow(zoom)
    }

    fun computeIdealZoomForRadius(lat: Double, radiusMeters: Double, minViewportPx: Int): Double {
        val effectiveMeters = radiusMeters.coerceAtLeast(80.0)
        val viewportPx = minViewportPx.coerceAtLeast(220).toDouble()
        val targetMetersPerPx = (effectiveMeters * 2.56) / viewportPx
        val cosLat = cos(Math.toRadians(lat.coerceIn(-80.0, 80.0))).coerceAtLeast(0.05)
        val idealZoom = ln((156543.03392 * cosLat) / targetMetersPerPx) / ln(2.0)
        return idealZoom.coerceIn(7.5, 16.8)
    }
}

private val OsmDarkThemeColorFilter: ColorFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -0.78f, 0f, 0f, 0f, 222f,
            0f, -0.80f, 0f, 0f, 218f,
            0f, 0f, -0.74f, 0f, 224f,
            0f, 0f, 0f, 1f, 0f
        )
    )
)

/**
 * Crimson FixPoint 2.0 Smart Map (Native OpenStreetMap Canvas, zero WebView):
 * - Supports full-screen (`mapHeightDp == null`) or card-embedded height
 * - Renders FixPoint, Hotzone, Restaurant, and Fuel markers with distinct badges & distance labels
 * - Renders translucent Historical Hotspot Heatmap overlay (explicitly historical local data)
 * - Highlights top recommended next destination
 * - Supports tapping markers to open destination details sheet, or tapping empty map to pin/move
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
    mapHeightDp: Int? = null,
    layerFilters: MapLayerFilters = MapLayerFilters(),
    hotspots: List<HotspotRecommendation> = emptyList(),
    poiPins: List<StopPoiItem> = emptyList(),
    focusTargetLatLng: Pair<Double, Double>? = null,
    onMarkerSelected: ((SavedLocationItem) -> Unit)? = null,
    showBuiltInControls: Boolean = true,
    testTagName: String = "interactive_zone_map_canvas"
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val currentOnMapTapPin by rememberUpdatedState(onMapTapPin)
    val currentOnMarkerSelected by rememberUpdatedState(onMarkerSelected)
    val currentPins by rememberUpdatedState(allPins)

    val initialLat = activePin?.latitude ?: userLatitude ?: 13.756331
    val initialLng = activePin?.longitude ?: userLongitude ?: 100.501762

    var centerLat by remember { mutableDoubleStateOf(initialLat) }
    var centerLng by remember { mutableDoubleStateOf(initialLng) }
    var zoomLevel by remember { mutableDoubleStateOf(13.2) }
    var viewportSize by remember { mutableStateOf(IntSize(800, 600)) }

    var tileDrawRevision by remember { mutableIntStateOf(0) }
    var reloadTrigger by remember { mutableIntStateOf(0) }
    var loadedTileSuccessCount by remember { mutableIntStateOf(0) }
    var tileErrorMessageTh by remember {
        mutableStateOf<String?>(checkNetworkStatusErrorTh(context))
    }

    val animatedRadiusKm by animateFloatAsState(
        targetValue = radiusKm.coerceIn(0f, 30f),
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "mapRadiusKmAnim"
    )

    fun fitMapBoundsToTarget() {
        val minDim = min(viewportSize.width, viewportSize.height).coerceAtLeast(240)
        if (activePin != null) {
            centerLat = activePin.latitude
            centerLng = activePin.longitude
            val totalRadiusMeters = (radiusKm * 1000.0) + gpsBufferMeters.toDouble()
            zoomLevel = WebMercatorMath.computeIdealZoomForRadius(
                lat = activePin.latitude,
                radiusMeters = totalRadiusMeters.coerceAtLeast(150.0),
                minViewportPx = minDim
            )
            return
        }
        if (userLatitude != null && userLongitude != null) {
            centerLat = userLatitude
            centerLng = userLongitude
            zoomLevel = 14.5
        }
    }

    LaunchedEffect(activePin?.id, activePin?.latitude, activePin?.longitude, radiusKm) {
        fitMapBoundsToTarget()
    }

    LaunchedEffect(focusTargetLatLng) {
        if (focusTargetLatLng != null) {
            centerLat = focusTargetLatLng.first
            centerLng = focusTargetLatLng.second
            if (zoomLevel < 14.0) zoomLevel = 14.5
        }
    }

    val labelPaint = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = with(density) { 10.5.sp.toPx() }
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }
    val subLabelPaint = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.parseColor("#CBD5E1")
            textSize = with(density) { 9.5.sp.toPx() }
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }
    val badgeTextPaint = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = with(density) { 10.5.sp.toPx() }
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }

    val containerShape = if (mapHeightDp != null) RoundedCornerShape(18.dp) else RoundedCornerShape(0.dp)
    val hitSlopPx = with(density) { 26.dp.toPx() }

    Box(
        modifier = modifier
            .then(
                if (mapHeightDp != null) Modifier.fillMaxWidth().height(mapHeightDp.dp)
                else Modifier.fillMaxSize()
            )
            .background(Color(0xFF0E0D13), containerShape)
            .then(
                if (mapHeightDp != null) {
                    Modifier.border(
                        width = 1.dp,
                        color = when (zoneStatus) {
                            ZoneMonitorStatus.INSIDE_ZONE -> EmeraldSRank.copy(alpha = 0.7f)
                            ZoneMonitorStatus.OUTSIDE_ZONE -> CrimsonNeon
                            else -> HairlineBorder
                        },
                        shape = containerShape
                    )
                } else Modifier
            )
            .clip(containerShape)
            .onSizeChanged { size ->
                if (size.width > 0 && size.height > 0) {
                    val wasDefault = viewportSize.width == 800 && viewportSize.height == 600
                    viewportSize = size
                    if (wasDefault) fitMapBoundsToTarget()
                }
            }
            .pointerInput(Unit) {
                val touchSlopPx = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val downPos = down.position
                    var totalPanDistance = 0f
                    var wasMultiTouch = false

                    do {
                        val event = awaitPointerEvent()
                        val pressedCount = event.changes.count { it.pressed }
                        if (pressedCount >= 2) {
                            wasMultiTouch = true
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val centroid = event.calculateCentroid()
                            if (zoomChange != 1f || panChange != Offset.Zero) {
                                val oldZoom = zoomLevel
                                val newZoom = (oldZoom + ln(zoomChange.toDouble()) / ln(2.0)).coerceIn(3.0, 18.8)
                                val oldWorldSize = 256.0 * 2.0.pow(oldZoom)
                                val newWorldSize = 256.0 * 2.0.pow(newZoom)

                                val offsetFromCenterX = if (centroid != Offset.Unspecified) {
                                    (centroid.x - size.width / 2f).toDouble()
                                } else 0.0
                                val offsetFromCenterY = if (centroid != Offset.Unspecified) {
                                    (centroid.y - size.height / 2f).toDouble()
                                } else 0.0

                                val focusWorldX = WebMercatorMath.lngToWorldX(centerLng, oldWorldSize) + offsetFromCenterX
                                val focusWorldY = WebMercatorMath.latToWorldY(centerLat, oldWorldSize) + offsetFromCenterY
                                val focusLat = WebMercatorMath.worldYToLat(focusWorldY, oldWorldSize)
                                val focusLng = WebMercatorMath.worldXToLng(focusWorldX, oldWorldSize)

                                val newCenterWorldX = WebMercatorMath.lngToWorldX(focusLng, newWorldSize) - offsetFromCenterX - panChange.x
                                val newCenterWorldY = WebMercatorMath.latToWorldY(focusLat, newWorldSize) - offsetFromCenterY - panChange.y

                                zoomLevel = newZoom
                                centerLng = WebMercatorMath.worldXToLng(newCenterWorldX, newWorldSize)
                                centerLat = WebMercatorMath.worldYToLat(newCenterWorldY, newWorldSize)
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else if (pressedCount == 1 && !wasMultiTouch) {
                            val panChange = event.calculatePan()
                            totalPanDistance += panChange.getDistance()
                            if (totalPanDistance > touchSlopPx) {
                                val worldSize = 256.0 * 2.0.pow(zoomLevel)
                                val newWorldX = WebMercatorMath.lngToWorldX(centerLng, worldSize) - panChange.x
                                val newWorldY = WebMercatorMath.latToWorldY(centerLat, worldSize) - panChange.y
                                centerLng = WebMercatorMath.worldXToLng(newWorldX, worldSize)
                                centerLat = WebMercatorMath.worldYToLat(newWorldY, worldSize)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (!wasMultiTouch && totalPanDistance <= touchSlopPx) {
                        val worldSize = 256.0 * 2.0.pow(zoomLevel)
                        val centerWorldX = WebMercatorMath.lngToWorldX(centerLng, worldSize)
                        val centerWorldY = WebMercatorMath.latToWorldY(centerLat, worldSize)

                        // Check if user tapped an existing marker first
                        val markerCb = currentOnMarkerSelected
                        val tappedPin = if (markerCb != null) {
                            currentPins.firstOrNull { pin ->
                                val px = (WebMercatorMath.lngToWorldX(pin.longitude, worldSize) - centerWorldX + size.width / 2.0).toFloat()
                                val py = (WebMercatorMath.latToWorldY(pin.latitude, worldSize) - centerWorldY + size.height / 2.0).toFloat()
                                hypot((downPos.x - px).toDouble(), (downPos.y - py).toDouble()) <= hitSlopPx
                            }
                        } else null

                        if (tappedPin != null && markerCb != null) {
                            markerCb(tappedPin)
                        } else {
                            val tappedWorldX = centerWorldX + (downPos.x - size.width / 2f)
                            val tappedWorldY = centerWorldY + (downPos.y - size.height / 2f)
                            val tappedLat = WebMercatorMath.worldYToLat(tappedWorldY, worldSize)
                            val tappedLng = WebMercatorMath.worldXToLng(tappedWorldX, worldSize)
                            currentOnMapTapPin(tappedLat, tappedLng)
                        }
                    }
                }
            }
            .testTag(testTagName)
    ) {
        val intZoom = floor(zoomLevel).toInt().coerceIn(2, 19)
        val numTiles = 1 shl intZoom
        val tileWorldSize = 256.0 * numTiles
        val centerTileX = (WebMercatorMath.lngToWorldX(centerLng, tileWorldSize) / 256.0).toInt()
        val centerTileY = (WebMercatorMath.latToWorldY(centerLat, tileWorldSize) / 256.0).toInt()

        LaunchedEffect(intZoom, centerTileX, centerTileY, reloadTrigger) {
            val netErr = checkNetworkStatusErrorTh(context)
            if (netErr != null) {
                tileErrorMessageTh = netErr
                return@LaunchedEffect
            }
            for (dy in -2..2) {
                for (dx in -2..2) {
                    val tx = ((centerTileX + dx) % numTiles + numTiles) % numTiles
                    val ty = centerTileY + dy
                    if (ty in 0 until numTiles && OsmTileRepository.getCached(intZoom, tx, ty) == null) {
                        coroutineScope.launch {
                            val res = OsmTileRepository.fetchTile(context, intZoom, tx, ty)
                            res.onSuccess {
                                loadedTileSuccessCount++
                                tileErrorMessageTh = null
                                tileDrawRevision++
                            }.onFailure { err ->
                                if (loadedTileSuccessCount == 0 && err.message != "กำลังโหลดไทล์...") {
                                    tileErrorMessageTh = err.message
                                }
                            }
                        }
                    }
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val rev = tileDrawRevision
            if (rev < 0) return@Canvas

            val canvasW = size.width
            val canvasH = size.height
            val continuousWorldSize = 256.0 * 2.0.pow(zoomLevel)
            val centerWorldX = WebMercatorMath.lngToWorldX(centerLng, continuousWorldSize)
            val centerWorldY = WebMercatorMath.latToWorldY(centerLat, continuousWorldSize)

            // 1. Subtle tactical dark base grid
            val gridStepPx = 48.dp.toPx()
            val gridColor = Color(0xFF191620)
            var gx = 0f
            while (gx < canvasW) {
                drawLine(gridColor, Offset(gx, 0f), Offset(gx, canvasH), strokeWidth = 1f)
                gx += gridStepPx
            }
            var gy = 0f
            while (gy < canvasH) {
                drawLine(gridColor, Offset(0f, gy), Offset(canvasW, gy), strokeWidth = 1f)
                gy += gridStepPx
            }

            // 2. Standard OpenStreetMap dark-filtered tiles
            val scaleFactor = 2.0.pow(zoomLevel - intZoom)
            val scaledTilePx = 256.0 * scaleFactor
            val topLeftWorldX = centerWorldX - canvasW / 2.0
            val topLeftWorldY = centerWorldY - canvasH / 2.0

            val startTileX = floor(topLeftWorldX / scaledTilePx).toInt() - 1
            val endTileX = floor((topLeftWorldX + canvasW) / scaledTilePx).toInt() + 1
            val startTileY = max(0, floor(topLeftWorldY / scaledTilePx).toInt() - 1)
            val endTileY = min(numTiles - 1, floor((topLeftWorldY + canvasH) / scaledTilePx).toInt() + 1)

            for (ty in startTileY..endTileY) {
                for (txRaw in startTileX..endTileX) {
                    val tx = ((txRaw % numTiles) + numTiles) % numTiles
                    val dstLeft = (txRaw * scaledTilePx - topLeftWorldX).roundToInt()
                    val dstTop = (ty * scaledTilePx - topLeftWorldY).roundToInt()
                    val dstRight = ((txRaw + 1) * scaledTilePx - topLeftWorldX).roundToInt()
                    val dstBottom = ((ty + 1) * scaledTilePx - topLeftWorldY).roundToInt()
                    val dstW = (dstRight - dstLeft).coerceAtLeast(1)
                    val dstH = (dstBottom - dstTop).coerceAtLeast(1)

                    val tileBmp = OsmTileRepository.getCached(intZoom, tx, ty)
                    if (tileBmp != null) {
                        drawImage(
                            image = tileBmp,
                            dstOffset = IntOffset(dstLeft, dstTop),
                            dstSize = IntSize(dstW, dstH),
                            colorFilter = OsmDarkThemeColorFilter
                        )
                    } else if (intZoom > 2) {
                        val parentBmp = OsmTileRepository.getCached(intZoom - 1, tx / 2, ty / 2)
                        if (parentBmp != null) {
                            drawImage(
                                image = parentBmp,
                                srcOffset = IntOffset((tx % 2) * 128, (ty % 2) * 128),
                                srcSize = IntSize(128, 128),
                                dstOffset = IntOffset(dstLeft, dstTop),
                                dstSize = IntSize(dstW, dstH),
                                colorFilter = OsmDarkThemeColorFilter
                            )
                        }
                    }
                }
            }

            fun latLngToScreen(lat: Double, lng: Double): Offset {
                val wx = WebMercatorMath.lngToWorldX(lng, continuousWorldSize)
                val wy = WebMercatorMath.latToWorldY(lat, continuousWorldSize)
                return Offset(
                    x = (wx - centerWorldX + canvasW / 2.0).toFloat(),
                    y = (wy - centerWorldY + canvasH / 2.0).toFloat()
                )
            }

            // 3. Historical Hotspot Heatmap Layer (translucent radial clusters from local historical records)
            if (layerFilters.showHeatmap && hotspots.isNotEmpty()) {
                hotspots.forEach { spot ->
                    val pos = latLngToScreen(spot.latitude, spot.longitude)
                    val heatRadiusPx = (46.dp.toPx() + spot.estimatedHeatScore * 52.dp.toPx())
                    if (pos.x in -heatRadiusPx..(canvasW + heatRadiusPx) && pos.y in -heatRadiusPx..(canvasH + heatRadiusPx)) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    CrimsonNeon.copy(alpha = 0.34f * spot.estimatedHeatScore),
                                    AmberWarning.copy(alpha = 0.16f * spot.estimatedHeatScore),
                                    Color.Transparent
                                ),
                                center = pos,
                                radius = heatRadiusPx
                            ),
                            radius = heatRadiusPx,
                            center = pos
                        )
                    }
                }
            }

            // 4. Active FixPoint Zone Radius Circle (0–30 km) + GPS Buffer Ring
            if (layerFilters.showZoneRadius && activePin != null) {
                val pinScreenPos = latLngToScreen(activePin.latitude, activePin.longitude)
                val metersPerPx = WebMercatorMath.metersPerPixel(activePin.latitude, zoomLevel)
                val radiusMeters = (animatedRadiusKm * 1000.0).coerceAtLeast(25.0)
                val radiusPx = (radiusMeters / metersPerPx).toFloat().coerceIn(6f, 8000f)

                val isOutside = zoneStatus == ZoneMonitorStatus.OUTSIDE_ZONE
                val fillAlpha = if (isOutside) 0.28f else 0.18f
                val strokeColor = if (isOutside) Color(0xFFFF1744) else CrimsonNeon

                drawCircle(
                    color = CrimsonRed.copy(alpha = fillAlpha),
                    radius = radiusPx,
                    center = pinScreenPos
                )
                drawCircle(
                    color = strokeColor.copy(alpha = 0.30f),
                    radius = radiusPx,
                    center = pinScreenPos,
                    style = Stroke(width = 6.dp.toPx())
                )
                drawCircle(
                    color = strokeColor,
                    radius = radiusPx,
                    center = pinScreenPos,
                    style = Stroke(width = 2.dp.toPx())
                )
                if (gpsBufferMeters > 0) {
                    val totalBufferPx = ((radiusMeters + gpsBufferMeters) / metersPerPx).toFloat().coerceIn(8f, 8200f)
                    drawCircle(
                        color = AmberWarning.copy(alpha = 0.78f),
                        radius = totalBufferPx,
                        center = pinScreenPos,
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                        )
                    )
                }
            }

            // 5. Fuel Stations from Overpass (optional layer)
            if (layerFilters.showFuelStations && poiPins.isNotEmpty()) {
                poiPins.take(30).forEach { poi ->
                    val pos = latLngToScreen(poi.latitude, poi.longitude)
                    if (pos.x in -40f..(canvasW + 40f) && pos.y in -40f..(canvasH + 40f)) {
                        val badgeColor = if (poi.category == StopPoiCategory.FUEL) AmberWarning else CyanTelemetry
                        drawCircle(color = badgeColor, radius = 8.dp.toPx(), center = pos)
                        drawCircle(color = Color.White, radius = 8.dp.toPx(), center = pos, style = Stroke(1.6.dp.toPx()))
                        drawContext.canvas.nativeCanvas.drawText(
                            if (poi.category == StopPoiCategory.FUEL) "F" else "P",
                            pos.x,
                            pos.y + 3.5.dp.toPx(),
                            badgeTextPaint
                        )
                    }
                }
            }

            // 6. Saved Pins filtered by category (FixPoint, Hotzone, Restaurant, Fuel, Rest)
            val topHotspotId = hotspots.firstOrNull()?.id
            allPins.forEach { pin ->
                val isActive = activePin?.id == pin.id
                val visible = isActive || when (pin.category) {
                    PinCategory.FIXPOINT -> layerFilters.showFixPoints
                    PinCategory.HOTZONE -> layerFilters.showHotzones
                    PinCategory.RESTAURANT -> layerFilters.showRestaurants
                    PinCategory.FUEL -> layerFilters.showFuelStations
                    PinCategory.REST_AREA -> layerFilters.showFixPoints
                }
                if (!visible) return@forEach

                val pos = latLngToScreen(pin.latitude, pin.longitude)
                if (pos.x !in -60f..(canvasW + 60f) || pos.y !in -60f..(canvasH + 60f)) return@forEach

                val markerColor = when {
                    isActive -> CrimsonRed
                    pin.category == PinCategory.HOTZONE -> VioletInsight
                    pin.category == PinCategory.RESTAURANT -> AmberWarning
                    pin.category == PinCategory.FUEL -> CyanTelemetry
                    else -> Color(0xFF475569)
                }
                val symbol = when {
                    isActive -> "★"
                    pin.category == PinCategory.HOTZONE -> "H"
                    pin.category == PinCategory.RESTAURANT -> "R"
                    pin.category == PinCategory.FUEL -> "F"
                    else -> "P"
                }

                // Recommended Next Destination halo
                if (pin.id == topHotspotId) {
                    drawCircle(
                        color = EmeraldSRank.copy(alpha = 0.25f),
                        radius = 18.dp.toPx(),
                        center = pos
                    )
                    drawCircle(
                        color = EmeraldSRank,
                        radius = 15.dp.toPx(),
                        center = pos,
                        style = Stroke(
                            width = 1.6.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                        )
                    )
                }

                val markerRadius = if (isActive) 11.dp.toPx() else 9.dp.toPx()
                if (isActive) {
                    drawCircle(color = CrimsonNeon.copy(alpha = 0.32f), radius = 17.dp.toPx(), center = pos)
                }
                drawCircle(color = markerColor, radius = markerRadius, center = pos)
                drawCircle(color = Color.White, radius = markerRadius, center = pos, style = Stroke(2.dp.toPx()))
                drawContext.canvas.nativeCanvas.drawText(symbol, pos.x, pos.y + 3.5.dp.toPx(), badgeTextPaint)

                // Tooltip pill with name & distance from rider
                if (isActive || pin.id == topHotspotId || zoomLevel >= 12.5) {
                    val distLabel = if (userLatitude != null && userLongitude != null) {
                        val d = GeoMath.haversineMeters(userLatitude, userLongitude, pin.latitude, pin.longitude)
                        " • ${GeoMath.formatShortZoneDistanceTh(d)}"
                    } else ""
                    val labelText = pin.name.take(16) + distLabel
                    val tw = labelPaint.measureText(labelText)
                    val pillW = tw + 16.dp.toPx()
                    val pillH = 20.dp.toPx()
                    val pillTopLeft = Offset(pos.x - pillW / 2f, pos.y - markerRadius - 6.dp.toPx() - pillH)
                    drawRoundRect(
                        color = Color(0xEB0E0A11),
                        topLeft = pillTopLeft,
                        size = Size(pillW, pillH),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                    )
                    drawRoundRect(
                        color = if (isActive) CrimsonNeon else markerColor.copy(alpha = 0.8f),
                        topLeft = pillTopLeft,
                        size = Size(pillW, pillH),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                        style = Stroke(1.dp.toPx())
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        labelText,
                        pos.x,
                        pillTopLeft.y + pillH * 0.72f,
                        if (isActive) labelPaint else subLabelPaint
                    )
                }
            }

            // 7. Rider Live GPS Position & Dashed Link to Active Pin
            if (userLatitude != null && userLongitude != null) {
                val userPos = latLngToScreen(userLatitude, userLongitude)
                val uColor = if (zoneStatus == ZoneMonitorStatus.OUTSIDE_ZONE) CrimsonNeon else EmeraldSRank
                if (activePin != null) {
                    val pinPos = latLngToScreen(activePin.latitude, activePin.longitude)
                    drawLine(
                        color = uColor.copy(alpha = 0.85f),
                        start = pinPos,
                        end = userPos,
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    )
                }
                drawCircle(color = uColor.copy(alpha = 0.25f), radius = 15.dp.toPx(), center = userPos)
                drawCircle(color = uColor, radius = 7.dp.toPx(), center = userPos)
                drawCircle(color = Color.White, radius = 7.dp.toPx(), center = userPos, style = Stroke(2.2.dp.toPx()))
            }
        }

        // Right-side Floating Map Zoom & Recenter Controls
        if (showBuiltInControls) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MapControlCircleButton(
                    label = "+",
                    contentDesc = "ขยายแผนที่",
                    testTag = "btn_map_zoom_in",
                    onClick = { zoomLevel = (zoomLevel + 1.0).coerceIn(3.0, 18.8) }
                )
                MapControlCircleButton(
                    label = "−",
                    contentDesc = "ย่อแผนที่",
                    testTag = "btn_map_zoom_out",
                    onClick = { zoomLevel = (zoomLevel - 1.0).coerceIn(3.0, 18.8) }
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color(0xEB15121B))
                        .border(1.dp, CrimsonNeon.copy(alpha = 0.7f), CircleShape)
                        .clickable { fitMapBoundsToTarget() }
                        .testTag("btn_map_auto_fit")
                ) {
                    Icon(
                        imageVector = Icons.Default.CenterFocusStrong,
                        contentDescription = "จัดกึ่งกลางโซน",
                        tint = CrimsonNeon,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (userLatitude != null && userLongitude != null) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color(0xEB15121B))
                            .border(1.dp, EmeraldSRank.copy(alpha = 0.7f), CircleShape)
                            .clickable {
                                centerLat = userLatitude
                                centerLng = userLongitude
                                zoomLevel = 15.2
                            }
                            .testTag("btn_map_recenter_gps")
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "ไปยังตำแหน่งฉัน",
                            tint = EmeraldSRank,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Offline / Tile Error Pill (compact, non-blocking)
        if (tileErrorMessageTh != null && loadedTileSuccessCount == 0) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 64.dp, start = 14.dp, end = 14.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xEE180D13))
                    .border(1.dp, AmberWarning.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("tile_load_error_banner"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = AmberWarning,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = tileErrorMessageTh!!,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextHudPrimary,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "ลองโหลดแผนที่ใหม่",
                    tint = CrimsonNeon,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable {
                            tileErrorMessageTh = checkNetworkStatusErrorTh(context)
                            reloadTrigger++
                        }
                        .testTag("btn_retry_load_tiles")
                )
            }
        }

        // OpenStreetMap Attribution Badge (Mandatory & Always Visible)
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 10.dp, bottom = 10.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xD90B090E))
                .border(1.dp, HairlineBorder, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("osm_attribution_badge"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "© OpenStreetMap contributors",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = TextHudSecondary
            )
            if (distanceFromPinMeters != null) {
                Text(
                    text = "• ${GeoMath.formatShortZoneDistanceTh(distanceFromPinMeters)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = if (zoneStatus == ZoneMonitorStatus.OUTSIDE_ZONE) CrimsonNeon else EmeraldSRank,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MapControlCircleButton(
    label: String,
    contentDesc: String,
    testTag: String,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Color(0xEB15121B))
            .border(1.dp, CrimsonBorderGlow, CircleShape)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Text(
            text = label,
            color = TextHudPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp
        )
    }
}
