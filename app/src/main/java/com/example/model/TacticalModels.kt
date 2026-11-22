package com.example.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class AppScreen(val titleTh: String, val titleEn: String) {
    DASHBOARD("ภาพรวม", "Dashboard"),
    AI_ASSISTANT("AI ผู้ช่วย", "AI Assistant"),
    SMART_MAP("โซนรับงาน", "Work Zone"),
    EARNINGS("รายได้", "Earnings"),
    PROFILE("โปรไฟล์", "Profile")
}

enum class PinCategory(val labelTh: String) {
    FIXPOINT("โซนรับงาน"),
    HOTZONE("โซนฮอตสปอต"),
    RESTAURANT("ร้านอาหาร/จุดรับ"),
    FUEL("ปั๊มน้ำมัน"),
    REST_AREA("จุดพักรถ")
}

enum class GpsLockState(val labelTh: String, val subLabelTh: String) {
    OFF("ปิดการทำงาน", "สถานะพัก"),
    SEARCHING("กำลังค้นหาสัญญาณ", "สแกนดาวเทียม GNSS"),
    LOCKED("ล็อกสัญญาณแล้ว", "ตรึงสัญญาณพิกัด")
}

enum class ZoneMonitorStatus(
    val titleTh: String,
    val detailTh: String
) {
    NO_ACTIVE_PIN("ไม่มีหมุดโซน", "หมุดหลัก: —"),
    WAITING_FOR_GPS("รอสัญญาณ GPS", "พิกัดปัจจุบัน: —"),
    INSIDE_ZONE("อยู่ในโซนรับงาน", "ภายในรัศมีที่กำหนด"),
    OUTSIDE_ZONE("ออกนอกโซนรับงานแล้ว", "เกินรัศมีที่กำหนด")
}

enum class MapTapActionMode(val labelTh: String) {
    MOVE_ACTIVE_PIN("ย้ายหมุดหลัก"),
    CREATE_NEW_PIN("เพิ่มหมุดใหม่")
}

enum class MeasurementUnit(
    val titleTh: String,
    val distanceUnitTh: String,
    val speedUnitTh: String
) {
    METRIC("ระบบเมตริก", "เมตร", "กม./ชม."),
    IMPERIAL("ระบบอิมพีเรียล", "ฟุต", "ไมล์/ชม."),
    NAUTICAL("ระบบเดินเรือและอากาศยาน", "เมตร", "นอต")
}

enum class UpdateFrequency(
    val titleTh: String,
    val detailTh: String,
    val intervalMillis: Long,
    val minIntervalMillis: Long
) {
    HIGH_PRECISION("ความถี่สูงสุด (0.5 วินาที)", "500 มิลลิวินาที", 500L, 250L),
    STANDARD("มาตรฐาน (1 วินาที)", "1,000 มิลลิวินาที", 1000L, 500L),
    BATTERY_SAVER("ประหยัดพลังงาน (3 วินาที)", "3,000 มิลลิวินาที", 3000L, 1500L)
}

enum class CoordinateFormat(val titleTh: String) {
    DECIMAL("ทศนิยมมาตรฐาน (DDD.DDDDDD°)"),
    DMS("องศา ลิปดา ฟิลิปดา (DDD° MM' SS.S\")")
}

data class SavedLocationItem(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val altitudeMeters: Double?,
    val timestampMillis: Long,
    val category: PinCategory = PinCategory.FIXPOINT,
    val historicalOrderCount: Int = 0,
    val historicalAvgPayoutBaht: Double = 0.0,
    val notes: String = ""
) {
    fun formattedTimestamp(): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.US)
        return sdf.format(Date(timestampMillis))
    }

    companion object {
        private const val RECORD_SEP = "\u001E"
        private const val FIELD_SEP = "\u001F"

        fun serializeList(items: List<SavedLocationItem>): String {
            return items.joinToString(RECORD_SEP) { item ->
                val safeName = item.name.replace(RECORD_SEP, " ").replace(FIELD_SEP, " ")
                val safeNotes = item.notes.replace(RECORD_SEP, " ").replace(FIELD_SEP, " ")
                listOf(
                    item.id,
                    safeName,
                    item.latitude.toString(),
                    item.longitude.toString(),
                    item.accuracyMeters?.toString() ?: "",
                    item.altitudeMeters?.toString() ?: "",
                    item.timestampMillis.toString(),
                    item.category.name,
                    item.historicalOrderCount.toString(),
                    item.historicalAvgPayoutBaht.toString(),
                    safeNotes
                ).joinToString(FIELD_SEP)
            }
        }

        fun deserializeList(raw: String?): List<SavedLocationItem> {
            if (raw.isNullOrBlank()) return emptyList()
            return raw.split(RECORD_SEP).mapNotNull { record ->
                val parts = record.split(FIELD_SEP)
                if (parts.size < 7) return@mapNotNull null
                val lat = parts[2].toDoubleOrNull() ?: return@mapNotNull null
                val lng = parts[3].toDoubleOrNull() ?: return@mapNotNull null
                val acc = parts[4].toFloatOrNull()
                val alt = parts[5].toDoubleOrNull()
                val ts = parts[6].toLongOrNull() ?: System.currentTimeMillis()
                val category = parts.getOrNull(7)
                    ?.let { runCatching { PinCategory.valueOf(it) }.getOrNull() }
                    ?: PinCategory.FIXPOINT
                val histCount = parts.getOrNull(8)?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                val histAvg = parts.getOrNull(9)?.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0
                val notes = parts.getOrNull(10).orEmpty()
                SavedLocationItem(
                    id = parts[0].ifBlank { "LOC-$ts" },
                    name = parts[1].ifBlank { "หมุดโซนรับงาน" },
                    latitude = lat,
                    longitude = lng,
                    accuracyMeters = acc,
                    altitudeMeters = alt,
                    timestampMillis = ts,
                    category = category,
                    historicalOrderCount = histCount,
                    historicalAvgPayoutBaht = histAvg,
                    notes = notes
                )
            }
        }
    }
}

