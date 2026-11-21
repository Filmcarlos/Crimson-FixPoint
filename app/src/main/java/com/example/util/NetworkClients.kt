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
import java.util.Locale

sealed class OverpassFuelResult {
    data class Success(
        val stations: List<StopPoiItem>,
        val endpointUsed: String
    ) : OverpassFuelResult()

    data class Failure(
        val reasonTh: String
    ) : OverpassFuelResult()
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
}
