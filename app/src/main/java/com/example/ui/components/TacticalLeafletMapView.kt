package com.example.ui.components

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.model.GeoMath
import com.example.model.HotzonePin
import com.example.model.MerchantPoint
import com.example.model.OrderEvaluation
import com.example.model.OrderGrade
import com.example.ui.theme.CrimsonBorderGlow
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.EmeraldSRank
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextHudMuted
import com.example.ui.theme.TextHudPrimary
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private class LeafletBridge(
    private val onPinClicked: (Double, Double) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onMapClick(lat: Double, lng: Double) {
        mainHandler.post {
            onPinClicked(lat, lng)
        }
    }
}

/**
 * Interactive Leaflet.js + CartoDB Dark Matter Map Component.
 * - Allows clicking/tapping anywhere on the real map to set the Crimson Anchor Pin.
 * - Draws the solid Crimson Red "Pickup Ring" (รัศมีรับงานใกล้หมุด)
 *   and glowing dashed "Drop-off Ring" (รัศมีจุดส่งไม่เกินหมุด) that resize in real time with sliders.
 * - Renders Merchant Cluster points, Golden Centroid, and Incoming Order vectors.
 */
@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
@Composable
fun TacticalLeafletMapView(
    activePin: HotzonePin,
    maxPickupRadiusKm: Float,
    maxDropoffRadiusKm: Float,
    isLocked: Boolean,
    merchants: List<MerchantPoint>,
    goldenCentroidLat: Double,
    goldenCentroidLng: Double,
    orderEvaluation: OrderEvaluation?,
    onMapPinClicked: (Double, Double) -> Unit,
    modifier: Modifier = Modifier,
    mapHeight: Dp = 315.dp,
    testTagName: String = "leaflet_dark_matter_map"
) {
    val stateJsonString = remember(
        activePin.id,
        activePin.lat,
        activePin.lng,
        activePin.zoneTag,
        maxPickupRadiusKm,
        maxDropoffRadiusKm,
        isLocked,
        merchants,
        goldenCentroidLat,
        goldenCentroidLng,
        orderEvaluation
    ) {
        buildMapStateJson(
            activePin = activePin,
            maxPickupRadiusKm = maxPickupRadiusKm,
            maxDropoffRadiusKm = maxDropoffRadiusKm,
            isLocked = isLocked,
            merchants = merchants,
            goldenCentroidLat = goldenCentroidLat,
            goldenCentroidLng = goldenCentroidLng,
            orderEvaluation = orderEvaluation
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(mapHeight)
            .clip(RoundedCornerShape(18.dp))
            .background(ObsidianBlack)
            .border(1.5.dp, CrimsonBorderGlow, RoundedCornerShape(18.dp))
            .testTag(testTagName)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setBackgroundColor(android.graphics.Color.parseColor("#070709"))
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                    // Prevent parent vertical scroll from intercepting map pan/pinch/tap gestures
                    setOnTouchListener { v, event ->
                        when (event.action) {
                            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
                                v.parent?.requestDisallowInterceptTouchEvent(true)
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                                v.parent?.requestDisallowInterceptTouchEvent(false)
                        }
                        false
                    }
                    addJavascriptInterface(LeafletBridge(onMapPinClicked), "AndroidBridge")
                    webChromeClient = WebChromeClient()
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            val currentPayload = (view?.tag as? String) ?: stateJsonString
                            view?.evaluateJavascript(
                                "if(window.updateTacticalMapState){window.updateTacticalMapState($currentPayload);}",
                                null
                            )
                        }
                    }
                    tag = stateJsonString
                    loadDataWithBaseURL(
                        "https://cartodb-basemaps-a.global.ssl.fastly.net/",
                        LEAFLET_DARK_MATTER_HTML,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            },
            update = { webView ->
                webView.tag = stateJsonString
                webView.evaluateJavascript(
                    "if(window.updateTacticalMapState){window.updateTacticalMapState($stateJsonString);}",
                    null
                )
            }
        )

        // Top Tactical Overlay Header on Map
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianBlack.copy(alpha = 0.84f))
                    .border(1.dp, CrimsonBorderGlow, RoundedCornerShape(8.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "CARTODB DARK MATTER • แตะแผนที่เพื่อปักหมุด",
                    style = MaterialTheme.typography.labelSmall,
                    color = CrimsonNeon,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = String.format(
                        Locale.US,
                        "PIN: %.5f°N, %.5f°E (%s)",
                        activePin.lat,
                        activePin.lng,
                        activePin.zoneTag
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = TextHudPrimary
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(ObsidianBlack.copy(alpha = 0.84f))
                    .border(1.dp, EmeraldSRank.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 9.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "รับ ≤ ${GeoMath.formatKm(maxPickupRadiusKm.toDouble())} km",
                    style = MaterialTheme.typography.labelSmall,
                    color = CrimsonNeon,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "ส่ง ≤ ${GeoMath.format1Dec(maxDropoffRadiusKm.toDouble())} km",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldSRank
                )
            }
        }

        // Bottom Map Legend Bar
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(ObsidianBlack.copy(alpha = 0.88f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            MapLegendItem(color = CrimsonRed, text = "วงกลมทึบ: รัศมีรับ (${GeoMath.format1Dec(maxPickupRadiusKm.toDouble())}km)")
            MapLegendItem(color = CrimsonNeon, text = "เส้นประเรืองแสง: รัศมีส่ง (${GeoMath.format1Dec(maxDropoffRadiusKm.toDouble())}km)")
            MapLegendItem(color = EmeraldSRank, text = "แตะปักหมุดได้ทันที")
        }
    }
}

