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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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
import com.example.model.DeliveryStopItem
import com.example.model.GeoMath
import com.example.model.SavedLocationItem
import com.example.model.StopPoiCategory
import com.example.model.StopPoiItem
import com.example.model.ZoneMonitorStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonBorderGlow
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.EmeraldSRank
import com.example.ui.theme.TextHudMuted
import com.example.ui.theme.TextHudPrimary
import com.example.ui.theme.TextHudSecondary
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
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sinh
import kotlin.math.tan

/**
 * Memory + Disk Cache for standard OpenStreetMap tiles (`https://tile.openstreetmap.org/{z}/{x}/{y}.png`).
 * Completely eliminates `android.webkit.WebView` so Chromium renderer crashes (`aw_browser_terminator.cc`)
 * can never happen, while rendering real OpenStreetMap tiles with dark-mode styling.
 */
private object OsmTileRepository {
    private val memoryCache = object : LruCache<String, ImageBitmap>(180) {}
    private val inFlightKeys: MutableSet<String> = Collections.synchronizedSet(HashSet())
    private val downloadSemaphore = Semaphore(6)

    fun getCached(z: Int, x: Int, y: Int): ImageBitmap? {
        return memoryCache.get("$z/$x/$y")
    }

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
                        "CrimsonFixPoint/1.0.1 (Android; https://www.openstreetmap.org)"
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
                            Result.failure( IllegalStateException("ไม่สามารถถอดรหัสภาพแผนที่ OpenStreetMap ได้"))
                        }
                    } else {
                        val reason = when (code) {
                            403, 429 -> "โหลดไทล์แผนที่ไม่สำเร็จ: เซิร์ฟเวอร์ OpenStreetMap จำกัดการเข้าถึงชั่วคราว (HTTP $code)"
                            in 500..599 -> "โหลดไทล์แผนที่ไม่สำเร็จ: เซิร์ฟเวอร์ OpenStreetMap ขัดข้องชั่วคราว (HTTP $code)"
                            else -> "โหลดไทล์แผนที่ OpenStreetMap ไม่สำเร็จ (HTTP $code)"
                        }
                        Result.failure(IllegalStateException(reason))
                    }
                } finally {
                    conn.disconnect()
                }
            }
        } catch (e: Exception) {
            val netErr = checkNetworkStatusErrorTh(context)
            val msg = netErr
                ?: "โหลดไทล์แผนที่จาก tile.openstreetmap.org ไม่สำเร็จ (${e.localizedMessage ?: "เครือข่ายขัดข้อง"})"
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
            ?: return@runCatching "ไม่สามารถโหลดไทล์แผนที่ได้: อุปกรณ์ไม่ได้เชื่อมต่ออินเทอร์เน็ต"
        val caps = cm.getNetworkCapabilities(activeNetwork)
            ?: return@runCatching "ไม่สามารถโหลดไทล์แผนที่ได้: ไม่พบความสามารถเครือข่ายที่ใช้งานได้"
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            "ไม่สามารถโหลดไทล์แผนที่ได้: เครือข่ายปัจจุบันไม่มีสิทธิ์เข้าถึงอินเทอร์เน็ต"
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

    fun worldXToLng(worldX: Double, worldSizePx: Double): Double {
        val rawLng = (worldX / worldSizePx) * 360.0 - 180.0
        return rawLng.coerceIn(-180.0, 180.0)
    }

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
        // Fit diameter with 28% margin around the circle
        val targetMetersPerPx = (effectiveMeters * 2.56) / viewportPx
        val cosLat = cos(Math.toRadians(lat.coerceIn(-80.0, 80.0))).coerceAtLeast(0.05)
        val idealZoom = ln((156543.03392 * cosLat) / targetMetersPerPx) / ln(2.0)
        return idealZoom.coerceIn(7.5, 16.8)
    }
}