data class SatelliteBarItem(
    val svid: Int,
    val constellationLabel: String,
    val cn0DbHz: Float,
    val usedInFix: Boolean
)

data class LiveGnssTelemetry(
    val hasFix: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val rawLatitude: Double? = null,
    val rawLongitude: Double? = null,
    val accuracyMeters: Float? = null,
    val altitudeMeters: Double? = null,
    val speedMetersPerSec: Float? = null,
    val bearingDegrees: Float? = null,
    val compassAzimuthDegrees: Float? = null,
    val satellitesUsedInFix: Int = 0,
    val satellitesVisible: Int = 0,
    val averageSnrDbHz: Float = 0f,
    val satelliteBars: List<SatelliteBarItem> = emptyList(),
    val lastFixTimestampMillis: Long? = null
) {
    val effectiveDirectionDegrees: Float?
        get() = when {
            speedMetersPerSec != null && speedMetersPerSec > 0.5f && bearingDegrees != null && bearingDegrees > 0f ->
                bearingDegrees
            compassAzimuthDegrees != null -> compassAzimuthDegrees
            bearingDegrees != null && bearingDegrees > 0f -> bearingDegrees
            else -> null
        }

    val signalQualityLabelTh: String
        get() = when {
            !hasFix && satellitesUsedInFix == 0 && averageSnrDbHz <= 0f -> "ไม่มีสัญญาณ"
            averageSnrDbHz >= 38f || (accuracyMeters != null && accuracyMeters <= 4.5f) -> "ดีเยี่ยม"
            averageSnrDbHz >= 30f || (accuracyMeters != null && accuracyMeters <= 10f) -> "ดี"
            averageSnrDbHz >= 22f || (accuracyMeters != null && accuracyMeters <= 20f) -> "ปานกลาง"
            else -> "อ่อน"
        }

    val signalQualityFraction: Float
        get() = when {
            averageSnrDbHz > 0f -> (averageSnrDbHz / 45f).coerceIn(0.08f, 1.0f)
            hasFix && accuracyMeters != null -> (1f - (accuracyMeters / 40f)).coerceIn(0.15f, 1.0f)
            satellitesVisible > 0 -> (satellitesUsedInFix.toFloat() / satellitesVisible.coerceAtLeast(1)).coerceIn(0.1f, 0.7f)
            else -> 0f
        }
}

object GeoMath {
    private const val EARTH_RADIUS_KM = 6371.0

    fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2).pow(2.0) +
            sin(dLon / 2).pow(2.0) * cos(rLat1) * cos(rLat2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_KM * c
    }

    fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        return haversineKm(lat1, lon1, lat2, lon2) * 1000.0
    }

    /**
     * Parses pasted or typed coordinate strings such as:
     * - "13.756331, 100.501762"
     * - "13.756331 100.501762"
     * - "พิกัด: 13.756331, 100.501762"
     */
    fun parseCoordinateInput(rawInput: String): Pair<Double, Double>? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null
        val regex = Regex("""[-+]?\d+(?:\.\d+)?""")
        val matches = regex.findAll(trimmed).mapNotNull { it.value.toDoubleOrNull() }.toList()
        if (matches.size < 2) return null
        val lat = matches[0]
        val lng = matches[1]
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return null
        return lat to lng
    }

    fun formatZoneDistanceTh(distanceMeters: Double?): String {
        if (distanceMeters == null) return "—"
        return if (distanceMeters >= 1000.0) {
            String.format(Locale.US, "%.2f กม. (%.0f ม.)", distanceMeters / 1000.0, distanceMeters)
        } else {
            String.format(Locale.US, "%.0f เมตร (%.2f กม.)", distanceMeters, distanceMeters / 1000.0)
        }
    }

    fun formatShortZoneDistanceTh(distanceMeters: Double?): String {
        if (distanceMeters == null) return "—"
        return if (distanceMeters >= 1000.0) {
            String.format(Locale.US, "%.2f กม.", distanceMeters / 1000.0)
        } else {
            String.format(Locale.US, "%.0f ม.", distanceMeters)
        }
    }

    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(lon2 - lon1)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        val theta = Math.toDegrees(atan2(y, x))
        return (((theta + 360.0) % 360.0)).toFloat()
    }

    fun shortCardinalDirectionTh(degrees: Float?): String {
        if (degrees == null) return "—"
        val normalized = ((degrees % 360f) + 360f) % 360f
        val dirs = listOf(
            "เหนือ",
            "ตะวันออกเฉียงเหนือ",
            "ตะวันออก",
            "ตะวันออกเฉียงใต้",
            "ใต้",
            "ตะวันตกเฉียงใต้",
            "ตะวันตก",
            "ตะวันตกเฉียงเหนือ"
        )
        val index = (((normalized + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)
        return "${dirs[index]} (${normalized.roundToInt()}°)"
    }

    fun cardinalDirectionTh(degrees: Float?): String {
        if (degrees == null) return "—"
        val normalized = ((degrees % 360f) + 360f) % 360f
        val dirs = listOf(
            "เหนือ (N)",
            "ตะวันออกเฉียงเหนือ (NE)",
            "ตะวันออก (E)",
            "ตะวันออกเฉียงใต้ (SE)",
            "ใต้ (S)",
            "ตะวันตกเฉียงใต้ (SW)",
            "ตะวันตก (W)",
            "ตะวันตกเฉียงเหนือ (NW)"
        )
        val index = (((normalized + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)
        return "${normalized.roundToInt()}° ${dirs[index]}"
    }

    fun formatCoordinate(value: Double?, isLatitude: Boolean, format: CoordinateFormat): String {
        if (value == null) return "—"
        return when (format) {
            CoordinateFormat.DECIMAL -> String.format(Locale.US, "%.6f°", value)
            CoordinateFormat.DMS -> {
                val hemisphere = if (isLatitude) {
                    if (value >= 0) "N" else "S"
                } else {
                    if (value >= 0) "E" else "W"
                }
                val absVal = abs(value)
                val deg = floor(absVal).toInt()
                val minFull = (absVal - deg) * 60.0
                val min = floor(minFull).toInt()
                val sec = (minFull - min) * 60.0
                String.format(Locale.US, "%d°%02d'%04.1f\"%s", deg, min, sec, hemisphere)
            }
        }
    }

    fun formatCoordinatePair(lat: Double?, lng: Double?, format: CoordinateFormat): String {
        if (lat == null || lng == null) return "—"
        return when (format) {
            CoordinateFormat.DECIMAL -> String.format(Locale.US, "%.6f, %.6f", lat, lng)
            CoordinateFormat.DMS -> "${formatCoordinate(lat, true, format)} ${formatCoordinate(lng, false, format)}"
        }
    }

    fun formatDistanceMeters(meters: Float?, unit: MeasurementUnit): String {
        if (meters == null) return "—"
        return when (unit) {
            MeasurementUnit.METRIC, MeasurementUnit.NAUTICAL ->
                String.format(Locale.US, "±%.1f %s", meters, unit.distanceUnitTh)
            MeasurementUnit.IMPERIAL ->
                String.format(Locale.US, "±%.1f %s", meters * 3.28084f, unit.distanceUnitTh)
        }
    }

    fun formatAltitude(altitudeMeters: Double?, unit: MeasurementUnit): String {
        if (altitudeMeters == null) return "—"
        return when (unit) {
            MeasurementUnit.METRIC, MeasurementUnit.NAUTICAL ->
                String.format(Locale.US, "%.1f %s", altitudeMeters, unit.distanceUnitTh)
            MeasurementUnit.IMPERIAL ->
                String.format(Locale.US, "%.1f %s", altitudeMeters * 3.28084, unit.distanceUnitTh)
        }
    }

    fun formatSpeed(speedMps: Float?, unit: MeasurementUnit): String {
        if (speedMps == null) return "—"
        val converted = when (unit) {
            MeasurementUnit.METRIC -> speedMps * 3.6f
            MeasurementUnit.IMPERIAL -> speedMps * 2.23694f
            MeasurementUnit.NAUTICAL -> speedMps * 1.94384f
        }
        return String.format(Locale.US, "%.1f %s", converted, unit.speedUnitTh)
    }
}
