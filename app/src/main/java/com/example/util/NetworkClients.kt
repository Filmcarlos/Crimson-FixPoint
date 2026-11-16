package com.example.util

import com.example.model.StopPoiCategory
import com.example.model.StopPoiItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.max
import kotlin.math.roundToInt

sealed class OverpassFuelResult {
    data class Success(
        val stations: List<StopPoiItem>,
        val endpointUsed: String
    ) : OverpassFuelResult()

    data class Failure(
        val reasonTh: String
    ) : OverpassFuelResult()
}

data class OpenMeteoParsedForecast(
    val willRainSoon: Boolean,
    val minutesUntilRain: Int?,
    val probabilityPercent: Int,
    val precipitationMm: Double,
    val fetchedAtMillis: Long
)

sealed class OpenMeteoResult {
    data class Success(val forecast: OpenMeteoParsedForecast) : OpenMeteoResult()
    data class Failure(val reasonTh: String) : OpenMeteoResult()
}

object NetworkClients {
    private const val APP_USER_AGENT = "CrimsonFixPoint/1.0.1 (Android; OpenStreetMap)"

    private val OVERPASS_ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter"
    )

    /**
     * Queries Overpass API for amenity=fuel (node/way, out center;) around (latitude, longitude).
     * Automatically falls back from overpass-api.de to overpass.kumi.systems if the primary server fails.
     * Never returns mock/fake data.
     */
    suspend fun fetchNearbyFuelStationsOverpass(
        latitude: Double,
        longitude: Double,
        radiusKm: Int
    ): OverpassFuelResult = withContext(Dispatchers.IO) {
        val radiusMeters = (radiusKm.coerceIn(1, 20) * 1000)
        val latStr = String.format(Locale.US, "%.6f", latitude)
        val lonStr = String.format(Locale.US, "%.6f", longitude)

        val overpassQl = """
            [out:json][timeout:18];
            (
              node["amenity"="fuel"](around:$radiusMeters,$latStr,$lonStr);
              way["amenity"="fuel"](around:$radiusMeters,$latStr,$lonStr);
            );
            out center;
        """.trimIndent()

        val encodedBody = "data=" + URLEncoder.encode(overpassQl, "UTF-8")
        val failureReasons = mutableListOf<String>()

        for (endpoint in OVERPASS_ENDPOINTS) {
            try {
                val url = URL(endpoint)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 12_000
                    readTimeout = 15_000
                    doOutput = true
                    setRequestProperty("User-Agent", APP_USER_AGENT)
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                }

                try {
                    conn.outputStream.use { os ->
                        os.write(encodedBody.toByteArray(Charsets.UTF_8))
                    }
                    val code = conn.responseCode
                    if (code == HttpURLConnection.HTTP_OK) {
                        val responseText = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                            it.readText()
                        }
                        val stations = parseOverpassElements(responseText)
                        return@withContext OverpassFuelResult.Success(
                            stations = stations,
                            endpointUsed = endpoint
                        )
                    } else {
                        failureReasons.add("${ URL(endpoint).host }: HTTP $code")
                    }
                } finally {
                    conn.disconnect()
                }
            } catch (e: Exception) {
                val host = runCatching { URL(endpoint).host }.getOrDefault(endpoint)
                failureReasons.add("$host: ${e.localizedMessage ?: "เชื่อมต่อไม่สำเร็จ"}")
            }
        }

        val combinedReason = failureReasons.joinToString(" / ").ifBlank { "ไม่สามารถเชื่อมต่อเซิร์ฟเวอร์ได้" }
        OverpassFuelResult.Failure("ค้นหาปั๊มน้ำมันไม่สำเร็จ ($combinedReason)")
    }

    internal fun parseOverpassElements(jsonString: String): List<StopPoiItem> {
        val root = JSONObject(jsonString)
        val elements = root.optJSONArray("elements") ?: return emptyList()
        val now = System.currentTimeMillis()
        val results = ArrayList<StopPoiItem>(elements.length())

        for (i in 0 until elements.length()) {
            val el = elements.optJSONObject(i) ?: continue
            val idNum = el.optLong("id", i.toLong())
            val typeStr = el.optString("type", "node")

            val lat: Double?
            val lon: Double?
            if (el.has("lat") && el.has("lon")) {
                lat = el.optDouble("lat")
                lon = el.optDouble("lon")
            } else {
                val center = el.optJSONObject("center")
                lat = center?.optDouble("lat")
                lon = center?.optDouble("lon")
            }

            if (lat == null || lon == null || lat.isNaN() || lon.isNaN()) continue

            val tags = el.optJSONObject("tags")
            val rawName = tags?.optString("name")?.trim().orEmpty()
            val rawBrand = tags?.optString("brand")?.trim().orEmpty()
            val rawOperator = tags?.optString("operator")?.trim().orEmpty()

            val displayName = when {
                rawName.isNotEmpty() -> rawName
                rawBrand.isNotEmpty() -> rawBrand
                rawOperator.isNotEmpty() -> rawOperator
                else -> "ปั๊มน้ำมัน"
            }

            results.add(
                StopPoiItem(
                    id = "OSM-$typeStr-$idNum",
                    name = displayName,
                    category = StopPoiCategory.FUEL,
                    latitude = lat,
                    longitude = lon,
                    isUserSaved = false,
                    timestampMillis = now
                )
            )
        }
        return results
    }

    /**
     * Queries Open-Meteo (https://api.open-meteo.com/v1/forecast) without any API key.
     * Requests precipitation, precipitation_probability, minutely_15, timezone=auto.
     * Never returns mock/fake data.
     */
    suspend fun fetchRainForecastOpenMeteo(
        latitude: Double,
        longitude: Double,
        probabilityThresholdPercent: Int,
        leadTimeWindowMinutes: Int
    ): OpenMeteoResult = withContext(Dispatchers.IO) {
        val latStr = String.format(Locale.US, "%.5f", latitude)
        val lonStr = String.format(Locale.US, "%.5f", longitude)
        val urlStr =
            "https://api.open-meteo.com/v1/forecast?latitude=$latStr&longitude=$lonStr" +
                "&minutely_15=precipitation,precipitation_probability" +
                "&timezone=auto&forecast_days=1"

        try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", APP_USER_AGENT)
                setRequestProperty("Accept", "application/json")
            }
            try {
                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    return@withContext OpenMeteoResult.Failure("ดึงพยากรณ์ไม่ได้ (HTTP $code)")
                }
                val body = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use {
                    it.readText()
                }
                val parsed = parseOpenMeteoMinutely15(
                    jsonString = body,
                    probabilityThresholdPercent = probabilityThresholdPercent,
                    leadTimeWindowMinutes = leadTimeWindowMinutes,
                    nowEpochMillis = System.currentTimeMillis()
                )
                if (parsed != null) {
                    OpenMeteoResult.Success(parsed)
                } else {
                    OpenMeteoResult.Failure("ดึงพยากรณ์ไม่ได้")
                }
            } finally {
                conn.disconnect()
            }
        } catch (_: Exception) {
            OpenMeteoResult.Failure("ดึงพยากรณ์ไม่ได้")
        }
    }

    internal fun parseOpenMeteoMinutely15(
        jsonString: String,
        probabilityThresholdPercent: Int,
        leadTimeWindowMinutes: Int,
        nowEpochMillis: Long
    ): OpenMeteoParsedForecast? {
        val root = JSONObject(jsonString)
        val utcOffsetSeconds = root.optInt("utc_offset_seconds", 0)
        val minutely15 = root.optJSONObject("minutely_15") ?: return null
        val times = minutely15.optJSONArray("time") ?: return null
        val precipArr = minutely15.optJSONArray("precipitation")
        val probArr = minutely15.optJSONArray("precipitation_probability")

        val count = times.length()
        if (count == 0) return null

        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("GMT")
        }

        // Convert each local ISO timestamp to actual UTC epoch millis using utc_offset_seconds
        var startSlotIndex = 0
        var minAbsDiff = Long.MAX_VALUE
        val slotEpochs = LongArray(count)

        for (i in 0 until count) {
            val tStr = times.optString(i, "")
            val parsedGmtMillis = runCatching { sdf.parse(tStr)?.time }.getOrNull() ?: continue
            val slotUtcMillis = parsedGmtMillis - (utcOffsetSeconds * 1000L)
            slotEpochs[i] = slotUtcMillis
            val diff = kotlin.math.abs(slotUtcMillis - nowEpochMillis)
            if (diff < minAbsDiff) {
                minAbsDiff = diff
                startSlotIndex = i
            }
        }

        // Inspect slots from current slot up to 60 minutes (4 x 15-minute slots)
        val maxSlotsToInspect = max(4, (leadTimeWindowMinutes / 15) + 1)
        var detectedMinutesUntilRain: Int? = null
        var maxProb = 0
        var maxPrecipMm = 0.0

        for (offset in 0..maxSlotsToInspect) {
            val idx = startSlotIndex + offset
            if (idx >= count) break
            val prob = probArr?.optDouble(idx, 0.0)?.takeIf { !it.isNaN() }?.roundToInt() ?: 0
            val precip = precipArr?.optDouble(idx, 0.0)?.takeIf { !it.isNaN() } ?: 0.0

            if (prob > maxProb) maxProb = prob
            if (precip > maxPrecipMm) maxPrecipMm = precip

            val isRainSlot = prob >= probabilityThresholdPercent || precip >= 0.15
            if (isRainSlot && detectedMinutesUntilRain == null) {
                val rawMinutes = ((slotEpochs[idx] - nowEpochMillis) / 60_000L).toInt()
                detectedMinutesUntilRain = when {
                    rawMinutes <= 5 -> 15
                    rawMinutes > 60 -> 60
                    else -> rawMinutes
                }
            }
        }

        return OpenMeteoParsedForecast(
            willRainSoon = detectedMinutesUntilRain != null,
            minutesUntilRain = detectedMinutesUntilRain,
            probabilityPercent = maxProb.coerceIn(0, 100),
            precipitationMm = maxPrecipMm,
            fetchedAtMillis = nowEpochMillis
        )
    }
}
