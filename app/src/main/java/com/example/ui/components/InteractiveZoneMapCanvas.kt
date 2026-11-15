package com.example.ui.components

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.GeoMath
import com.example.model.SavedLocationItem
import com.example.model.ZoneMonitorStatus
import com.example.ui.theme.CrimsonBorderGlow
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.EmeraldSRank
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextHudMuted
import com.example.ui.theme.TextHudPrimary
import com.example.ui.theme.TextHudSecondary
import java.util.Locale
import kotlin.math.max

private class LeafletBridge(
    private val onTapCallback: (Double, Double) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onMapClicked(lat: Double, lng: Double) {
        if (lat.isNaN() || lng.isNaN()) return
        mainHandler.post {
            onTapCallback(lat.coerceIn(-85.0, 85.0), lng.coerceIn(-180.0, 180.0))
        }
    }
}

/**
 * Real Leaflet + OpenStreetMap Dark Tiles (CARTO Dark Matter, no API key required):
 * - Renders interactive Leaflet map with CARTO Dark tiles (`https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png`)
 * - Tap anywhere on map to place/move pin (`map.on('click', ...)`)
 * - Draws translucent crimson radius circle (`#E11D48` / `#F43F5E`) around the active pin
 * - Automatically zooms to fit the full radius circle (`map.fitBounds(circle.getBounds())`) when radius changes
 * - Shows live user GPS marker and geodesic line to the pin
 * - Displays required OpenStreetMap & CARTO attribution (`© OpenStreetMap contributors © CARTO`)
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
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
    val currentOnMapTap by rememberUpdatedState(onMapTapPin)
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isPageReady by remember { mutableStateOf(false) }

    val buildJsUpdateCommand = remember(
        activePin,
        allPins,
        userLatitude,
        userLongitude,
        radiusKm,
        gpsBufferMeters,
        zoneStatus
    ) {
        val hasPin = activePin != null
        val pinLat = activePin?.latitude ?: (userLatitude ?: 13.756331)
        val pinLng = activePin?.longitude ?: (userLongitude ?: 100.501762)
        val radiusMeters = max(0f, radiusKm * 1000f)
        val bufferMeters = max(0, gpsBufferMeters)
        val hasUser = userLatitude != null && userLongitude != null
        val uLat = userLatitude ?: 0.0
        val uLng = userLongitude ?: 0.0
        val isOutside = zoneStatus == ZoneMonitorStatus.OUTSIDE_ZONE

        val secondaryJson = allPins
            .filter { it.id != activePin?.id }
            .joinToString(prefix = "[", postfix = "]") { item ->
                String.format(Locale.US, "[%.6f,%.6f]", item.latitude, item.longitude)
            }

        String.format(
            Locale.US,
            "window.updateCrimsonMap && window.updateCrimsonMap(%b, %.6f, %.6f, %.1f, %d, %b, %.6f, %.6f, %b, %s);",
            hasPin,
            pinLat,
            pinLng,
            radiusMeters,
            bufferMeters,
            hasUser,
            uLat,
            uLng,
            isOutside,
            secondaryJson
        )
    }

    LaunchedEffect(buildJsUpdateCommand, isPageReady) {
        if (isPageReady) {
            runCatching {
                webViewRef?.evaluateJavascript(buildJsUpdateCommand, null)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                webViewRef?.stopLoading()
                webViewRef?.destroy()
            }
            webViewRef = null
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(mapHeightDp.dp)
            .clip(PrecisionPanelShape)
            .background(ObsidianBlack)
            .border(
                width = 1.5.dp,
                color = when (zoneStatus) {
                    ZoneMonitorStatus.INSIDE_ZONE -> EmeraldSRank.copy(alpha = 0.85f)
                    ZoneMonitorStatus.OUTSIDE_ZONE -> CrimsonNeon
                    else -> CrimsonBorderGlow
                },
                shape = PrecisionPanelShape
            )
            .testTag("interactive_zone_map_canvas")
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.parseColor("#070709"))
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        setSupportZoom(false)
                        builtInZoomControls = false
                        displayZoomControls = false
                    }
                    // Prevent parent verticalScroll from intercepting map pan/pinch/tap gestures
                    setOnTouchListener { v, event ->
                        when (event.actionMasked) {
                            MotionEvent.ACTION_DOWN,
                            MotionEvent.ACTION_MOVE,
                            MotionEvent.ACTION_POINTER_DOWN -> {
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            }
                            MotionEvent.ACTION_UP,
                            MotionEvent.ACTION_CANCEL -> {
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                            }
                        }
                        false
                    }
                    addJavascriptInterface(
                        LeafletBridge { lat, lng -> currentOnMapTap(lat, lng) },
                        "AndroidBridge"
                    )
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            isPageReady = true
                            runCatching {
                                view?.evaluateJavascript(buildJsUpdateCommand, null)
                            }
                        }
                    }
                    loadDataWithBaseURL(
                        "https://crimsonfixpoint.local/",
                        LEAFLET_CARTO_DARK_HTML,
                        "text/html",
                        "UTF-8",
                        null
                    )
                    webViewRef = this
                }
            },
            update = { view ->
                webViewRef = view
                if (isPageReady) {
                    runCatching {
                        view.evaluateJavascript(buildJsUpdateCommand, null)
                    }
                }
            }
        )

        // Top-Left Map HUD Badge: Active Pin & Radius
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

        // Top-Right Zoom / Auto-Fit Controls driving Leaflet directly
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
                        runCatching {
                            webViewRef?.evaluateJavascript("window.zoomInMap && window.zoomInMap();", null)
                        }
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
                        runCatching {
                            webViewRef?.evaluateJavascript("window.zoomOutMap && window.zoomOutMap();", null)
                        }
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
                    .clickable {
                        runCatching {
                            webViewRef?.evaluateJavascript("window.fitRadiusCircle && window.fitRadiusCircle();", null)
                        }
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

            // Required OpenStreetMap & CARTO Attribution Badge
            Box(
                modifier = Modifier
                    .align(Alignment.End)
                    .clip(PrecisionButtonShape)
                    .background(Color(0xCC070709))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
                    .testTag("osm_attribution_badge")
            ) {
                Text(
                    text = "© OpenStreetMap contributors • © CARTO",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                    color = TextHudMuted
                )
            }
        }
    }
}

private const val LEAFLET_CARTO_DARK_HTML = """
<!DOCTYPE html>
<html lang="th">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
<link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
<script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
<style>
  html, body {
    margin: 0;
    padding: 0;
    width: 100%;
    height: 100%;
    background-color: #070709;
    overflow: hidden;
    -webkit-user-select: none;
    user-select: none;
  }
  #map {
    width: 100%;
    height: 100%;
    background-color: #070709;
  }
  .leaflet-container {
    background: #070709 !important;
    font-family: sans-serif;
  }
  .leaflet-control-attribution {
    display: none !important;
  }
  .crimson-pin-icon {
    width: 22px;
    height: 22px;
    border-radius: 50%;
    background: radial-gradient(circle, #FFFFFF 22%, #F43F5E 55%, #E11D48 100%);
    border: 2px solid #F43F5E;
    box-shadow: 0 0 14px rgba(244, 63, 94, 0.95);
  }
  .user-gps-icon-inside {
    width: 16px;
    height: 16px;
    border-radius: 50%;
    background: radial-gradient(circle, #FFFFFF 25%, #00E676 65%, #059669 100%);
    border: 2px solid #FFFFFF;
    box-shadow: 0 0 12px rgba(0, 230, 118, 0.9);
  }
  .user-gps-icon-outside {
    width: 16px;
    height: 16px;
    border-radius: 50%;
    background: radial-gradient(circle, #FFFFFF 25%, #F43F5E 65%, #BE123C 100%);
    border: 2px solid #FFFFFF;
    box-shadow: 0 0 14px rgba(244, 63, 94, 0.95);
  }
</style>
</head>
<body>
<div id="map"></div>
<script>
  var map = L.map('map', {
    zoomControl: false,
    attributionControl: true
  }).setView([13.756331, 100.501762], 13);

  L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
    subdomains: 'abcd',
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors &copy; <a href="https://carto.com/attributions">CARTO</a>'
  }).addTo(map);

  var activePinMarker = null;
  var radiusCircle = null;
  var bufferCircle = null;
  var userMarker = null;
  var linkLine = null;
  var secondaryLayerGroup = L.layerGroup().addTo(map);

  var lastPinLat = null;
  var lastPinLng = null;
  var lastRadiusMeters = null;

  map.on('click', function(e) {
    if (window.AndroidBridge && window.AndroidBridge.onMapClicked) {
      window.AndroidBridge.onMapClicked(e.latlng.lat, e.latlng.lng);
    }
  });

  window.zoomInMap = function() {
    map.zoomIn(1, { animate: true });
  };

  window.zoomOutMap = function() {
    map.zoomOut(1, { animate: true });
  };

  window.fitRadiusCircle = function() {
    if (radiusCircle && radiusCircle.getRadius() > 0) {
      map.fitBounds(radiusCircle.getBounds(), { padding: [26, 26], animate: true, duration: 0.35 });
    } else if (activePinMarker) {
      map.setView(activePinMarker.getLatLng(), 15, { animate: true });
    }
  };

  window.updateCrimsonMap = function(
    hasPin, pinLat, pinLng, radiusMeters, bufferMeters,
    hasUser, userLat, userLng, isOutside, secondaryCoords
  ) {
    secondaryLayerGroup.clearLayers();
    if (secondaryCoords && secondaryCoords.length) {
      for (var i = 0; i < secondaryCoords.length; i++) {
        var c = secondaryCoords[i];
        L.circleMarker([c[0], c[1]], {
          radius: 5,
          color: '#A1A1AA',
          weight: 1.5,
          fillColor: '#3F3F46',
          fillOpacity: 0.85
        }).addTo(secondaryLayerGroup);
      }
    }

    if (hasPin) {
      var pinLatLng = [pinLat, pinLng];
      var pinIcon = L.divIcon({
        className: 'crimson-pin-icon',
        iconSize: [22, 22],
        iconAnchor: [11, 11]
      });

      if (!activePinMarker) {
        activePinMarker = L.marker(pinLatLng, { icon: pinIcon }).addTo(map);
      } else {
        activePinMarker.setLatLng(pinLatLng);
      }

      var effectiveRadius = Math.max(radiusMeters, 15);
      if (!radiusCircle) {
        radiusCircle = L.circle(pinLatLng, {
          radius: effectiveRadius,
          color: '#F43F5E',
          weight: 2.5,
          opacity: 0.95,
          fillColor: '#E11D48',
          fillOpacity: 0.24
        }).addTo(map);
      } else {
        radiusCircle.setLatLng(pinLatLng);
        radiusCircle.setRadius(effectiveRadius);
      }

      if (bufferMeters > 0) {
        var totalBufRadius = effectiveRadius + bufferMeters;
        if (!bufferCircle) {
          bufferCircle = L.circle(pinLatLng, {
            radius: totalBufRadius,
            color: '#F59E0B',
            weight: 1.3,
            dashArray: '6, 6',
            opacity: 0.75,
            fillOpacity: 0.0
          }).addTo(map);
        } else {
          bufferCircle.setLatLng(pinLatLng);
          bufferCircle.setRadius(totalBufRadius);
        }
      } else if (bufferCircle) {
        map.removeLayer(bufferCircle);
        bufferCircle = null;
      }

      var pinChanged = (lastPinLat !== pinLat || lastPinLng !== pinLng);
      var radiusChanged = (lastRadiusMeters !== radiusMeters);
      if (pinChanged || radiusChanged) {
        lastPinLat = pinLat;
        lastPinLng = pinLng;
        lastRadiusMeters = radiusMeters;
        if (radiusMeters > 0) {
          map.fitBounds(radiusCircle.getBounds(), { padding: [26, 26], animate: true, duration: 0.35 });
        } else {
          map.setView(pinLatLng, 15, { animate: true });
        }
      }
    } else {
      if (activePinMarker) { map.removeLayer(activePinMarker); activePinMarker = null; }
      if (radiusCircle) { map.removeLayer(radiusCircle); radiusCircle = null; }
      if (bufferCircle) { map.removeLayer(bufferCircle); bufferCircle = null; }
      lastPinLat = null;
      lastPinLng = null;
      lastRadiusMeters = null;
    }

    if (hasUser) {
      var uLatLng = [userLat, userLng];
      var uIcon = L.divIcon({
        className: isOutside ? 'user-gps-icon-outside' : 'user-gps-icon-inside',
        iconSize: [16, 16],
        iconAnchor: [8, 8]
      });

      if (!userMarker) {
        userMarker = L.marker(uLatLng, { icon: uIcon }).addTo(map);
      } else {
        userMarker.setLatLng(uLatLng);
        userMarker.setIcon(uIcon);
      }

      if (hasPin) {
        var lineColor = isOutside ? '#F43F5E' : '#00E676';
        var pts = [[pinLat, pinLng], uLatLng];
        if (!linkLine) {
          linkLine = L.polyline(pts, {
            color: lineColor,
            weight: 2,
            dashArray: '8, 6',
            opacity: 0.85
          }).addTo(map);
        } else {
          linkLine.setLatLngs(pts);
          linkLine.setStyle({ color: lineColor });
        }
      } else if (linkLine) {
        map.removeLayer(linkLine);
        linkLine = null;
      }

      if (!hasPin && lastPinLat === null) {
        map.setView(uLatLng, 14, { animate: true });
        lastPinLat = userLat;
      }
    } else {
      if (userMarker) { map.removeLayer(userMarker); userMarker = null; }
      if (linkLine) { map.removeLayer(linkLine); linkLine = null; }
    }
  };
</script>
</body>
</html>
"""
