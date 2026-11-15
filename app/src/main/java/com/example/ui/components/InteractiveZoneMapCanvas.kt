package com.example.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.GeoMath
import com.example.model.SavedLocationItem
import com.example.model.ZoneMonitorStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonBorderGlow
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.EmeraldSRank
import com.example.ui.theme.TextHudMuted
import com.example.ui.theme.TextHudPrimary
import com.example.ui.theme.TextHudSecondary
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private class LeafletMapJsBridge(
    private val mainHandler: Handler,
    private val onTapCallback: (Double, Double) -> Unit,
    private val onMapReadyCallback: () -> Unit,
    private val onTileLoadSuccessCallback: () -> Unit,
    private val onTileErrorCallback: (String) -> Unit
) {
    @JavascriptInterface
    fun onMapTapped(lat: Double, lng: Double) {
        if (lat.isNaN() || lng.isNaN()) return
        mainHandler.post {
            onTapCallback(lat.coerceIn(-85.0, 85.0), lng.coerceIn(-180.0, 180.0))
        }
    }

    @JavascriptInterface
    fun onMapReady() {
        mainHandler.post {
            onMapReadyCallback()
        }
    }

    @JavascriptInterface
    fun onTileLoaded() {
        mainHandler.post {
            onTileLoadSuccessCallback()
        }
    }

    @JavascriptInterface
    fun onTileError(reason: String) {
        mainHandler.post {
            onTileErrorCallback(reason)
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

/**
 * Real Leaflet + Standard OpenStreetMap Interactive Map (`https://tile.openstreetmap.org/{z}/{x}/{y}.png`):
 * - Uses keyless OpenStreetMap standard tiles (zero Google Maps code or API keys)
 * - Displays "© OpenStreetMap contributors" attribution
 * - Configures WebView to allow loading images over the internet (`loadsImagesAutomatically = true`, `blockNetworkImage = false`, `blockNetworkLoads = false`)
 * - Displays a clear on-screen error message explaining the cause if map tiles fail to load
 * - Supports tapping on the map to place/move a pin and draws a translucent crimson radius circle around the active pin
 */
@SuppressLint("SetJavaScriptEnabled")
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
    val context = LocalContext.current
    val currentOnMapTapPin by rememberUpdatedState(onMapTapPin)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLeafletReady by remember { mutableStateOf(false) }
    var tileErrorMessageTh by remember {
        mutableStateOf<String?>(checkNetworkStatusErrorTh(context))
    }
    var reloadTrigger by remember { mutableIntStateOf(0) }

    val initialLat = activePin?.latitude ?: userLatitude ?: 13.756331
    val initialLng = activePin?.longitude ?: userLongitude ?: 100.501762

    val leafletHtml = remember( {
        buildLeafletStandardOsmHtml(initialLat = initialLat, initialLng = initialLng)
    } )

    fun pushStateToLeaflet(autoFitRadius: Boolean) {
        val wv = webViewRef ?: return
        if (!isLeafletReady) return

        val pinsJson = JSONArray().apply {
            allPins.forEach { item ->
                put(
                    JSONObject().apply {
                        put("id", item.id)
                        put("name", item.name)
                        put("lat", item.latitude)
                        put("lng", item.longitude)
                    }
                )
            }
        }

        val payload = JSONObject().apply {
            if (activePin != null) {
                put("hasActivePin", true)
                put("pinId", activePin.id)
                put("pinName", activePin.name)
                put("pinLat", activePin.latitude)
                put("pinLng", activePin.longitude)
            } else {
                put("hasActivePin", false)
            }
            if (userLatitude != null && userLongitude != null) {
                put("hasUser", true)
                put("userLat", userLatitude)
                put("userLng", userLongitude)
            } else {
                put("hasUser", false)
            }
            put("radiusMeters", (radiusKm * 1000f).toDouble())
            put("bufferMeters", gpsBufferMeters)
            put("isOutside", zoneStatus == ZoneMonitorStatus.OUTSIDE_ZONE)
            put("autoFit", autoFitRadius)
            put("secondaryPins", pinsJson)
        }

        wv.evaluateJavascript("window.updateFixPointMapState && window.updateFixPointMapState($payload);", null)
    }

    // Push updates when pin, radius, or buffer changes (with auto-fit to radius circle)
    LaunchedEffect(
        isLeafletReady,
        activePin?.id,
        activePin?.latitude,
        activePin?.longitude,
        activePin?.name,
        radiusKm,
        gpsBufferMeters,
        allPins.size
    ) {
        pushStateToLeaflet(autoFitRadius = true)
    }

    // Push live user GPS updates without resetting manual pan/zoom
    LaunchedEffect(
        isLeafletReady,
        userLatitude,
        userLongitude,
        zoneStatus
    ) {
        pushStateToLeaflet(autoFitRadius = false)
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.stopLoading()
            webViewRef?.destroy()
            webViewRef = null
        }
    }

    val containerShape = RoundedCornerShape(4.dp)

    // Note: Do NOT clip WebView with CutCornerShape so hardware-accelerated tiles render brightly on all devices
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(mapHeightDp.dp)
            .background(Color(0xFFECE8DF), containerShape)
            .border(
                width = 1.5.dp,
                color = when (zoneStatus) {
                    ZoneMonitorStatus.INSIDE_ZONE -> EmeraldSRank.copy(alpha = 0.9f)
                    ZoneMonitorStatus.OUTSIDE_ZONE -> CrimsonNeon
                    else -> CrimsonBorderGlow
                },
                shape = containerShape
            )
            .testTag("interactive_zone_map_canvas")
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(1.5.dp),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.parseColor("#ECE8DF"))

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        isForceDarkAllowed = false
                    }

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        loadsImagesAutomatically = true
                        blockNetworkImage = false
                        blockNetworkLoads = false
                        allowContentAccess = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        cacheMode = WebSettings.LOAD_DEFAULT
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(false)
                        builtInZoomControls = false
                        displayZoomControls = false
                        userAgentString =
                            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 CrimsonFixPoint/2.0 (https://www.openstreetmap.org)"
                    }

                    // Prevent parent vertical scroll from intercepting map drags/taps
                    setOnTouchListener { v, event ->
                        when (event.actionMasked) {
                            android.view.MotionEvent.ACTION_DOWN,
                            android.view.MotionEvent.ACTION_MOVE -> {
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            }
                            android.view.MotionEvent.ACTION_UP,
                            android.view.MotionEvent.ACTION_CANCEL -> {
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                        false
                    }

                    addJavascriptInterface(
                        LeafletMapJsBridge(
                            mainHandler = mainHandler,
                            onTapCallback = { lat, lng -> currentOnMapTapPin(lat, lng) },
                            onMapReadyCallback = {
                                isLeafletReady = true
                            },
                            onTileLoadSuccessCallback = {
                                tileErrorMessageTh = null
                            },
                            onTileErrorCallback = { reason ->
                                val networkErr = checkNetworkStatusErrorTh(ctx)
                                tileErrorMessageTh = networkErr ?: reason
                            }
                        ),
                        "AndroidMapBridge"
                    )

                    webViewClient = object : WebViewClient() {
                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            val url = request?.url?.toString().orEmpty()
                            if (url.contains("tile.openstreetmap.org") || url.contains("leaflet")) {
                                val netErr = checkNetworkStatusErrorTh(ctx)
                                val errDesc = error?.description?.toString().orEmpty()
                                val reason = netErr
                                    ?: "โหลดไทล์แผนที่ OpenStreetMap ไม่สำเร็จ: ไม่สามารถเชื่อมต่อเซิร์ฟเวอร์ไทล์ได้ ($errDesc)"
                                mainHandler.post {
                                    tileErrorMessageTh = reason
                                }
                            }
                        }

                        override fun onReceivedHttpError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            errorResponse: WebResourceResponse?
                        ) {
                            super.onReceivedHttpError(view, request, errorResponse)
                            val url = request?.url?.toString().orEmpty()
                            if (url.contains("tile.openstreetmap.org")) {
                                val statusCode = errorResponse?.statusCode ?: 0
                                val reason = when (statusCode) {
                                    403, 429 -> "โหลดไทล์แผนที่ไม่สำเร็จ: เซิร์ฟเวอร์ OpenStreetMap จำกัดการเข้าถึงชั่วคราว (HTTP $statusCode)"
                                    in 500..599 -> "โหลดไทล์แผนที่ไม่สำเร็จ: เซิร์ฟเวอร์ OpenStreetMap ขัดข้องชั่วคราว (HTTP $statusCode)"
                                    else -> "โหลดไทล์แผนที่ OpenStreetMap ไม่สำเร็จ (HTTP $statusCode)"
                                }
                                mainHandler.post {
                                    tileErrorMessageTh = reason
                                }
                            }
                        }
                    }

                    loadDataWithBaseURL(
                        "https://www.openstreetmap.org/",
                        leafletHtml,
                        "text/html",
                        "UTF-8",
                        null
                    )
                    webViewRef = this
                }
            },
            update = { view ->
                webViewRef = view
            }
        )

        // Reload trigger effect
        LaunchedEffect(reloadTrigger) {
            if (reloadTrigger > 0) {
                tileErrorMessageTh = checkNetworkStatusErrorTh(context)
                isLeafletReady = false
                webViewRef?.loadDataWithBaseURL(
                    "https://www.openstreetmap.org/",
                    leafletHtml,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }

        // Top-Left Map HUD Badge: Active Pin & Radius
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

        // Top-Right Controls: Zoom In / Out / Auto-Fit Radius Circle
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
                        webViewRef?.evaluateJavascript("window.zoomInMap && window.zoomInMap();", null)
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
                        webViewRef?.evaluateJavascript("window.zoomOutMap && window.zoomOutMap();", null)
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
                        webViewRef?.evaluateJavascript("window.fitRadiusBounds && window.fitRadiusBounds();", null)
                    }
                    .testTag("btn_map_auto_fit")
            ) {
                Icon(
                    imageVector = Icons.Default.CenterFocusStrong,
                    contentDescription = "ซูมพอดีวงกลม",
                    tint = CrimsonNeon,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        // Error Banner when tiles fail to load (Requirement 5: ถ้าไทล์โหลดไม่ได้ ให้แสดงข้อความแจ้งสาเหตุบนหน้าจอ)
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
                        .clickable { reloadTrigger++ }
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

            // Required OpenStreetMap Attribution Badge ("© OpenStreetMap contributors")
            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(PrecisionButtonShape)
                    .background(Color(0xD909080D))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
                    .testTag("osm_attribution_badge")
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

private fun buildLeafletStandardOsmHtml(initialLat: Double, initialLng: Double): String {
    return """
<!DOCTYPE html>
<html lang="th">
<head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
    <meta name="color-scheme" content="light only" />
    <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css"
          onerror="this.onerror=null;this.href='https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.css';" />
    <style>
        html, body {
            margin: 0;
            padding: 0;
            width: 100%;
            height: 100%;
            background-color: #ECE8DF;
            overflow: hidden;
            -webkit-user-select: none;
            user-select: none;
        }
        #map {
            width: 100%;
            height: 100%;
            background-color: #ECE8DF;
        }
        .leaflet-control-attribution {
            display: none !important;
        }
        .crimson-pin-marker {
            width: 24px;
            height: 24px;
            border-radius: 50% 50% 50% 0;
            background: #E11D48;
            transform: rotate(-45deg);
            border: 2.5px solid #FFFFFF;
            box-shadow: 0 0 12px rgba(244, 63, 94, 0.85);
            position: relative;
        }
        .crimson-pin-marker::after {
            content: '';
            width: 8px;
            height: 8px;
            margin: 5px 0 0 5px;
            background: #FFFFFF;
            position: absolute;
            border-radius: 50%;
        }
        .pin-label-tooltip {
            background: rgba(15, 10, 14, 0.9) !important;
            border: 1px solid #F43F5E !important;
            color: #FFFFFF !important;
            font-weight: bold !important;
            font-size: 11px !important;
            padding: 2px 6px !important;
            border-radius: 4px !important;
            box-shadow: 0 2px 6px rgba(0,0,0,0.4) !important;
        }
        .pin-label-tooltip::before {
            display: none !important;
        }
    </style>
</head>
<body>
    <div id="map"></div>
    <script>
        var map = null;
        var osmTileLayer = null;
        var activePinMarker = null;
        var radiusCircle = null;
        var bufferCircle = null;
        var userMarker = null;
        var userPulseCircle = null;
        var distanceLine = null;
        var secondaryLayerGroup = null;
        var loadedTileCount = 0;
        var errorTileCount = 0;

        function initFixPointLeaflet() {
            if (typeof L === 'undefined') {
                if (window.AndroidMapBridge && window.AndroidMapBridge.onTileError) {
                    window.AndroidMapBridge.onTileError('ไม่สามารถโหลดไลบรารีแผนที่ Leaflet ได้: กรุณาตรวจสอบการเชื่อมต่ออินเทอร์เน็ต');
                }
                return;
            }

            map = L.map('map', {
                zoomControl: false,
                attributionControl: true
            }).setView([$initialLat, $initialLng], 13);

            osmTileLayer = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
                maxZoom: 19,
                attribution: '&copy; OpenStreetMap contributors'
            });

            osmTileLayer.on('tileload', function() {
                loadedTileCount++;
                errorTileCount = 0;
                if (window.AndroidMapBridge && window.AndroidMapBridge.onTileLoaded) {
                    window.AndroidMapBridge.onTileLoaded();
                }
            });

            osmTileLayer.on('tileerror', function(err) {
                errorTileCount++;
                if (loadedTileCount === 0 || errorTileCount >= 2) {
                    var msg = !navigator.onLine
                        ? 'ไม่สามารถโหลดไทล์แผนที่ได้: อุปกรณ์ไม่ได้เชื่อมต่ออินเทอร์เน็ต'
                        : 'โหลดไทล์แผนที่จาก tile.openstreetmap.org ไม่สำเร็จ (เครือข่ายขัดข้องหรือเซิร์ฟเวอร์ไม่ตอบสนอง)';
                    if (window.AndroidMapBridge && window.AndroidMapBridge.onTileError) {
                        window.AndroidMapBridge.onTileError(msg);
                    }
                }
            });

            osmTileLayer.addTo(map);
            secondaryLayerGroup = L.layerGroup().addTo(map);

            map.on('click', function(e) {
                if (window.AndroidMapBridge && window.AndroidMapBridge.onMapTapped) {
                    window.AndroidMapBridge.onMapTapped(e.latlng.lat, e.latlng.lng);
                }
            });

            setTimeout(function() {
                map.invalidateSize();
            }, 120);

            if (window.AndroidMapBridge && window.AndroidMapBridge.onMapReady) {
                window.AndroidMapBridge.onMapReady();
            }
        }

        window.zoomInMap = function() {
            if (map) map.zoomIn();
        };

        window.zoomOutMap = function() {
            if (map) map.zoomOut();
        };

        window.fitRadiusBounds = function() {
            if (!map) return;
            if (radiusCircle) {
                var b = radiusCircle.getBounds();
                if (b && b.isValid()) {
                    map.fitBounds(b, { padding: [26, 26], maxZoom: 17 });
                }
            }
        };

        window.updateFixPointMapState = function(state) {
            if (!map || typeof L === 'undefined') return;
            map.invalidateSize();

            // 1. Secondary saved pins
            if (secondaryLayerGroup) {
                secondaryLayerGroup.clearLayers();
                if (state.secondaryPins && state.secondaryPins.length) {
                    for (var i = 0; i < state.secondaryPins.length; i++) {
                        var sp = state.secondaryPins[i];
                        if (!state.hasActivePin || sp.id !== state.pinId) {
                            L.circleMarker([sp.lat, sp.lng], {
                                radius: 6,
                                color: '#FFFFFF',
                                weight: 2,
                                fillColor: '#3F3F46',
                                fillOpacity: 0.9
                            }).addTo(secondaryLayerGroup);
                        }
                    }
                }
            }

            // 2. Active Pin & Translucent Crimson Radius Circle
            if (state.hasActivePin) {
                var pinLatLng = [state.pinLat, state.pinLng];
                var rMeters = Math.max(25, state.radiusMeters || 0);
                var totalBufferMeters = rMeters + (state.bufferMeters || 0);

                var pinIcon = L.divIcon({
                    className: '',
                    html: '<div class="crimson-pin-marker"></div>',
                    iconSize: [24, 24],
                    iconAnchor: [12, 24]
                });

                if (!activePinMarker) {
                    activePinMarker = L.marker(pinLatLng, { icon: pinIcon }).addTo(map);
                } else {
                    activePinMarker.setLatLng(pinLatLng);
                }

                activePinMarker.unbindTooltip();
                if (state.pinName) {
                    activePinMarker.bindTooltip(state.pinName, {
                        permanent: true,
                        direction: 'top',
                        offset: [0, -26],
                        className: 'pin-label-tooltip'
                    });
                }

                var strokeColor = state.isOutside ? '#FF1744' : '#F43F5E';
                var fillColor = '#E11D48';
                var fillAlpha = state.isOutside ? 0.34 : 0.24;

                if (!radiusCircle) {
                    radiusCircle = L.circle(pinLatLng, {
                        radius: rMeters,
                        color: strokeColor,
                        weight: 2.5,
                        opacity: 0.95,
                        fillColor: fillColor,
                        fillOpacity: fillAlpha
                    }).addTo(map);
                } else {
                    radiusCircle.setLatLng(pinLatLng);
                    radiusCircle.setRadius(rMeters);
                    radiusCircle.setStyle({
                        color: strokeColor,
                        fillColor: fillColor,
                        fillOpacity: fillAlpha
                    });
                }

                if ((state.bufferMeters || 0) > 0) {
                    if (!bufferCircle) {
                        bufferCircle = L.circle(pinLatLng, {
                            radius: totalBufferMeters,
                            color: '#F59E0B',
                            weight: 1.5,
                            dashArray: '6, 6',
                            fillOpacity: 0.0
                        }).addTo(map);
                    } else {
                        bufferCircle.setLatLng(pinLatLng);
                        bufferCircle.setRadius(totalBufferMeters);
                    }
                } else if (bufferCircle) {
                    map.removeLayer(bufferCircle);
                    bufferCircle = null;
                }

                if (state.autoFit && radiusCircle) {
                    var bounds = radiusCircle.getBounds();
                    if (bounds && bounds.isValid()) {
                        map.fitBounds(bounds, { padding: [26, 26], maxZoom: 17 });
                    }
                }
            } else {
                if (activePinMarker) { map.removeLayer(activePinMarker); activePinMarker = null; }
                if (radiusCircle) { map.removeLayer(radiusCircle); radiusCircle = null; }
                if (bufferCircle) { map.removeLayer(bufferCircle); bufferCircle = null; }
            }

            // 3. Real User GPS Position Dot & Line to Active Pin
            if (state.hasUser) {
                var userLatLng = [state.userLat, state.userLng];
                var uColor = state.isOutside ? '#F43F5E' : '#00C853';

                if (!userPulseCircle) {
                    userPulseCircle = L.circleMarker(userLatLng, {
                        radius: 13,
                        color: uColor,
                        weight: 0,
                        fillColor: uColor,
                        fillOpacity: 0.28
                    }).addTo(map);
                } else {
                    userPulseCircle.setLatLng(userLatLng);
                    userPulseCircle.setStyle({ fillColor: uColor, color: uColor });
                }

                if (!userMarker) {
                    userMarker = L.circleMarker(userLatLng, {
                        radius: 6.5,
                        color: '#FFFFFF',
                        weight: 2.5,
                        fillColor: uColor,
                        fillOpacity: 1.0
                    }).addTo(map);
                } else {
                    userMarker.setLatLng(userLatLng);
                    userMarker.setStyle({ fillColor: uColor });
                }

                if (state.hasActivePin) {
                    var linePts = [[state.pinLat, state.pinLng], userLatLng];
                    if (!distanceLine) {
                        distanceLine = L.polyline(linePts, {
                            color: uColor,
                            weight: 2,
                            dashArray: '8, 6',
                            opacity: 0.9
                        }).addTo(map);
                    } else {
                        distanceLine.setLatLngs(linePts);
                        distanceLine.setStyle({ color: uColor });
                    }
                } else if (distanceLine) {
                    map.removeLayer(distanceLine);
                    distanceLine = null;
                }
            } else {
                if (userMarker) { map.removeLayer(userMarker); userMarker = null; }
                if (userPulseCircle) { map.removeLayer(userPulseCircle); userPulseCircle = null; }
                if (distanceLine) { map.removeLayer(distanceLine); distanceLine = null; }
            }
        };

        function loadScript(src, onSuccess, onError) {
            var s = document.createElement('script');
            s.src = src;
            s.onload = onSuccess;
            s.onerror = onError;
            document.body.appendChild(s);
        }

        loadScript(
            'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js',
            initFixPointLeaflet,
            function() {
                loadScript(
                    'https://cdnjs.cloudflare.com/ajax/libs/leaflet/1.9.4/leaflet.min.js',
                    initFixPointLeaflet,
                    function() {
                        if (window.AndroidMapBridge && window.AndroidMapBridge.onTileError) {
                            window.AndroidMapBridge.onTileError('ไม่สามารถโหลดสคริปต์แผนที่ Leaflet ได้ (กรุณาตรวจสอบอินเทอร์เน็ต)');
                        }
                    }
                );
            }
        );
    </script>
</body>
</html>
    """.trimIndent()
}