/**
 * Dark-theme color filter applied directly to standard OpenStreetMap tiles
 * (`https://tile.openstreetmap.org/{z}/{x}/{y}.png`) so streets, labels, and water
 * blend into the Crimson-Obsidian theme while remaining clear and legible.
 */
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
 * Native OpenStreetMap Interactive Map (`https://tile.openstreetmap.org/{z}/{x}/{y}.png`):
 * - Uses keyless OpenStreetMap standard tiles styled in dark theme
 * - Zero `android.webkit.WebView` usage (prevents Chromium renderer process crashes)
 * - Displays "© OpenStreetMap contributors" attribution
 * - Displays a clear on-screen error message explaining the cause if map tiles fail to load
 * - Supports tapping on the map to place/move a pin, smooth pan/zoom, translucent red radius circle,
 *   fuel/rest POI pins, and numbered delivery route stops + red route line.
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
    mapHeightDp: Int = 270,
    poiPins: List<StopPoiItem> = emptyList(),
    deliveryStops: List<DeliveryStopItem> = emptyList(),
    isRouteMapMode: Boolean = false,
    testTagName: String = "interactive_zone_map_canvas"
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val currentOnMapTapPin by rememberUpdatedState(onMapTapPin)

    val initialLat = activePin?.latitude
        ?: deliveryStops.firstOrNull()?.latitude
        ?: userLatitude
        ?: 13.756331
    val initialLng = activePin?.longitude
        ?: deliveryStops.firstOrNull()?.longitude
        ?: userLongitude
        ?: 100.501762

    var centerLat by remember { mutableDoubleStateOf(initialLat) }
    var centerLng by remember { mutableDoubleStateOf(initialLng) }
    var zoomLevel by remember { mutableDoubleStateOf(13.0) }
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
        if (!isRouteMapMode && activePin != null) {
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

        val pts = mutableListOf<Pair<Double, Double>>()
        if (isRouteMapMode) {
            if (userLatitude != null && userLongitude != null) {
                pts.add(userLatitude to userLongitude)
            }
            deliveryStops.forEach { pts.add(it.latitude to it.longitude) }
        } else if (userLatitude != null && userLongitude != null) {
            pts.add(userLatitude to userLongitude)
        }

        if (pts.isEmpty()) return
        if (pts.size == 1) {
            centerLat = pts[0].first
            centerLng = pts[0].second
            zoomLevel = 14.5
            return
        }

        val minLat = pts.minOf { it.first }
        val maxLat = pts.maxOf { it.first }
        val minLng = pts.minOf { it.second }
        val maxLng = pts.maxOf { it.second }
        val midLat = (minLat + maxLat) / 2.0
        val midLng = (minLng + maxLng) / 2.0
        val spanMeters = GeoMath.haversineMeters(minLat, minLng, maxLat, maxLng).coerceAtLeast(300.0)
        centerLat = midLat
        centerLng = midLng
        zoomLevel = WebMercatorMath.computeIdealZoomForRadius(
            lat = midLat,
            radiusMeters = spanMeters * 0.65,
            minViewportPx = minDim
        )
    }

    // Auto-fit when active pin, radius, or route stops change
    LaunchedEffect(
        activePin?.id,
        activePin?.latitude,
        activePin?.longitude,
        radiusKm,
        gpsBufferMeters,
        deliveryStops.size,
        isRouteMapMode
    ) {
        fitMapBoundsToTarget()
    }

    // Native label paints for markers
    val labelPaint = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = with(density) { 10.5.sp.toPx() }
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }
    val badgeTextPaint = remember(density) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = with(density) { 11.sp.toPx() }
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
    }

    val containerShape = RoundedCornerShape(4.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(mapHeightDp.dp)
            .background(Color(0xFF141318), containerShape)
            .border(
                width = 1.5.dp,
                color = when {
                    isRouteMapMode -> CrimsonNeon
                    zoneStatus == ZoneMonitorStatus.INSIDE_ZONE -> EmeraldSRank.copy(alpha = 0.9f)
                    zoneStatus == ZoneMonitorStatus.OUTSIDE_ZONE -> CrimsonNeon
                    else -> CrimsonBorderGlow
                },
                shape = containerShape
            )
            .clip(containerShape)
            .onSizeChanged { size ->
                if (size.width > 0 && size.height > 0) {
                    val wasDefault = viewportSize.width == 800 && viewportSize.height == 600
                    viewportSize = size
                    if (wasDefault) {
                        fitMapBoundsToTarget()
                    }
                }
            }
            .pointerInput(isRouteMapMode) {
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

                                // Keep centroid anchored during pinch-zoom
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

                                val newCenterWorldX =
                                    WebMercatorMath.lngToWorldX(focusLng, newWorldSize) - offsetFromCenterX - panChange.x
                                val newCenterWorldY =
                                    WebMercatorMath.latToWorldY(focusLat, newWorldSize) - offsetFromCenterY - panChange.y

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

                    // Single tap (not a pan or pinch) -> Place or move pin at tapped coordinates
                    if (!wasMultiTouch && totalPanDistance <= touchSlopPx) {
                        val worldSize = 256.0 * 2.0.pow(zoomLevel)
                        val centerWorldX = WebMercatorMath.lngToWorldX(centerLng, worldSize)
                        val centerWorldY = WebMercatorMath.latToWorldY(centerLat, worldSize)
                        val tappedWorldX = centerWorldX + (downPos.x - size.width / 2f)
                        val tappedWorldY = centerWorldY + (downPos.y - size.height / 2f)
                        val tappedLat = WebMercatorMath.worldYToLat(tappedWorldY, worldSize)
                        val tappedLng = WebMercatorMath.worldXToLng(tappedWorldX, worldSize)
                        currentOnMapTapPin(tappedLat, tappedLng)
                    }
                }
            }
            .testTag(testTagName)
    ) {
        // Trigger background tile downloads for visible OpenStreetMap tiles
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

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(1.5.dp)
        ) {
            // Read revision so Canvas redraws as tiles arrive
            val rev = tileDrawRevision
            if (rev < 0) return@Canvas

            val canvasW = size.width
            val canvasH = size.height
            val continuousWorldSize = 256.0 * 2.0.pow(zoomLevel)
            val centerWorldX = WebMercatorMath.lngToWorldX(centerLng, continuousWorldSize)
            val centerWorldY = WebMercatorMath.latToWorldY(centerLat, continuousWorldSize)

            // 1. Draw subtle dark base grid while tiles stream in
            val gridStepPx = 48.dp.toPx()
            val gridColor = Color(0xFF1F1B24)
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

            // 2. Draw standard OpenStreetMap tiles (https://tile.openstreetmap.org/{z}/{x}/{y}.png)
            // with dark-mode ColorMatrix filter
            val scaleFactor = 2.0.pow(zoomLevel - intZoom)
            val scaledTilePx = (256.0 * scaleFactor)
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
                        // Fallback to parent zoom tile slice while current zoom loads
                        val parentBmp = OsmTileRepository.getCached(intZoom - 1, tx / 2, ty / 2)
                        if (parentBmp != null) {
                            val srcX = (tx % 2) * 128
                            val srcY = (ty % 2) * 128
                            drawImage(
                                image = parentBmp,
                                srcOffset = IntOffset(srcX, srcY),
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

            // 3. Draw Secondary Saved Pins (when not in route mode)
            if (!isRouteMapMode) {
                allPins.forEach { sp ->
                    if (activePin == null || sp.id != activePin.id) {
                        val pos = latLngToScreen(sp.latitude, sp.longitude)
                        if (pos.x in -40f..(canvasW + 40f) && pos.y in -40f..(canvasH + 40f)) {
                            drawCircle(
                                color = Color(0xFF3F3F46),
                                radius = 6.dp.toPx(),
                                center = pos
                            )
                            drawCircle(
                                color = Color.White,
                                radius = 6.dp.toPx(),
                                center = pos,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }
            }

            // 4. Draw Fuel Stations & Rest Stop POI Markers
            if (!isRouteMapMode && poiPins.isNotEmpty()) {
                poiPins.take(40).forEach { poi ->
                    val pos = latLngToScreen(poi.latitude, poi.longitude)
                    if (pos.x in -50f..(canvasW + 50f) && pos.y in -50f..(canvasH + 50f)) {
                        val badgeColor = if (poi.category == StopPoiCategory.FUEL) {
                            AmberWarning
                        } else {
                            Color(0xFF38BDF8)
                        }
                        drawCircle(
                            color = badgeColor,
                            radius = 9.dp.toPx(),
                            center = pos
                        )
                        drawCircle(
                            color = Color.White,
                            radius = 9.dp.toPx(),
                            center = pos,
                            style = Stroke(width = 1.8.dp.toPx())
                        )
                        val symbol = if (poi.category == StopPoiCategory.FUEL) "F" else "P"
                        drawContext.canvas.nativeCanvas.drawText(
                            symbol,
                            pos.x,
                            pos.y + 3.5.dp.toPx(),
                            badgeTextPaint
                        )
                    }
                }
            }

            // 5. Draw Delivery Route Polyline & Numbered Stop Badges (1..15)
            if (deliveryStops.isNotEmpty()) {
                val routePoints = mutableListOf<Offset>()
                if (userLatitude != null && userLongitude != null) {
                    routePoints.add(latLngToScreen(userLatitude, userLongitude))
                }
                deliveryStops.forEach { stop ->
                    if (!stop.isDelivered) {
                        routePoints.add(latLngToScreen(stop.latitude, stop.longitude))
                    }
                }
                if (routePoints.size >= 2) {
                    val routePath = Path().apply {
                        moveTo(routePoints[0].x, routePoints[0].y)
                        for (i in 1 until routePoints.size) {
                            lineTo(routePoints[i].x, routePoints[i].y)
                        }
                    }
                    drawPath(
                        path = routePath,
                        color = CrimsonRed.copy(alpha = 0.35f),
                        style = Stroke(
                            width = 8.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                    drawPath(
                        path = routePath,
                        color = CrimsonNeon,
                        style = Stroke(
                            width = 3.2.dp.toPx(),
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                deliveryStops.forEachIndexed { idx, stop ->
                    val pos = latLngToScreen(stop.latitude, stop.longitude)
                    if (pos.x in -50f..(canvasW + 50f) && pos.y in -50f..(canvasH + 50f)) {
                        val badgeRadius = 12.dp.toPx()
                        val fillColor = if (stop.isDelivered) EmeraldSRank else CrimsonRed
                        drawCircle(
                            color = fillColor.copy(alpha = 0.35f),
                            radius = badgeRadius + 4.dp.toPx(),
                            center = pos
                        )
                        drawCircle(
                            color = fillColor,
                            radius = badgeRadius,
                            center = pos
                        )
                        drawCircle(
                            color = Color.White,
                            radius = badgeRadius,
                            center = pos,
                            style = Stroke(width = 2.dp.toPx())
                        )
                        val label = if (stop.isDelivered) {
                            "✓"
                        } else {
                            (if (stop.sequenceOrder > 0) stop.sequenceOrder else idx + 1).toString()
                        }
                        drawContext.canvas.nativeCanvas.drawText(
                            label,
                            pos.x,
                            pos.y + 4.dp.toPx(),
                            badgeTextPaint
                        )
                    }
                }
            }

            // 6. Draw Active Pin & Translucent Crimson Radius Circle (0-30 km)
            if (activePin != null && !isRouteMapMode) {
                val pinScreenPos = latLngToScreen(activePin.latitude, activePin.longitude)
                val metersPerPx = WebMercatorMath.metersPerPixel(activePin.latitude, zoomLevel)
                val radiusMeters = (animatedRadiusKm * 1000.0).coerceAtLeast(25.0)
                val radiusPx = (radiusMeters / metersPerPx).toFloat().coerceIn(6f, 8000f)

                val isOutside = zoneStatus == ZoneMonitorStatus.OUTSIDE_ZONE
                val fillAlpha = if (isOutside) 0.32f else 0.22f
                val strokeColor = if (isOutside) Color(0xFFFF1744) else CrimsonNeon

                // Translucent red radius circle
                drawCircle(
                    color = CrimsonRed.copy(alpha = fillAlpha),
                    radius = radiusPx,
                    center = pinScreenPos
                )
                // Glowing outer border of radius circle
                drawCircle(
                    color = strokeColor.copy(alpha = 0.35f),
                    radius = radiusPx,
                    center = pinScreenPos,
                    style = Stroke(width = 6.dp.toPx())
                )
                drawCircle(
                    color = strokeColor,
                    radius = radiusPx,
                    center = pinScreenPos,
                    style = Stroke(width = 2.2.dp.toPx())
                )

                // GPS Buffer ring (dashed amber)
                if (gpsBufferMeters > 0) {
                    val totalBufferPx =
                        ((radiusMeters + gpsBufferMeters) / metersPerPx).toFloat().coerceIn(8f, 8200f)
                    drawCircle(
                        color = AmberWarning.copy(alpha = 0.85f),
                        radius = totalBufferPx,
                        center = pinScreenPos,
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f), 0f)
                        )
                    )
                }

                // Active Pin Marker
                drawCircle(
                    color = CrimsonNeon.copy(alpha = 0.35f),
                    radius = 15.dp.toPx(),
                    center = pinScreenPos
                )
                drawCircle(
                    color = CrimsonRed,
                    radius = 9.dp.toPx(),
                    center = pinScreenPos
                )
                drawCircle(
                    color = Color.White,
                    radius = 9.dp.toPx(),
                    center = pinScreenPos,
                    style = Stroke(width = 2.5.dp.toPx())
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.dp.toPx(),
                    center = pinScreenPos
                )

                // Pin Name Tooltip Pill above pin
                if (activePin.name.isNotBlank()) {
                    val textWidth = labelPaint.measureText(activePin.name)
                    val pillW = textWidth + 16.dp.toPx()
                    val pillH = 20.dp.toPx()
                    val pillTopLeft = Offset(
                        x = pinScreenPos.x - pillW / 2f,
                        y = pinScreenPos.y - 16.dp.toPx() - pillH
                    )
                    drawRoundRect(
                        color = Color(0xEB0F0A0E),
                        topLeft = pillTopLeft,
                        size = Size(pillW, pillH),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                    drawRoundRect(
                        color = CrimsonNeon,
                        topLeft = pillTopLeft,
                        size = Size(pillW, pillH),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        style = Stroke(width = 1.dp.toPx())
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        activePin.name,
                        pinScreenPos.x,
                        pillTopLeft.y + pillH * 0.72f,
                        labelPaint
                    )
                }
            }

            // 7. Draw Real User GPS Position & Dashed Line to Active Pin
            if (userLatitude != null && userLongitude != null) {
                val userPos = latLngToScreen(userLatitude, userLongitude)
                val uColor = if (zoneStatus == ZoneMonitorStatus.OUTSIDE_ZONE) CrimsonNeon else EmeraldSRank

                if (activePin != null && !isRouteMapMode) {
                    val pinPos = latLngToScreen(activePin.latitude, activePin.longitude)
                    drawLine(
                        color = uColor.copy(alpha = 0.88f),
                        start = pinPos,
                        end = userPos,
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f)
                    )
                }

                drawCircle(
                    color = uColor.copy(alpha = 0.28f),
                    radius = 13.dp.toPx(),
                    center = userPos
                )
                drawCircle(
                    color = uColor,
                    radius = 6.5.dp.toPx(),
                    center = userPos
                )
                drawCircle(
                    color = Color.White,
                    radius = 6.5.dp.toPx(),
                    center = userPos,
                    style = Stroke(width = 2.2.dp.toPx())
                )
            }
        }

        // Top-Left Map HUD Badge
        if (!isRouteMapMode) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(PrecisionButtonShape)
                    .background(Color(0xE60D0A0F))
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
                        text = activePin?.name ?: "ไม่มีหมุดโซน",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                        color = TextHudPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = String.format(
                        Locale.US,
                        "รัศมี %.1f กม. • เผื่อ %d ม.",
                        radiusKm,
                        gpsBufferMeters
                    ),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = CrimsonNeon
                )
            }
        } else {
            val pendingCount = deliveryStops.count { !it.isDelivered }
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp)
                    .clip(PrecisionButtonShape)
                    .background(Color(0xE60D0A0F))
                    .border(1.dp, CrimsonBorderGlow, PrecisionButtonShape)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "จุดส่งคงเหลือ $pendingCount/${deliveryStops.size} จุด",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                    color = TextHudPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "แตะแผนที่เพื่อเพิ่มจุดส่ง",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                    color = CrimsonNeon
                )
            }
        }

        // Top-Right Controls: Zoom In / Out / Auto-Fit
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
                    .background(Color(0xE6120D11))
                    .border(1.dp, CrimsonBorderGlow, PrecisionButtonShape)
                    .clickable {
                        zoomLevel = (zoomLevel + 1.0).coerceIn(3.0, 18.8)
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
                    .background(Color(0xE6120D11))
                    .border(1.dp, CrimsonBorderGlow, PrecisionButtonShape)
                    .clickable {
                        zoomLevel = (zoomLevel - 1.0).coerceIn(3.0, 18.8)
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
                    .background(Color(0xE6120D11))
                    .border(1.dp, CrimsonNeon, PrecisionButtonShape)
                    .clickable {
                        fitMapBoundsToTarget()
                    }
                    .testTag("btn_map_auto_fit")
            ) {
                Icon(
                    imageVector = Icons.Default.CenterFocusStrong,
                    contentDescription = "ซูมพอดีขอบเขต",
                    tint = CrimsonNeon,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Error Banner when tiles fail to load
        if (tileErrorMessageTh != null) {
            Row(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 14.dp)
                    .fillMaxWidth()
                    .clip(PrecisionButtonShape)
                    .background(Color(0xF21A090D))
                    .border(1.5.dp, CrimsonNeon, PrecisionButtonShape)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag("tile_load_error_banner"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = null,
                        tint = AmberWarning,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = tileErrorMessageTh!!,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
                        color = TextHudPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(PrecisionButtonShape)
                        .background(CrimsonRed)
                        .clickable {
                            tileErrorMessageTh = checkNetworkStatusErrorTh(context)
                            reloadTrigger++
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("btn_retry_load_tiles")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "ลองโหลดแผนที่ใหม่",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "ลองใหม่",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Bottom Map Overlay: Live User GPS Position, Distance from Pin & OpenStreetMap Attribution
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!isRouteMapMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(PrecisionButtonShape)
                        .background(Color(0xE609080D))
                        .border(1.dp, CrimsonBorderGlow, PrecisionButtonShape)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
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
                                String.format(Locale.US, "%.5f, %.5f", userLatitude, userLongitude)
                            } else {
                                "พิกัดผู้ใช้: —"
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

            // Required OpenStreetMap Attribution Badge ("© OpenStreetMap contributors")
            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(PrecisionButtonShape)
                    .background(Color(0xD909080D))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
                    .testTag(if (isRouteMapMode) "route_osm_attribution_badge" else "osm_attribution_badge")
            ) {
                Text(
                    text = "© OpenStreetMap contributors",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    color = TextHudSecondary
                )
            }
        }
    }
}
