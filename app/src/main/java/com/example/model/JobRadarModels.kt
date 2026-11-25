package com.example.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class DeliveryPlatform(
    val displayNameTh: String,
    val shortBadge: String,
    val knownPackages: List<String>
) {
    LINE_MAN(
        displayNameTh = "LINE MAN",
        shortBadge = "LM",
        knownPackages = listOf("com.linecorp.linemanth", "com.lineman.rider", "com.linecorp.lineman")
    ),
    GRAB(
        displayNameTh = "Grab",
        shortBadge = "GRAB",
        knownPackages = listOf("com.grabtaxi.driver2", "com.grabtaxi.driver")
    ),
    SHOPEE_FOOD(
        displayNameTh = "ShopeeFood",
        shortBadge = "SPF",
        knownPackages = listOf("com.shopeefood.driver", "com.shopee.food.driver.th", "com.shopee.food.rider")
    ),
    OTHER(
        displayNameTh = "แพลตฟอร์มอื่น",
        shortBadge = "APP",
        knownPackages = emptyList()
    );

    companion object {
        fun fromPackageName(pkg: String?): DeliveryPlatform? {
            if (pkg.isNullOrBlank()) return null
            val lower = pkg.lowercase(Locale.ROOT)
            return entries.firstOrNull { platform ->
                platform.knownPackages.any { it.equals(lower, ignoreCase = true) }
            } ?: when {
                "lineman" in lower -> LINE_MAN
                "grab" in lower && "driver" in lower -> GRAB
                "shopee" in lower && ("food" in lower || "driver" in lower || "rider" in lower) -> SHOPEE_FOOD
                else -> null
            }
        }

        fun fromHintText(text: String?): DeliveryPlatform {
            val lower = text?.lowercase(Locale.ROOT).orEmpty()
            return when {
                "lineman" in lower || "line man" in lower || "ไลน์แมน" in lower -> LINE_MAN
                "grab" in lower || "แกร็บ" in lower -> GRAB
                "shopee" in lower || "ช้อปปี้" in lower -> SHOPEE_FOOD
                else -> OTHER
            }
        }
    }
}

enum class JobRankingMode(val labelTh: String, val subtitleTh: String) {
    NEAREST_FIRST("ใกล้สุดก่อน", "เรียงตามระยะทางไปรับงานที่ใกล้ตำแหน่งคุณที่สุด"),
    SMART_BEST_JOB("คุ้มค่าที่สุด (AI)", "เรียงตามคะแนน Smart Score, ฿/กม. และกำไรสุทธิ")
}

enum class JobOfferSource(val labelTh: String) {
    NOTIFICATION_LISTENER("ดักจับแจ้งเตือน OS"),
    OCR_SCREEN_READER("OCR จากหน้าจอ/ภาพ"),
    MANUAL_OR_SHARED("นำเข้าข้อความ/วางข้อมูล")
}

enum class JobOfferStatus(val labelTh: String) {
    ACTIVE("กำลังเปิดรับ"),
    EXPIRED("หมดเวลาแล้ว"),
    ACCEPTED_LOGGED("บันทึกเข้าบัญชีแล้ว"),
    DISMISSED("ข้ามรายการ")
}

data class PlatformConnectionStatus(
    val platform: DeliveryPlatform,
    val isAdapterEnabled: Boolean,
    val notificationListenerGranted: Boolean,
    val ocrEngineReady: Boolean,
    val publicDispatchApiAvailable: Boolean = false,
    val connectionStateLabelTh: String,
    val limitationReasonTh: String,
    val lastSignalMillis: Long? = null,
    val detectedTodayCount: Int = 0
)

data class DetectedJobOffer(
    val id: String,
    val signatureHash: String,
    val platform: DeliveryPlatform,
    val source: JobOfferSource,
    val pickupName: String,
    val dropoffName: String,
    val payoutBaht: Double,
    val tripDistanceKm: Double,
    val deadMileageKm: Double = 0.0,
    val estimatedMinutes: Int? = null,
    val pickupLat: Double? = null,
    val pickupLng: Double? = null,
    val distanceFromRiderKm: Double? = null,
    val netProfitBaht: Double = 0.0,
    val earningsPerKm: Double = 0.0,
    val earningsPerHour: Double? = null,
    val smartScore: Int = 0,
    val grade: OrderGrade = OrderGrade.B,
    val confidenceScore: Float = 0f,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val expiresAtMillis: Long = createdAtMillis + DEFAULT_TTL_MILLIS,
    val status: JobOfferStatus = JobOfferStatus.ACTIVE,
    val isDuplicateMerged: Boolean = false,
    val rawSummary: String = ""
) {
    fun effectiveDistanceToPickupKm(): Double? = distanceFromRiderKm ?: deadMileageKm.takeIf { it > 0.0 }

    fun remainingSeconds(nowMillis: Long = System.currentTimeMillis()): Int {
        if (status != JobOfferStatus.ACTIVE) return 0
        return ((expiresAtMillis - nowMillis) / 1000L).toInt().coerceAtLeast(0)
    }

    fun isExpiredAt(nowMillis: Long = System.currentTimeMillis()): Boolean {
        return status == JobOfferStatus.EXPIRED || (status == JobOfferStatus.ACTIVE && nowMillis >= expiresAtMillis)
    }

    fun formattedCreatedTime(): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
        return sdf.format(Date(createdAtMillis))
    }

    companion object {
        const val DEFAULT_TTL_MILLIS = 90_000L
    }
}

data class CloudSyncState(
    val isMembershipProEnabled: Boolean = false,
    val cloudSyncOptIn: Boolean = false,
    val pendingSyncRecordsCount: Int = 0,
    val lastLocalBackupMillis: Long? = null,
    val lastCloudSyncAttemptMillis: Long? = null,
    val backendProvisioned: Boolean = false,
    val syncStatusLabelTh: String = "โหมด Offline-First ในเครื่อง",
    val syncDetailTh: String = "ข้อมูลทั้งหมดถูกบันทึกถาวรใน Room Database บนอุปกรณ์ของคุณ"
)