@Composable
private fun MapLegendItem(color: Color, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = TextHudMuted
        )
    }
}

private fun buildMapStateJson(
    activePin: HotzonePin,
    maxPickupRadiusKm: Float,
    maxDropoffRadiusKm: Float,
    isLocked: Boolean,
    merchants: List<MerchantPoint>,
    goldenCentroidLat: Double,
    goldenCentroidLng: Double,
    orderEvaluation: OrderEvaluation?
): String {
    val root = JSONObject()
    root.put("pinId", activePin.id)
    root.put("pinName", activePin.name)
    root.put("zoneTag", activePin.zoneTag)
    root.put("lat", activePin.lat)
    root.put("lng", activePin.lng)
    root.put("pickupMeters", (maxPickupRadiusKm * 1000f).toDouble())
    root.put("dropoffMeters", (maxDropoffRadiusKm * 1000f).toDouble())
    root.put("isLocked", isLocked)
    root.put("centroidLat", goldenCentroidLat)
    root.put("centroidLng", goldenCentroidLng)

    val merchantArray = JSONArray()
    merchants.forEach { m ->
        val obj = JSONObject()
        obj.put("name", m.name)
        obj.put("lat", m.lat)
        obj.put("lng", m.lng)
        merchantArray.put(obj)
    }
    root.put("merchants", merchantArray)

    if (orderEvaluation != null) {
        val ordObj = JSONObject()
        ordObj.put("pickupLat", orderEvaluation.order.pickupLat)
        ordObj.put("pickupLng", orderEvaluation.order.pickupLng)
        ordObj.put("dropoffLat", orderEvaluation.order.dropoffLat)
        ordObj.put("dropoffLng", orderEvaluation.order.dropoffLng)
        ordObj.put("merchantName", orderEvaluation.order.merchantName)
        ordObj.put("dropoffName", orderEvaluation.order.dropoffName)
        val colorHex = when (orderEvaluation.grade) {
            OrderGrade.S_RANK_LOOP -> "#10B981"
            OrderGrade.A_RANK_IN_ZONE -> "#F43F5E"
            OrderGrade.REJECT_OUT_OF_ZONE -> "#F59E0B"
        }
        ordObj.put("color", colorHex)
        root.put("order", ordObj)
    }
    return root.toString()
}

