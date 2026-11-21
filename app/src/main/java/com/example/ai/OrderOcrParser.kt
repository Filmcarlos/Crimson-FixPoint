package com.example.ai

import com.example.model.OrderInputSource
import com.example.model.ParsedOrderDraft

/**
 * Deterministic, offline delivery-order OCR text parser.
 * Supports Thai & English delivery app patterns (Grab, LINE MAN, ShopeeFood, Robinhood, Lalamove, Foodpanda).
 *
 * Strict integrity guarantee:
 * - Never fabricates missing fields; undetected values stay null and are reported in [ParsedOrderDraft.missingFields].
 */
object OrderOcrParser {

    private val CURRENCY_PATTERNS = listOf(
        Regex("""(?:฿|THB|บาท)\s*([0-9]{1,4}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE),
        Regex("""([0-9]{1,4}(?:,[0-9]{3})*(?:\.[0-9]{1,2})?)\s*(?:฿|THB|บาท)""", RegexOption.IGNORE_CASE),
        Regex("""(?:รายได้|ค่าส่ง|ค่าตอบแทน|ยอดรับ|payout|fare|earn(?:ings)?)\s*[:：]?\s*(?:฿|THB)?\s*([0-9]{1,4}(?:\.[0-9]{1,2})?)""", RegexOption.IGNORE_CASE)
    )

    private val DISTANCE_KM_PATTERNS = listOf(
        Regex("""(?:รวม|ระยะทาง|distance|total)\s*[:：]?\s*([0-9]+(?:\.[0-9]+)?)\s*(?:กม\.?|km)""", RegexOption.IGNORE_CASE),
        Regex("""([0-9]+(?:\.[0-9]+)?)\s*(?:กม\.?|km)\b""", RegexOption.IGNORE_CASE)
    )

    private val DISTANCE_M_PATTERN = Regex("""\b([0-9]{2,4})\s*(?:ม\.|เมตร|m)\b""", RegexOption.IGNORE_CASE)

    private val DEAD_MILEAGE_PATTERNS = listOf(
        Regex("""(?:ไปรับ|ถึงร้าน|รับงาน|pickup|to\s*store|to\s*pickup)\s*[:：]?\s*([0-9]+(?:\.[0-9]+)?)\s*(?:กม\.?|km)""", RegexOption.IGNORE_CASE),
        Regex("""(?:ไปรับ|ถึงร้าน|pickup)\s*[:：]?\s*([0-9]{2,4})\s*(?:ม\.|เมตร|m)\b""", RegexOption.IGNORE_CASE)
    )

    private val DURATION_HOUR_MIN_PATTERN = Regex(
        """([0-9]{1,2})\s*(?:ชม\.?|ชั่วโมง|hr|h)\s*([0-9]{1,2})\s*(?:นาที|min|m)""",
        RegexOption.IGNORE_CASE
    )
    private val DURATION_MIN_PATTERN = Regex(
        """([0-9]{1,3})\s*(?:นาที|mins?|minutes?)\b""",
        RegexOption.IGNORE_CASE
    )

    private val PICKUP_LINE_REGEX = Regex("""^(?:รับ|ร้าน|จุดรับ|pickup|from|store)\s*[:：\-]?\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val DROPOFF_LINE_REGEX = Regex("""^(?:ส่ง|ปลายทาง|ลูกค้า|จุดส่ง|dropoff|drop-off|to|deliver)\s*[:：\-]?\s*(.+)$""", RegexOption.IGNORE_CASE)

    fun parseOrderText(
        rawText: String,
        source: OrderInputSource = OrderInputSource.MANUAL_OR_TEXT
    ): ParsedOrderDraft {
        val cleaned = rawText.trim()
        if (cleaned.isEmpty()) {
            return ParsedOrderDraft(
                source = source,
                rawOcrText = "",
                overallConfidence = 0f,
                missingFields = listOf("รายได้ (บาท)", "ระยะทาง (กม.)", "เวลาโดยประมาณ (นาที)"),
                warnings = listOf("ไม่พบข้อความในภาพหรือข้อความนำเข้า กรุณากรอกข้อมูลงานด้วยตนเอง")
            )
        }

        val lines = cleaned.lines().map { it.trim() }.filter { it.isNotEmpty() }

        // 1. Platform hint (informational only)
        val lower = cleaned.lowercase()
        val platformHint = when {
            "grab" in lower -> "Grab"
            "lineman" in lower || "line man" in lower -> "LINE MAN"
            "shopee" in lower -> "ShopeeFood"
            "robinhood" in lower -> "Robinhood"
            "lalamove" in lower -> "Lalamove"
            "foodpanda" in lower -> "foodpanda"
            else -> null
        }

        // 2. Payout (Baht)
        var payoutBaht: Double? = null
        var payoutConfidence = 0f
        for ((idx, pattern) in CURRENCY_PATTERNS.withIndex()) {
            val match = pattern.find(cleaned)
            val parsed = match?.groupValues?.getOrNull(1)?.replace(",", "")?.toDoubleOrNull()
            if (parsed != null && parsed in 5.0..50_000.0) {
                payoutBaht = parsed
                payoutConfidence = if (idx == 2) 0.94f else 0.86f
                break
            }
        }

        // 3. Dead mileage (distance to pickup if explicitly stated)
        var deadMileageKm: Double? = null
        DEAD_MILEAGE_PATTERNS[0].find(cleaned)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let {
            if (it in 0.05..100.0) deadMileageKm = it
        }
        if (deadMileageKm == null) {
            DEAD_MILEAGE_PATTERNS[1].find(cleaned)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.let { meters ->
                if (meters in 20.0..9999.0) deadMileageKm = meters / 1000.0
            }
        }

        // 4. Trip distance (km)
        var distanceKm: Double? = null
        var distanceConfidence = 0f
        for ((idx, pattern) in DISTANCE_KM_PATTERNS.withIndex()) {
            val allKm = pattern.findAll(cleaned)
                .mapNotNull { it.groupValues.getOrNull(1)?.toDoubleOrNull() }
                .filter { it in 0.1..250.0 }
                .toList()
            if (allKm.isNotEmpty()) {
                // If multiple distances appear (e.g. pickup 1.2 km + total 5.4 km), take the largest plausible trip distance
                distanceKm = if (idx == 0) allKm.first() else allKm.maxOrNull()
                distanceConfidence = if (idx == 0) 0.92f else 0.82f
                break
            }
        }
        if (distanceKm == null) {
            val mMatch = DISTANCE_M_PATTERN.find(cleaned)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            if (mMatch != null && mMatch in 50.0..9999.0) {
                distanceKm = mMatch / 1000.0
                distanceConfidence = 0.72f
            }
        }

        // 5. Estimated duration (minutes)
        var estimatedMinutes: Int? = null
        var durationConfidence = 0f
        val hmMatch = DURATION_HOUR_MIN_PATTERN.find(cleaned)
        if (hmMatch != null) {
            val h = hmMatch.groupValues[1].toIntOrNull() ?: 0
            val m = hmMatch.groupValues[2].toIntOrNull() ?: 0
            val total = h * 60 + m
            if (total in 1..480) {
                estimatedMinutes = total
                durationConfidence = 0.90f
            }
        } else {
            val minMatch = DURATION_MIN_PATTERN.find(cleaned)?.groupValues?.getOrNull(1)?.toIntOrNull()
            if (minMatch != null && minMatch in 1..480) {
                estimatedMinutes = minMatch
                durationConfidence = 0.85f
            }
        }

        // 6. Pickup & Dropoff names
        var pickupName: String? = null
        var dropoffName: String? = null
        for (line in lines) {
            if (pickupName == null) {
                PICKUP_LINE_REGEX.find(line)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.length >= 2 }?.let {
                    pickupName = it.take(64)
                }
            }
            if (dropoffName == null) {
                DROPOFF_LINE_REGEX.find(line)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.length >= 2 }?.let {
                    dropoffName = it.take(64)
                }
            }
        }

        val missing = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        if (payoutBaht == null) missing.add("รายได้ (บาท)")
        if (distanceKm == null) missing.add("ระยะทาง (กม.)")
        if (estimatedMinutes == null) missing.add("เวลาโดยประมาณ (นาที)")

        if (missing.isNotEmpty()) {
            warnings.add("ระบบไม่พบข้อมูลบางส่วน (${missing.joinToString(", ")}) กรุณาตรวจสอบและกรอกเพิ่มเติมก่อนบันทึก — ระบบจะไม่เดาตัวเลขแทน")
        }

        val detectedWeights = listOf(
            payoutConfidence * 0.45f,
            distanceConfidence * 0.35f,
            durationConfidence * 0.20f
        ).sum()

        return ParsedOrderDraft(
            source = source,
            rawOcrText = cleaned,
            payoutBaht = payoutBaht,
            payoutConfidence = payoutConfidence,
            distanceKm = distanceKm,
            distanceConfidence = distanceConfidence,
            estimatedMinutes = estimatedMinutes,
            durationConfidence = durationConfidence,
            deadMileageKm = deadMileageKm,
            pickupName = pickupName,
            dropoffName = dropoffName,
            platformHint = platformHint,
            overallConfidence = detectedWeights.coerceIn(0f, 1f),
            missingFields = missing,
            warnings = warnings,
            wasUserCorrected = false
        )
    }
}
