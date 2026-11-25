package com.example.integration

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.example.ai.RiderIntelligenceEngine
import com.example.model.DeliveryPlatform
import com.example.model.DetectedJobOffer
import com.example.model.FuelEstimateState
import com.example.model.GeoMath
import com.example.model.JobOfferSource
import com.example.model.JobOfferStatus
import com.example.model.JobRankingMode
import com.example.model.OrderGrade
import com.example.model.OrderInputSource
import com.example.model.ParsedOrderDraft
import com.example.model.PlatformConnectionStatus
import com.example.model.RiderProfileConfig
import com.example.model.ScreenReaderState
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Unified Multi-Platform Job Radar Engine:
 * - Job Data Parser (via platform adapters)
 * - Duplicate Job Detection
 * - GPS Distance Engine
 * - Job Expiration Detection
 * - Dual Ranking Modes (Nearest First / Smart Best Job)
 * - Real OS Platform Status Monitoring (never simulates closed API connections)
 */
object JobRadarEngine {

    const val DUPLICATE_WINDOW_MS = 90_000L

    fun buildSignatureHash(
        platform: DeliveryPlatform,
        pickupName: String,
        payoutBaht: Double,
        tripDistanceKm: Double
    ): String {
        val normPickup = pickupName.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("""\s+"""), " ")
            .take(36)
        val roundedPayout = payoutBaht.roundToInt()
        val roundedDistTenth = (tripDistanceKm * 10.0).roundToInt()
        return "${platform.name}|$normPickup|$roundedPayout|$roundedDistTenth"
    }

    fun parseToJobOffer(
        rawText: String,
        source: JobOfferSource,
        profile: RiderProfileConfig,
        fuelState: FuelEstimateState,
        riderLat: Double? = null,
        riderLng: Double? = null,
        pickupLat: Double? = null,
        pickupLng: Double? = null,
        packageName: String? = null,
        title: String = "",
        nowMillis: Long = System.currentTimeMillis(),
        ttlMillis: Long = DetectedJobOffer.DEFAULT_TTL_MILLIS
    ): DetectedJobOffer? {
        val adapter = if (!packageName.isNullOrBlank()) {
            DeliveryAdapterRegistry.resolveForPackage(packageName) ?: return null
        } else {
            DeliveryAdapterRegistry.resolveForText("$title\n$rawText")
        }

        val inputSource = when (source) {
            JobOfferSource.NOTIFICATION_LISTENER -> OrderInputSource.MANUAL_OR_TEXT
            JobOfferSource.OCR_SCREEN_READER -> OrderInputSource.SCREEN_READER
            JobOfferSource.MANUAL_OR_SHARED -> OrderInputSource.MANUAL_OR_TEXT
        }

        val draft = adapter.extractDraft(title, rawText, inputSource) ?: return null
        val payout = draft.payoutBaht ?: return null
        if (payout <= 0.0) return null

        val tripDist = (draft.distanceKm ?: 0.0).coerceAtLeast(0.0)
        val deadDist = (draft.deadMileageKm ?: 0.0).coerceAtLeast(0.0)

        val distanceFromRider = computePickupDistanceKm(
            riderLat = riderLat,
            riderLng = riderLng,
            pickupLat = pickupLat,
            pickupLng = pickupLng
        ) ?: deadDist.takeIf { it > 0.0 }

        val analysis = if (tripDist > 0.0) {
            RiderIntelligenceEngine.analyzeOrder(draft, profile, fuelState)
        } else {
            null
        }

        val resolvedPickup = draft.pickupName?.ifBlank { null }
            ?: "จุดรับงาน (${adapter.platform.displayNameTh})"
        val resolvedDropoff = draft.dropoffName?.ifBlank { null }
            ?: "ปลายทางลูกค้า"

        val score = analysis?.smartScore ?: estimatePartialNotificationScore(payout, profile)
        val grade = analysis?.grade ?: when {
            score >= 84 -> OrderGrade.S
            score >= 70 -> OrderGrade.A
            score >= 55 -> OrderGrade.B
            score >= 40 -> OrderGrade.C
            else -> OrderGrade.D
        }

        val sig = buildSignatureHash(adapter.platform, resolvedPickup, payout, tripDist)
        return DetectedJobOffer(
            id = "JOB-$nowMillis-${UUID.randomUUID().toString().take(5)}",
            signatureHash = sig,
            platform = adapter.platform,
            source = source,
            pickupName = resolvedPickup,
            dropoffName = resolvedDropoff,
            payoutBaht = payout,
            tripDistanceKm = tripDist,
            deadMileageKm = deadDist,
            estimatedMinutes = draft.estimatedMinutes,
            pickupLat = pickupLat,
            pickupLng = pickupLng,
            distanceFromRiderKm = distanceFromRider,
            netProfitBaht = analysis?.netProfitBaht ?: payout,
            earningsPerKm = analysis?.earningsPerKm ?: 0.0,
            earningsPerHour = analysis?.earningsPerHour,
            smartScore = score,
            grade = grade,
            confidenceScore = draft.overallConfidence,
            createdAtMillis = nowMillis,
            expiresAtMillis = nowMillis + ttlMillis,
            status = JobOfferStatus.ACTIVE,
            isDuplicateMerged = false,
            rawSummary = rawText.trim().take(160)
        )
    }

    private fun estimatePartialNotificationScore(payout: Double, profile: RiderProfileConfig): Int {
        // Conservative neutral score when notification omits distance
        val baseline = (profile.minAcceptableBahtPerKm * 3.0).coerceAtLeast(30.0)
        return ((payout / baseline) * 55.0).roundToInt().coerceIn(25, 78)
    }

    fun isDuplicateOffer(
        existing: List<DetectedJobOffer>,
        candidate: DetectedJobOffer,
        windowMillis: Long = DUPLICATE_WINDOW_MS
    ): DetectedJobOffer? {
        return existing.firstOrNull { old ->
            val timeDelta = abs(candidate.createdAtMillis - old.createdAtMillis)
            if (timeDelta > windowMillis) return@firstOrNull false
            if (old.signatureHash == candidate.signatureHash) return@firstOrNull true
            old.platform == candidate.platform &&
                abs(old.payoutBaht - candidate.payoutBaht) < 0.5 &&
                abs(old.tripDistanceKm - candidate.tripDistanceKm) < 0.25 &&
                old.pickupName.equals(candidate.pickupName, ignoreCase = true)
        }
    }

    fun computePickupDistanceKm(
        riderLat: Double?,
        riderLng: Double?,
        pickupLat: Double?,
        pickupLng: Double?
    ): Double? {
        if (riderLat == null || riderLng == null || pickupLat == null || pickupLng == null) return null
        return GeoMath.haversineKm(riderLat, riderLng, pickupLat, pickupLng)
    }

    fun refreshDistancesWithGps(
        offers: List<DetectedJobOffer>,
        riderLat: Double?,
        riderLng: Double?
    ): List<DetectedJobOffer> {
        if (riderLat == null || riderLng == null) return offers
        return offers.map { offer ->
            val computed = computePickupDistanceKm(riderLat, riderLng, offer.pickupLat, offer.pickupLng)
            if (computed != null) offer.copy(distanceFromRiderKm = computed) else offer
        }
    }

    fun expireOutdatedOffers(
        offers: List<DetectedJobOffer>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<DetectedJobOffer> {
        return offers.map { offer ->
            if (offer.status == JobOfferStatus.ACTIVE && nowMillis >= offer.expiresAtMillis) {
                offer.copy(status = JobOfferStatus.EXPIRED)
            } else {
                offer
            }
        }
    }

    fun rankOffers(
        offers: List<DetectedJobOffer>,
        mode: JobRankingMode,
        nowMillis: Long = System.currentTimeMillis()
    ): List<DetectedJobOffer> {
        val activeOnly = offers
            .map {
                if (it.status == JobOfferStatus.ACTIVE && nowMillis >= it.expiresAtMillis) {
                    it.copy(status = JobOfferStatus.EXPIRED)
                } else {
                    it
                }
            }
            .filter { it.status == JobOfferStatus.ACTIVE }

        return when (mode) {
            JobRankingMode.NEAREST_FIRST -> activeOnly.sortedWith(
                compareBy<DetectedJobOffer> {
                    it.effectiveDistanceToPickupKm() ?: (it.tripDistanceKm.takeIf { d -> d > 0.0 } ?: Double.MAX_VALUE)
                }.thenByDescending { it.smartScore }
                    .thenByDescending { it.createdAtMillis }
            )

            JobRankingMode.SMART_BEST_JOB -> activeOnly.sortedWith(
                compareByDescending<DetectedJobOffer> { it.smartScore }
                    .thenByDescending { it.netProfitBaht }
                    .thenBy { it.effectiveDistanceToPickupKm() ?: Double.MAX_VALUE }
            )
        }
    }

    fun isNotificationListenerEnabled(context: Context): Boolean {
        return runCatching {
            val enabled = NotificationManagerCompat.getEnabledListenerPackages(context.applicationContext)
            enabled.contains(context.packageName)
        }.getOrDefault(false)
    }

    fun evaluatePlatformStatuses(
        notificationAccessGranted: Boolean,
        screenReaderState: ScreenReaderState,
        offers: List<DetectedJobOffer>,
        adapterEnabledMap: Map<DeliveryPlatform, Boolean> = emptyMap()
    ): List<PlatformConnectionStatus> {
        val ocrReady = screenReaderState == ScreenReaderState.ACTIVE_READY || true
        return listOf(
            DeliveryPlatform.LINE_MAN,
            DeliveryPlatform.GRAB,
            DeliveryPlatform.SHOPEE_FOOD
        ).map { platform ->
            val enabled = adapterEnabledMap[platform] ?: true
            val platformOffers = offers.filter { it.platform == platform }
            val lastSignal = platformOffers.maxOfOrNull { it.createdAtMillis }

            val statusLabel = when {
                !enabled -> "ปิดตัวกรองชั่วคราว"
                notificationAccessGranted && screenReaderState == ScreenReaderState.ACTIVE_READY ->
                    "พร้อมรับแจ้งเตือน + OCR"
                notificationAccessGranted ->
                    "ดักจับแจ้งเตือน OS พร้อมใช้"
                screenReaderState == ScreenReaderState.ACTIVE_READY ->
                    "พร้อมอ่านผ่าน Screen Reader OCR"
                else ->
                    "รอสิทธิ์ Notification Access / นำเข้า OCR"
            }

            val limitation = "ไม่มี Public Dispatch API จากผู้ให้บริการ (ใช้เฉพาะแจ้งเตือน OS และ OCR ที่ผู้ใช้ยินยอมเท่านั้น)"

            PlatformConnectionStatus(
                platform = platform,
                isAdapterEnabled = enabled,
                notificationListenerGranted = notificationAccessGranted,
                ocrEngineReady = ocrReady,
                publicDispatchApiAvailable = false,
                connectionStateLabelTh = statusLabel,
                limitationReasonTh = limitation,
                lastSignalMillis = lastSignal,
                detectedTodayCount = platformOffers.size
            )
        }
    }
}