private const val LEAFLET_DARK_MATTER_HTML = """
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
    background: #070709;
    overflow: hidden;
    font-family: monospace;
  }
  #map {
    width: 100%;
    height: 100%;
    background: #070709;
  }
  /* Tactical Dark Grid Fallback if offline */
  .leaflet-container {
    background-color: #070709 !important;
    background-image:
      radial-gradient(circle at center, rgba(225, 29, 72, 0.14) 0%, transparent 70%),
      linear-gradient(rgba(225, 29, 72, 0.08) 1px, transparent 1px),
      linear-gradient(90deg, rgba(225, 29, 72, 0.08) 1px, transparent 1px);
    background-size: 100% 100%, 28px 28px, 28px 28px;
  }
  .leaflet-control-attribution {
    display: none !important;
  }
  .leaflet-control-zoom {
    margin-top: 52px !important;
    border: 1px solid rgba(225, 29, 72, 0.45) !important;
  }
  .leaflet-control-zoom a {
    background-color: rgba(16, 16, 21, 0.92) !important;
    color: #F43F5E !important;
    border-bottom: 1px solid rgba(225, 29, 72, 0.3) !important;
  }
  .crimson-pin-icon {
    position: relative;
    width: 28px;
    height: 28px;
  }
  .crimson-pin-core {
    position: absolute;
    top: 5px;
    left: 5px;
    width: 18px;
    height: 18px;
    background: #E11D48;
    border: 2px solid #FFFFFF;
    transform: rotate(45deg);
    box-shadow: 0 0 14px #E11D48, 0 0 24px #F43F5E;
  }
  .crimson-pin-pulse {
    position: absolute;
    top: -6px;
    left: -6px;
    width: 40px;
    height: 40px;
    border: 2px solid #F43F5E;
    border-radius: 50%;
    animation: pulseRing 1.8s infinite ease-out;
  }
  @keyframes pulseRing {
    0% { transform: scale(0.55); opacity: 0.95; }
    100% { transform: scale(1.45); opacity: 0; }
  }
  /* Glowing effect on SVG paths for Drop-off and Pickup Rings */
  path.leaflet-interactive {
    filter: drop-shadow(0 0 6px rgba(244, 63, 94, 0.55));
  }
</style>
</head>
<body>
<div id="map"></div>
<script>
  var map = null;
  var pinMarker = null;
  var pickupCircle = null;
  var dropoffCircle = null;
  var centroidMarker = null;
  var merchantLayerGroup = null;
  var orderLayerGroup = null;
  var lastPinCoords = null;

  function initMap() {
    if (map || typeof L === 'undefined') return;
    map = L.map('map', {
      center: [13.8768, 100.4113],
      zoom: 14,
      zoomControl: true
    });

    // CartoDB Dark Matter Tiles
    L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
      subdomains: 'abcd',
      maxZoom: 19
    }).addTo(map);

    merchantLayerGroup = L.layerGroup().addTo(map);
    orderLayerGroup = L.layerGroup().addTo(map);

    // Click anywhere on the map to move the Crimson Anchor Pin immediately
    map.on('click', function(e) {
      var clickedLat = e.latlng.lat;
      var clickedLng = e.latlng.lng;
      if (pinMarker) pinMarker.setLatLng([clickedLat, clickedLng]);
      if (pickupCircle) pickupCircle.setLatLng([clickedLat, clickedLng]);
      if (dropoffCircle) dropoffCircle.setLatLng([clickedLat, clickedLng]);
      if (window.AndroidBridge && window.AndroidBridge.onMapClick) {
        window.AndroidBridge.onMapClick(clickedLat, clickedLng);
      }
    });
  }

  window.updateTacticalMapState = function(state) {
    initMap();
    if (!map || !state) return;

    var latlng = [state.lat, state.lng];

    // 1. Outer Drop-off Ring (Dashed Glowing Crimson Neon Circle)
    if (!dropoffCircle) {
      dropoffCircle = L.circle(latlng, {
        radius: state.dropoffMeters,
        color: '#F43F5E',
        weight: 2.5,
        opacity: 0.92,
        fillColor: '#991B1B',
        fillOpacity: 0.08,
        dashArray: '10, 8'
      }).addTo(map);
    } else {
      dropoffCircle.setLatLng(latlng);
      dropoffCircle.setRadius(state.dropoffMeters);
    }

    // 2. Inner Pickup Ring (Solid Crimson Red Circle)
    if (!pickupCircle) {
      pickupCircle = L.circle(latlng, {
        radius: state.pickupMeters,
        color: '#E11D48',
        weight: 2.5,
        opacity: 1.0,
        fillColor: '#E11D48',
        fillOpacity: 0.28
      }).addTo(map);
    } else {
      pickupCircle.setLatLng(latlng);
      pickupCircle.setRadius(state.pickupMeters);
    }

    // 3. Central Crimson Anchor Pin Marker
    var pinIcon = L.divIcon({
      className: 'crimson-pin-icon',
      html: '<div class="crimson-pin-pulse"></div><div class="crimson-pin-core"></div>',
      iconSize: [28, 28],
      iconAnchor: [14, 14]
    });

    if (!pinMarker) {
      pinMarker = L.marker(latlng, { icon: pinIcon }).addTo(map);
    } else {
      pinMarker.setLatLng(latlng);
    }

    // 4. Golden Centroid Marker
    if (state.centroidLat && state.centroidLng) {
      var cLatLng = [state.centroidLat, state.centroidLng];
      if (!centroidMarker) {
        centroidMarker = L.circleMarker(cLatLng, {
          radius: 6,
          color: '#10B981',
          weight: 2,
          fillColor: '#10B981',
          fillOpacity: 0.9
        }).addTo(map).bindTooltip('Golden Centroid', { direction: 'top' });
      } else {
        centroidMarker.setLatLng(cLatLng);
      }
    }

    // 5. Merchant Cluster Nodes
    merchantLayerGroup.clearLayers();
    if (state.merchants && state.merchants.length) {
      for (var i = 0; i < state.merchants.length; i++) {
        var m = state.merchants[i];
        L.circleMarker([m.lat, m.lng], {
          radius: 4.5,
          color: '#06B6D4',
          weight: 1.5,
          fillColor: '#06B6D4',
          fillOpacity: 0.85
        }).addTo(merchantLayerGroup);
      }
    }

    // 6. Active Order Pickup & Drop-off Vector
    orderLayerGroup.clearLayers();
    if (state.order) {
      var o = state.order;
      var pPt = [o.pickupLat, o.pickupLng];
      var dPt = [o.dropoffLat, o.dropoffLng];
      L.polyline([latlng, pPt, dPt], {
        color: o.color || '#10B981',
        weight: 2.5,
        opacity: 0.85
      }).addTo(orderLayerGroup);
      L.polyline([dPt, latlng], {
        color: o.color || '#10B981',
        weight: 1.8,
        dashArray: '6, 6',
        opacity: 0.65
      }).addTo(orderLayerGroup);
      L.circleMarker(pPt, {
        radius: 5,
        color: '#FFFFFF',
        weight: 1.5,
        fillColor: '#10B981',
        fillOpacity: 0.95
      }).addTo(orderLayerGroup);
      L.circleMarker(dPt, {
        radius: 6,
        color: '#FFFFFF',
        weight: 1.5,
        fillColor: o.color || '#F43F5E',
        fillOpacity: 0.95
      }).addTo(orderLayerGroup);
    }

    // Pan smoothly when pin changes significantly
    var coordKey = state.lat.toFixed(4) + ',' + state.lng.toFixed(4);
    if (lastPinCoords !== coordKey) {
      lastPinCoords = coordKey;
      map.panTo(latlng, { animate: true, duration: 0.45 });
    }
  };

  document.addEventListener('DOMContentLoaded', initMap);
</script>
</body>
</html>
"""
