package com.example.model

import androidx.compose.ui.graphics.Color
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.EmeraldSRank
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class TacticalTab(val titleTh: String, val titleEn: String, val badgeCode: String) {
    FIXPOINT_CORE("เรดาร์ล็อคหมุด", "FIXPOINT CORE", "CORE-01"),
    SMART_FILTER("สแกนคัดงาน", "SMART FILTER", "SCAN-02"),
    HOTZONE_PINS("คลังหมุดทองคำ", "HOTZONE PINS", "PINS-03"),
    FLOATING_HUD("HUD & สัญญาณ", "HUD & GNSS", "DIAG-04")
}

enum class RiderPlatform(
    val displayName: String,
    val shortTag: String,
    val recPickupKm: Float,
    val recDropoffKm: Float,
    val gpsHz: Int,
    val accentColor: Color,
    val algorithmNote: String
) {
    LINE_MAN(
        displayName = "LINE MAN Rider",
        shortTag = "LM",
        recPickupKm = 0.7f,
        recDropoffKm = 2.0f,
        gpsHz = 10,
        accentColor = Color(0xFF10B981),
        algorithmNote = "เน้นรัศมีใกล้ร้าน < 700m + ตรึงพิกัดนิ่งหน้าดงร้านอาหาร"
    ),
    GRAB(
        displayName = "Grab Driver",
        shortTag = "GRAB",
        recPickupKm = 0.9f,
        recDropoffKm = 2.5f,
        gpsHz = 8,
        accentColor = Color(0xFF22C55E),
        algorithmNote = "คำนวณจุด Batch ร้านค้าใกล้เคียง + กันงานยิงยาวออกนอกโซน"
    ),
    SHOPEE_FOOD(
        displayName = "ShopeeFood",
        shortTag = "SPF",
        recPickupKm = 0.6f,
        recDropoffKm = 1.8f,
        gpsHz = 12,
        accentColor = Color(0xFFF97316),
        algorithmNote = "บีบรัศมี Micro-Accuracy สูงสุด เน้นงานสั้นทำรอบไว"
    ),
    LALAMOVE(
        displayName = "Lalamove",
        shortTag = "LLM",
        recPickupKm = 1.2f,
        recDropoffKm = 3.5f,
        gpsHz = 6,
        accentColor = Color(0xFFF59E0B),
        algorithmNote = "สแกนความคุ้มค่า บาท/กม. รวมระยะตีรถกลับหมุดหลัก"
    ),
    ALL_APPS(
        displayName = "All Apps (Multi-App)",
        shortTag = "ALL",
        recPickupKm = 0.8f,
        recDropoffKm = 2.0f,
        gpsHz = 15,
        accentColor = CrimsonRed,
        algorithmNote = "เปิดโหมด Hybrid 15Hz รองรับการเปิดหลายแอปพร้อมกัน"
    )
}

enum class TacticalLockMode(
    val titleEn: String,
    val titleTh: String,
    val subtitle: String,
    val kalmanGain: Float,
    val targetAccuracyMeters: Float
) {
    PIN_CENTER_MICRO(
        titleEn = "Pin-Center Micro Lock",
        titleTh = "ตรึงหมุดความถี่สูง (Micro-Accuracy)",
        subtitle = "บีบรัศมี Accuracy แคบสุด (±1.8m) + WakeLock กันสัญญาณหลับเพื่อแย่งงานร้านติดหมุด",
        kalmanGain = 0.88f,
        targetAccuracyMeters = 1.8f
    ),
    MERCHANT_CENTROID(
        titleEn = "Merchant Centroid Stabilizer",
        titleTh = "ล็อคจุดกึ่งกลางดงร้าน (Kalman Anti-Drift)",
        subtitle = "คำนวณพิกัดเฉลี่ยระหว่างร้านฮิตรอบหมุด ลดอาการพิกัดกระโดดใต้อาคารหรือชายคาห้าง",
        kalmanGain = 0.94f,
        targetAccuracyMeters = 2.4f
    ),
    AUTO_LOOP_COMPASS(
        titleEn = "Auto Loop-Back Compass",
        titleTh = "เข็มทิศดึงกลับหมุดอัตโนมัติ (Loop-Back)",
        subtitle = "แสดงเวกเตอร์เข็มทิศและเวลาเดินทางตีรถกลับเข้าหมุดหลักทันทีหลังส่งงานเสร็จ",
        kalmanGain = 0.82f,
        targetAccuracyMeters = 2.9f
    )
}

data class HotzonePin(
    val id: String,
    val name: String,
    val zoneTag: String,
    val lat: Double,
    val lng: Double,
    val merchantCount: Int,
    val avgOrderRate: String,
    val description: String,
    val isCustom: Boolean = false
)

data class MerchantPoint(
    val id: String,
    val name: String,
    val category: String,
    val lat: Double,
    val lng: Double,
    val weight: Float = 1.0f
)

enum class OrderGrade(
    val badgeTitle: String,
    val badgeSubtitle: String,
    val color: Color
) {
    S_RANK_LOOP(
        badgeTitle = "S-RANK: รับเลย! รับใกล้-ส่งวนหมุด",
        badgeSubtitle = "จุดรับและจุดส่งอยู่ในวงแหวนยุทธวิธี ตีรถกลับหมุดไว",
        color = EmeraldSRank
    ),
    A_RANK_IN_ZONE(
        badgeTitle = "A-RANK: ในโซนรับได้ (ระวังระยะรับ/ส่ง)",
        badgeSubtitle = "อยู่ในเกณฑ์อนุโลมแต่ควรเช็คค่าตอบแทนต่อกิโลเมตร",
        color = CrimsonNeon
    ),
    REJECT_OUT_OF_ZONE(
        badgeTitle = "REJECT / WARNING: จุดส่งฉีกออกนอกหมุด!",
        badgeSubtitle = "เสี่ยงตีรถเปล่ายาวหรือหลุดออกนอกโซนทำรอบ",
        color = AmberWarning
    )
}

data class IncomingOrderCard(
    val id: String,
    val platform: RiderPlatform,
    val merchantName: String,
    val dropoffName: String,
    val pickupLat: Double,
    val pickupLng: Double,
    val dropoffLat: Double,
    val dropoffLng: Double,
    val payoutBaht: Int,
    val estMinutes: Int,
    val crossesBridgeOrZone: Boolean = false
)

data class OrderEvaluation(
    val order: IncomingOrderCard,
    val pickupToPinKm: Double,
    val dropoffToPinKm: Double,
    val orderTripKm: Double,
    val loopBackKm: Double,
    val totalLoopKm: Double,
    val bahtPerLoopKm: Double,
    val isPickupWithinRadius: Boolean,
    val isDropoffWithinRadius: Boolean,
    val grade: OrderGrade,
    val verdictDetail: String
)

data class AutoFilterRules(
    val notifyOnlyWithinDropoffRadius: Boolean = true,
    val blockCrossBridgeOrZone: Boolean = true,
    val shortLoopPriority: Boolean = true,
    val minBahtPerLoopKm: Float = 12.0f
)

data class SatelliteSnr(
    val constellation: String,
    val svId: String,
    val snrDbHz: Int,
    val usedInFix: Boolean,
    val color: Color
)

data class LockDriftLog(
    val id: String,
    val timestamp: String,
    val eventTitle: String,
    val pinName: String,
    val pickupDistKm: Double,
    val dropoffDistKm: Double,
    val statusTag: String,
    val isPositive: Boolean
)

object GeoMath {
    private const val EARTH_RADIUS_KM = 6371.0

    /**
     * Real Haversine distance formula returning distance in kilometers.
     */
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

    /**
     * Computes compass bearing in degrees (0..360) from point 1 to point 2.
     */
    fun bearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLon = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLon)
        val theta = atan2(y, x)
        return ((Math.toDegrees(theta) + 360.0) % 360.0).toFloat()
    }

    /**
     * Converts bearing degrees to Thai/English tactical cardinal direction.
     */
    fun cardinalDirection(bearing: Float): String {
        val dirs = listOf(
            "N (เหนือ)", "NE (ตะวันออกเฉียงเหนือ)", "E (ตะวันออก)", "SE (ตะวันออกเฉียงใต้)",
            "S (ใต้)", "SW (ตะวันตกเฉียงใต้)", "W (ตะวันตก)", "NW (ตะวันตกเฉียงเหนือ)"
        )
        val idx = (((bearing + 22.5f) % 360f) / 45f).toInt().coerceIn(0, 7)
        return dirs[idx]
    }

    /**
     * Calculates Golden Centroid (weighted average coordinate) of merchants and average distance to all merchants.
     */
    fun calculateGoldenCentroid(merchants: List<MerchantPoint>): Triple<Double, Double, Double> {
        if (merchants.isEmpty()) return Triple(13.8768, 100.4113, 0.0)
        var sumLat = 0.0
        var sumLng = 0.0
        var totalWeight = 0.0
        for (m in merchants) {
            sumLat += m.lat * m.weight
            sumLng += m.lng * m.weight
            totalWeight += m.weight
        }
        val centroidLat = sumLat / totalWeight
        val centroidLng = sumLng / totalWeight
        val avgDistKm = merchants.map {
            haversineKm(centroidLat, centroidLng, it.lat, it.lng)
        }.average()
        return Triple(centroidLat, centroidLng, avgDistKm)
    }

    /**
     * Evaluates an IncomingOrderCard against the active Pin, Dual Radii, and Auto-Filter Rules.
     */
    fun evaluateOrder(
        order: IncomingOrderCard,
        pinLat: Double,
        pinLng: Double,
        maxPickupRadiusKm: Float,
        maxDropoffRadiusKm: Float,
        rules: AutoFilterRules
    ): OrderEvaluation {
        val pickupToPin = haversineKm(pinLat, pinLng, order.pickupLat, order.pickupLng)
        val dropoffToPin = haversineKm(pinLat, pinLng, order.dropoffLat, order.dropoffLng)
        // Road curvature multiplier (~1.22x straight line for urban Bangkok/Nonthaburi streets)
        val orderTripKm = haversineKm(order.pickupLat, order.pickupLng, order.dropoffLat, order.dropoffLng) * 1.22
        val loopBackKm = dropoffToPin * 1.20
        val totalLoopKm = (pickupToPin + orderTripKm + loopBackKm).coerceAtLeast(0.25)
        val bahtPerLoopKm = order.payoutBaht.toDouble() / totalLoopKm

        val pickupOk = pickupToPin <= maxPickupRadiusKm.toDouble()
        val dropoffOk = dropoffToPin <= maxDropoffRadiusKm.toDouble()
        val bridgeBlocked = rules.blockCrossBridgeOrZone && order.crossesBridgeOrZone
        val shortLoopOk = !rules.shortLoopPriority || totalLoopKm <= (maxDropoffRadiusKm * 2.4f)

        val grade = when {
            bridgeBlocked -> OrderGrade.REJECT_OUT_OF_ZONE
            pickupOk && dropoffOk && shortLoopOk && bahtPerLoopKm >= rules.minBahtPerLoopKm -> OrderGrade.S_RANK_LOOP
            dropoffOk && pickupToPin <= (maxPickupRadiusKm * 1.35f) -> OrderGrade.A_RANK_IN_ZONE
            else -> OrderGrade.REJECT_OUT_OF_ZONE
        }

        val detail = when (grade) {
            OrderGrade.S_RANK_LOOP ->
                "[S-RANK: รับเลย! รับใกล้-ส่งวนหมุด] ร้านห่างหมุด ${formatKm(pickupToPin)} กม. • จุดส่งห่างหมุด ${formatKm(dropoffToPin)} กม."
            OrderGrade.A_RANK_IN_ZONE ->
                "[A-RANK: ในโซน] จุดส่งห่างหมุด ${formatKm(dropoffToPin)} กม. • คุ้มค่ารอบวิ่ง ${format1Dec(bahtPerLoopKm)} ฿/กม."
            OrderGrade.REJECT_OUT_OF_ZONE -> {
                if (bridgeBlocked) {
                    "[WARNING: บล็อกงานข้ามสะพาน/ข้ามเขต!] จุดส่งห่างหมุด ${formatKm(dropoffToPin)} กม."
                } else if (!dropoffOk) {
                    "[WARNING: จุดส่งฉีกออกนอกหมุด ${formatKm(dropoffToPin)} กม.] เกินรัศมีส่ง ${format1Dec(maxDropoffRadiusKm.toDouble())} กม."
                } else {
                    "[WARNING: ร้านอยู่ไกลหมุด ${formatKm(pickupToPin)} กม.] เกินรัศมีรับ ${format1Dec(maxPickupRadiusKm.toDouble())} กม."
                }
            }
        }

        return OrderEvaluation(
            order = order,
            pickupToPinKm = pickupToPin,
            dropoffToPinKm = dropoffToPin,
            orderTripKm = orderTripKm,
            loopBackKm = loopBackKm,
            totalLoopKm = totalLoopKm,
            bahtPerLoopKm = bahtPerLoopKm,
            isPickupWithinRadius = pickupOk,
            isDropoffWithinRadius = dropoffOk,
            grade = grade,
            verdictDetail = detail
        )
    }

    fun formatKm(value: Double): String {
        val rounded = (value * 100.0).roundToInt() / 100.0
        return String.format(java.util.Locale.US, "%.2f", rounded)
    }

    fun format1Dec(value: Double): String {
        val rounded = (value * 10.0).roundToInt() / 10.0
        return String.format(java.util.Locale.US, "%.1f", rounded)
    }
}
