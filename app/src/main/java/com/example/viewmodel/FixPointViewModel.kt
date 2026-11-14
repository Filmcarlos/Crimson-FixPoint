package com.example.viewmodel

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AutoFilterRules
import com.example.model.GeoMath
import com.example.model.HotzonePin
import com.example.model.IncomingOrderCard
import com.example.model.LockDriftLog
import com.example.model.MerchantPoint
import com.example.model.OrderEvaluation
import com.example.model.OrderGrade
import com.example.model.RiderPlatform
import com.example.model.SatelliteSnr
import com.example.model.TacticalLockMode
import com.example.model.TacticalTab
import com.example.service.LocationService
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.EmeraldSRank
import com.example.util.TacticalSoundEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

data class FixPointUiState(
    val activeTab: TacticalTab = TacticalTab.FIXPOINT_CORE,
    val selectedPlatform: RiderPlatform = RiderPlatform.LINE_MAN,
    val maxPickupRadiusKm: Float = 0.8f,
    val maxDropoffRadiusKm: Float = 2.0f,
    val lockMode: TacticalLockMode = TacticalLockMode.PIN_CENTER_MICRO,
    val isFixPointLocked: Boolean = true,
    val shockwaveTriggerCount: Int = 1,
    val isSoundMuted: Boolean = false,
    val showFloatingHudStrip: Boolean = true,
    val isFullBikeMountHudOpen: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val isUsingRealHardwareGps: Boolean = false,

    // Active Anchor Pin & Rider Telemetry
    val activePin: HotzonePin = FixPointViewModel.defaultHotzones().first(),
    val riderLat: Double = 13.8772,
    val riderLng: Double = 100.4116,
    val rawUnfilteredLat: Double = 13.8781,
    val rawUnfilteredLng: Double = 100.4125,
    val accuracyMeters: Float = 1.9f,
    val satelliteCount: Int = 26,
    val signalStabilityPercent: Int = 98,
    val kalmanDriftSuppressedMeters: Float = 14.6f,

    // Tab 2: Smart Order Filter & Live Scanner
    val presetOrders: List<IncomingOrderCard> = FixPointViewModel.defaultIncomingOrders(),
    val activeOrder: IncomingOrderCard = FixPointViewModel.defaultIncomingOrders().first(),
    val isScannerLaserActive: Boolean = true,
    val scanSequenceId: Int = 1,
    val autoFilterRules: AutoFilterRules = AutoFilterRules(),
    val sRankAcceptedCount: Int = 14,
    val outOfZoneRejectedCount: Int = 9,

    // Tab 3: Hotzones & Merchant Cluster Centroid
    val hotzonePins: List<HotzonePin> = FixPointViewModel.defaultHotzones(),
    val clusterMerchants: List<MerchantPoint> = FixPointViewModel.defaultMerchantsForPin(FixPointViewModel.defaultHotzones().first()),
    val goldenCentroidLat: Double = 13.8769,
    val goldenCentroidLng: Double = 100.4114,
    val goldenCentroidAvgDistKm: Double = 0.24,

    // Tab 4: GNSS SNR Matrix & Drift Log
    val satellites: List<SatelliteSnr> = FixPointViewModel.defaultSatellites(),
    val driftLogs: List<LockDriftLog> = FixPointViewModel.defaultDriftLogs()
) {
    val distanceToPinKm: Double
        get() = GeoMath.haversineKm(activePin.lat, activePin.lng, riderLat, riderLng)

    val distanceToPinMeters: Int
        get() = (distanceToPinKm * 1000.0).roundToInt()

    val loopBackBearingDeg: Float
        get() = GeoMath.bearingDegrees(riderLat, riderLng, activePin.lat, activePin.lng)

    val loopBackDirectionText: String
        get() = GeoMath.cardinalDirection(loopBackBearingDeg)

    val estReturnMinutes: Int
        get() = ((distanceToPinKm / 0.45).roundToInt()).coerceAtLeast(1)

    val currentOrderEvaluation: OrderEvaluation
        get() = GeoMath.evaluateOrder(
            order = activeOrder,
            pinLat = activePin.lat,
            pinLng = activePin.lng,
            maxPickupRadiusKm = maxPickupRadiusKm,
            maxDropoffRadiusKm = maxDropoffRadiusKm,
            rules = autoFilterRules
        )
}

class FixPointViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(FixPointUiState())
    val uiState: StateFlow<FixPointUiState> = _uiState.asStateFlow()

    private var locationService: LocationService? = null
    private var locationManager: LocationManager? = null
    private var locationListener: LocationListener? = null

    init {
        recalculateCentroidInternal(_uiState.value.clusterMerchants)
        startTelemetryKeepAliveLoop()
    }

    private fun startTelemetryKeepAliveLoop() {
        viewModelScope.launch {
            var tick = 0
            while (true) {
                delay(1800L)
                tick++
                _uiState.update { state ->
                    // Subtle realistic GNSS telemetry micro-fluctuation & Kalman stabilization
                    val jitterScale = if (state.isFixPointLocked) 0.00004 else 0.00038
                    val angle = (tick * 0.65)
                    val rawOffsetLat = sin(angle) * 0.00032
                    val rawOffsetLng = cos(angle * 0.8) * 0.00032

                    val stabilizedLat = if (state.isFixPointLocked) {
                        when (state.lockMode) {
                            TacticalLockMode.PIN_CENTER_MICRO ->
                                state.activePin.lat + sin(angle) * jitterScale
                            TacticalLockMode.MERCHANT_CENTROID ->
                                state.goldenCentroidLat + cos(angle) * jitterScale
                            TacticalLockMode.AUTO_LOOP_COMPASS ->
                                state.riderLat + (state.activePin.lat - state.riderLat) * 0.25
                        }
                    } else {
                        state.riderLat + rawOffsetLat * 0.35
                    }

                    val stabilizedLng = if (state.isFixPointLocked) {
                        when (state.lockMode) {
                            TacticalLockMode.PIN_CENTER_MICRO ->
                                state.activePin.lng + cos(angle) * jitterScale
                            TacticalLockMode.MERCHANT_CENTROID ->
                                state.goldenCentroidLng + sin(angle) * jitterScale
                            TacticalLockMode.AUTO_LOOP_COMPASS ->
                                state.riderLng + (state.activePin.lng - state.riderLng) * 0.25
                        }
                    } else {
                        state.riderLng + rawOffsetLng * 0.35
                    }

                    val targetAcc = if (state.isFixPointLocked) {
                        state.lockMode.targetAccuracyMeters + ((tick % 3) * 0.1f)
                    } else {
                        9.4f + ((tick % 5) * 1.1f)
                    }

                    val targetStability = if (state.isFixPointLocked) {
                        (97 + (tick % 3)).coerceAtMost(99)
                    } else {
                        72 + (tick % 9)
                    }

                    val updatedSatellites = state.satellites.mapIndexed { idx, sat ->
                        val delta = ((tick + idx) % 3) - 1
                        sat.copy(snrDbHz = (sat.snrDbHz + delta).coerceIn(24, 49))
                    }

                    state.copy(
                        riderLat = if (state.isUsingRealHardwareGps) state.riderLat else stabilizedLat,
                        riderLng = if (state.isUsingRealHardwareGps) state.riderLng else stabilizedLng,
                        rawUnfilteredLat = stabilizedLat + rawOffsetLat,
                        rawUnfilteredLng = stabilizedLng + rawOffsetLng,
                        accuracyMeters = targetAcc,
                        satelliteCount = if (state.isFixPointLocked) 26 + (tick % 4) else 17 + (tick % 3),
                        signalStabilityPercent = targetStability,
                        satellites = updatedSatellites
                    )
                }
            }
        }
    }

    fun selectTab(tab: TacticalTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun toggleSoundMute() {
        _uiState.update { state ->
            val nextMuted = !state.isSoundMuted
            if (!nextMuted) {
                TacticalSoundEngine.playLockChime(false)
            }
            state.copy(isSoundMuted = nextMuted)
        }
    }

    fun toggleFloatingHudStrip() {
        _uiState.update { it.copy(showFloatingHudStrip = !it.showFloatingHudStrip) }
    }

    fun setFullBikeMountHudOpen(open: Boolean) {
        _uiState.update { it.copy(isFullBikeMountHudOpen = open) }
    }

    fun selectPlatform(platform: RiderPlatform, applyRecommendedRadii: Boolean = true) {
        _uiState.update { state ->
            state.copy(
                selectedPlatform = platform,
                maxPickupRadiusKm = if (applyRecommendedRadii) platform.recPickupKm else state.maxPickupRadiusKm,
                maxDropoffRadiusKm = if (applyRecommendedRadii) platform.recDropoffKm else state.maxDropoffRadiusKm
            )
        }
        TacticalSoundEngine.playLockChime(_uiState.value.isSoundMuted)
    }

    fun updatePickupRadius(radiusKm: Float) {
        val clamped = ((radiusKm * 20f).roundToInt() / 20f).coerceIn(0.1f, 3.0f)
        _uiState.update { it.copy(maxPickupRadiusKm = clamped) }
    }

    fun updateDropoffRadius(radiusKm: Float) {
        val clamped = ((radiusKm * 10f).roundToInt() / 10f).coerceIn(0.5f, 8.0f)
        _uiState.update { it.copy(maxDropoffRadiusKm = clamped) }
    }

    fun selectLockMode(mode: TacticalLockMode) {
        _uiState.update { state ->
            val nextLat = when (mode) {
                TacticalLockMode.PIN_CENTER_MICRO -> state.activePin.lat
                TacticalLockMode.MERCHANT_CENTROID -> state.goldenCentroidLat
                TacticalLockMode.AUTO_LOOP_COMPASS -> state.riderLat
            }
            val nextLng = when (mode) {
                TacticalLockMode.PIN_CENTER_MICRO -> state.activePin.lng
                TacticalLockMode.MERCHANT_CENTROID -> state.goldenCentroidLng
                TacticalLockMode.AUTO_LOOP_COMPASS -> state.riderLng
            }
            state.copy(
                lockMode = mode,
                riderLat = nextLat,
                riderLng = nextLng,
                shockwaveTriggerCount = state.shockwaveTriggerCount + 1
            )
        }
        TacticalSoundEngine.playLockChime(_uiState.value.isSoundMuted)
    }

    fun toggleEngageFixPointLock() {
        _uiState.update { state ->
            val nextLocked = !state.isFixPointLocked
            val logEntry = LockDriftLog(
                id = "LOG-${System.currentTimeMillis()}",
                timestamp = currentTimeStr(),
                eventTitle = if (nextLocked) {
                    "ENGAGED ${state.lockMode.titleEn} [${state.selectedPlatform.shortTag}]"
                } else {
                    "RELEASED FIXPOINT LOCK (Manual Override)"
                },
                pinName = state.activePin.name,
                pickupDistKm = state.distanceToPinKm,
                dropoffDistKm = state.maxDropoffRadiusKm.toDouble(),
                statusTag = if (nextLocked) "LOCKED" else "STANDBY",
                isPositive = nextLocked
            )

            if (nextLocked) {
                TacticalSoundEngine.playLockChime(state.isSoundMuted)
            } else {
                TacticalSoundEngine.playOutOfZoneWarningBuzz(state.isSoundMuted)
            }

            state.copy(
                isFixPointLocked = nextLocked,
                riderLat = if (nextLocked) state.activePin.lat + 0.00012 else state.riderLat,
                riderLng = if (nextLocked) state.activePin.lng - 0.00010 else state.riderLng,
                shockwaveTriggerCount = state.shockwaveTriggerCount + 1,
                driftLogs = listOf(logEntry) + state.driftLogs.take(19)
            )
        }
    }

    fun simulateNextOrderCard() {
        val currentState = _uiState.value
        val pin = currentState.activePin

        // Generate or cycle realistic order around the currently locked Hotzone Pin
        val templates = listOf(
            Triple("สุกี้ตี๋น้อย & บาร์บีคิว", "หมู่บ้านพฤกษา วิลล์ (ซอยวัดพระเงิน)", false),
            Triple("KFC & McDonald's หน้าห้าง", "คอนโด พลัม พหลฯ-แจ้งวัฒนะ", false),
            Triple("ตลาดโต้รุ่ง & ข้าวมันไก่เจ๊หมวย", "ซอยร่วมสุข 4 (ท้ายซอย)", false),
            Triple("Starbucks Reserve & After You", "อาคารสำนักงาน ริมถนนใหญ่", false),
            Triple("ส้มตำเจ๊ไฝ & คอหมูย่างเตาถ่าน", "ข้ามสะพานพระราม 5 ไปฝั่งตัวเมือง", true),
            Triple("ชาบูหมาล่าสายพาน", "หมู่บ้านคาซ่า วิลล์ ราชพฤกษ์", false),
            Triple("กะเพราถาดยักษ์ & หมูกรอบชาชู", "จุดส่งฉีกข้ามเขต 4.8 กม.", true)
        )
        val pickIdx = (currentState.scanSequenceId) % templates.size
        val tpl = templates[pickIdx]

        // Create varied distances so rider can experience S-Rank Short-Loop, A-Rank, and Out-of-Zone Warning
        val scenarioType = currentState.scanSequenceId % 3
        val pickupRadiusOffsetKm = when (scenarioType) {
            0 -> Random.nextDouble(0.15, 0.55) // S-Rank close pickup
            1 -> Random.nextDouble(0.45, 0.95) // Medium pickup
            else -> Random.nextDouble(0.60, 1.45) // Farther pickup
        }
        val dropoffRadiusOffsetKm = when (scenarioType) {
            0 -> Random.nextDouble(0.65, 1.55) // S-Rank short-loop drop-off
            1 -> Random.nextDouble(1.40, 2.35) // Borderline drop-off
            else -> Random.nextDouble(3.10, 5.60) // Out-of-zone warning drop-off
        }

        val angle1 = Random.nextDouble(0.0, Math.PI * 2)
        val angle2 = angle1 + Random.nextDouble(0.4, 1.8)

        // 1 degree lat ~ 111 km
        val pLat = pin.lat + (pickupRadiusOffsetKm / 111.0) * sin(angle1)
        val pLng = pin.lng + (pickupRadiusOffsetKm / 108.0) * cos(angle1)
        val dLat = pin.lat + (dropoffRadiusOffsetKm / 111.0) * sin(angle2)
        val dLng = pin.lng + (dropoffRadiusOffsetKm / 108.0) * cos(angle2)

        val payout = when (scenarioType) {
            0 -> Random.nextInt(44, 62)
            1 -> Random.nextInt(48, 68)
            else -> Random.nextInt(55, 79)
        }

        val newOrder = IncomingOrderCard(
            id = "ORD-${1000 + currentState.scanSequenceId}",
            platform = if (currentState.selectedPlatform == RiderPlatform.ALL_APPS) {
                RiderPlatform.entries[currentState.scanSequenceId % 4]
            } else {
                currentState.selectedPlatform
            },
            merchantName = "${tpl.first} (${pin.zoneTag})",
            dropoffName = tpl.second,
            pickupLat = pLat,
            pickupLng = pLng,
            dropoffLat = dLat,
            dropoffLng = dLng,
            payoutBaht = payout,
            estMinutes = (dropoffRadiusOffsetKm * 6.5).roundToInt().coerceIn(7, 28),
            crossesBridgeOrZone = tpl.third && scenarioType == 2
        )

        applyEvaluatedOrder(newOrder)
    }

    fun selectPresetOrder(order: IncomingOrderCard) {
        // Re-anchor preset order offsets relative to the currently selected activePin so distances are realistic
        val basePin = defaultHotzones().first()
        val dLatP = order.pickupLat - basePin.lat
        val dLngP = order.pickupLng - basePin.lng
        val dLatD = order.dropoffLat - basePin.lat
        val dLngD = order.dropoffLng - basePin.lng
        val activePin = _uiState.value.activePin
        val relocated = order.copy(
            pickupLat = activePin.lat + dLatP,
            pickupLng = activePin.lng + dLngP,
            dropoffLat = activePin.lat + dLatD,
            dropoffLng = activePin.lng + dLngD
        )
        applyEvaluatedOrder(relocated)
    }

    fun evaluateCustomOrderInput(
        merchantName: String,
        dropoffName: String,
        pickupDistFromPinKm: Float,
        dropoffDistFromPinKm: Float,
        payoutBaht: Int,
        crossesBridge: Boolean
    ) {
        val pin = _uiState.value.activePin
        val pLat = pin.lat + (pickupDistFromPinKm / 111.0) * 0.8
        val pLng = pin.lng + (pickupDistFromPinKm / 108.0) * 0.6
        val dLat = pin.lat - (dropoffDistFromPinKm / 111.0) * 0.7
        val dLng = pin.lng + (dropoffDistFromPinKm / 108.0) * 0.71

        val customOrder = IncomingOrderCard(
            id = "CUSTOM-${100 + _uiState.value.scanSequenceId}",
            platform = _uiState.value.selectedPlatform,
            merchantName = merchantName.ifBlank { "ร้านค้ากำหนดเอง (${pin.zoneTag})" },
            dropoffName = dropoffName.ifBlank { "จุดส่งลูกค้ากำหนดเอง" },
            pickupLat = pLat,
            pickupLng = pLng,
            dropoffLat = dLat,
            dropoffLng = dLng,
            payoutBaht = payoutBaht.coerceAtLeast(30),
            estMinutes = (dropoffDistFromPinKm * 6f).roundToInt().coerceAtLeast(5),
            crossesBridgeOrZone = crossesBridge
        )
        applyEvaluatedOrder(customOrder)
    }

    private fun applyEvaluatedOrder(order: IncomingOrderCard) {
        _uiState.update { state ->
            val eval = GeoMath.evaluateOrder(
                order = order,
                pinLat = state.activePin.lat,
                pinLng = state.activePin.lng,
                maxPickupRadiusKm = state.maxPickupRadiusKm,
                maxDropoffRadiusKm = state.maxDropoffRadiusKm,
                rules = state.autoFilterRules
            )

            when (eval.grade) {
                OrderGrade.S_RANK_LOOP -> TacticalSoundEngine.playSRankMatchBeep(state.isSoundMuted)
                OrderGrade.A_RANK_IN_ZONE -> TacticalSoundEngine.playLockChime(state.isSoundMuted)
                OrderGrade.REJECT_OUT_OF_ZONE -> TacticalSoundEngine.playOutOfZoneWarningBuzz(state.isSoundMuted)
            }

            val logEntry = LockDriftLog(
                id = "SCAN-${System.currentTimeMillis()}",
                timestamp = currentTimeStr(),
                eventTitle = "${order.platform.shortTag} • ${order.merchantName}",
                pinName = state.activePin.zoneTag,
                pickupDistKm = eval.pickupToPinKm,
                dropoffDistKm = eval.dropoffToPinKm,
                statusTag = when (eval.grade) {
                    OrderGrade.S_RANK_LOOP -> "S-RANK วนหมุด"
                    OrderGrade.A_RANK_IN_ZONE -> "A-RANK ในโซน"
                    OrderGrade.REJECT_OUT_OF_ZONE -> "REJECT หลุดโซน"
                },
                isPositive = eval.grade != OrderGrade.REJECT_OUT_OF_ZONE
            )

            state.copy(
                activeOrder = order,
                scanSequenceId = state.scanSequenceId + 1,
                sRankAcceptedCount = if (eval.grade == OrderGrade.S_RANK_LOOP) state.sRankAcceptedCount + 1 else state.sRankAcceptedCount,
                outOfZoneRejectedCount = if (eval.grade == OrderGrade.REJECT_OUT_OF_ZONE) state.outOfZoneRejectedCount + 1 else state.outOfZoneRejectedCount,
                driftLogs = listOf(logEntry) + state.driftLogs.take(19)
            )
        }
    }

    fun updateAutoFilterRules(
        notifyOnlyWithinDropoff: Boolean? = null,
        blockCrossBridge: Boolean? = null,
        shortLoopPriority: Boolean? = null,
        minBahtPerKm: Float? = null
    ) {
        _uiState.update { state ->
            val current = state.autoFilterRules
            state.copy(
                autoFilterRules = current.copy(
                    notifyOnlyWithinDropoffRadius = notifyOnlyWithinDropoff ?: current.notifyOnlyWithinDropoffRadius,
                    blockCrossBridgeOrZone = blockCrossBridge ?: current.blockCrossBridgeOrZone,
                    shortLoopPriority = shortLoopPriority ?: current.shortLoopPriority,
                    minBahtPerLoopKm = minBahtPerKm ?: current.minBahtPerLoopKm
                )
            )
        }
    }

    fun selectHotzonePin(pin: HotzonePin) {
        val merchants = defaultMerchantsForPin(pin)
        val (cLat, cLng, avgDist) = GeoMath.calculateGoldenCentroid(merchants)
        _uiState.update { state ->
            val logEntry = LockDriftLog(
                id = "PIN-${System.currentTimeMillis()}",
                timestamp = currentTimeStr(),
                eventTitle = "ย้ายหมุดหลัก -> ${pin.name}",
                pinName = pin.zoneTag,
                pickupDistKm = 0.05,
                dropoffDistKm = state.maxDropoffRadiusKm.toDouble(),
                statusTag = "PIN LOCKED",
                isPositive = true
            )
            state.copy(
                activePin = pin,
                riderLat = pin.lat + 0.00015,
                riderLng = pin.lng - 0.00012,
                clusterMerchants = merchants,
                goldenCentroidLat = cLat,
                goldenCentroidLng = cLng,
                goldenCentroidAvgDistKm = avgDist,
                shockwaveTriggerCount = state.shockwaveTriggerCount + 1,
                driftLogs = listOf(logEntry) + state.driftLogs.take(19)
            )
        }
        TacticalSoundEngine.playLockChime(_uiState.value.isSoundMuted)
        // Re-center active order around the newly chosen hotzone pin
        selectPresetOrder(_uiState.value.activeOrder)
    }

    fun addCustomHotzonePin(name: String, zoneTag: String, lat: Double, lng: Double, description: String) {
        val cleanName = name.trim().ifBlank { "หมุดยุทธวิธีใหม่ #${_uiState.value.hotzonePins.size + 1}" }
        val cleanTag = zoneTag.trim().ifBlank { "โซนกำหนดเอง" }
        val newPin = HotzonePin(
            id = "CUSTOM-PIN-${System.currentTimeMillis()}",
            name = cleanName,
            zoneTag = cleanTag,
            lat = lat,
            lng = lng,
            merchantCount = 28,
            avgOrderRate = "สูง (หมุดส่วนตัว)",
            description = description.ifBlank { "หมุดพิกัดกำหนดเองสำหรับดักงานรับใกล้-ส่งใกล้หมุด" },
            isCustom = true
        )
        _uiState.update { state ->
            state.copy(hotzonePins = listOf(newPin) + state.hotzonePins)
        }
        selectHotzonePin(newPin)
    }

    fun setCurrentGpsAsActivePin() {
        val state = _uiState.value
        val gpsPin = HotzonePin(
            id = "GPS-PIN-${System.currentTimeMillis()}",
            name = "หมุดพิกัดปัจจุบัน (Live GPS Lock)",
            zoneTag = "พิกัดจริงปัจจุบัน",
            lat = state.riderLat,
            lng = state.riderLng,
            merchantCount = 32,
            avgOrderRate = "เรียลไทม์",
            description = "ดึงจากสัญญาณ GPS ปัจจุบัน (${String.format(Locale.US, "%.5f, %.5f", state.riderLat, state.riderLng)})",
            isCustom = true
        )
        _uiState.update { s ->
            s.copy(hotzonePins = listOf(gpsPin) + s.hotzonePins)
        }
        selectHotzonePin(gpsPin)
    }

    fun setPinFromMapClick(lat: Double, lng: Double) {
        val coordLabel = String.format(Locale.US, "%.4f, %.4f", lat, lng)
        val clickedPin = HotzonePin(
            id = "MAP-PIN-${System.currentTimeMillis()}",
            name = "หมุดปักบนแผนที่ ($coordLabel)",
            zoneTag = "หมุดแผนที่ $coordLabel",
            lat = lat,
            lng = lng,
            merchantCount = 40,
            avgOrderRate = "สูง (จุดปักหมุดบนแผนที่)",
            description = "ปักหมุดโดยตรงจากแผนที่ CartoDB Dark Matter ที่พิกัด $coordLabel",
            isCustom = true
        )
        _uiState.update { s ->
            val filtered = s.hotzonePins.filterNot { it.id.startsWith("MAP-PIN-") }
            s.copy(hotzonePins = listOf(clickedPin) + filtered)
        }
        selectHotzonePin(clickedPin)
    }

    fun addMerchantToCluster(name: String, category: String, offsetMetersNorth: Int, offsetMetersEast: Int) {
        val pin = _uiState.value.activePin
        val mLat = pin.lat + (offsetMetersNorth / 111000.0)
        val mLng = pin.lng + (offsetMetersEast / 108000.0)
        val newMerchant = MerchantPoint(
            id = "M-${System.currentTimeMillis()}",
            name = name.trim().ifBlank { "ร้านค้ายอดฮิต #${_uiState.value.clusterMerchants.size + 1}" },
            category = category.trim().ifBlank { "อาหารจานด่วน / คาเฟ่" },
            lat = mLat,
            lng = mLng,
            weight = 1.2f
        )
        val updatedList = _uiState.value.clusterMerchants + newMerchant
        recalculateCentroidInternal(updatedList)
        TacticalSoundEngine.playLockChime(_uiState.value.isSoundMuted)
    }

    fun removeMerchantFromCluster(merchantId: String) {
        val current = _uiState.value.clusterMerchants
        if (current.size <= 2) return // Keep at least 2 points for centroid
        val updated = current.filterNot { it.id == merchantId }
        recalculateCentroidInternal(updated)
    }

    fun snapPinToGoldenCentroid() {
        val state = _uiState.value
        val updatedPin = state.activePin.copy(
            lat = state.goldenCentroidLat,
            lng = state.goldenCentroidLng,
            description = "ปรับเข้าจุดกึ่งกลางทองคำ (Golden Centroid) เฉลี่ยห่างทุกร้าน ${GeoMath.formatKm(state.goldenCentroidAvgDistKm)} กม."
        )
        _uiState.update { s ->
            s.copy(
                activePin = updatedPin,
                riderLat = state.goldenCentroidLat,
                riderLng = state.goldenCentroidLng,
                lockMode = TacticalLockMode.MERCHANT_CENTROID,
                shockwaveTriggerCount = s.shockwaveTriggerCount + 1
            )
        }
        TacticalSoundEngine.playSRankMatchBeep(state.isSoundMuted)
    }

    private fun recalculateCentroidInternal(merchants: List<MerchantPoint>) {
        val (cLat, cLng, avgDist) = GeoMath.calculateGoldenCentroid(merchants)
        _uiState.update { state ->
            state.copy(
                clusterMerchants = merchants,
                goldenCentroidLat = cLat,
                goldenCentroidLng = cLng,
                goldenCentroidAvgDistKm = avgDist
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun bindHardwareLocationUpdates(context: Context, granted: Boolean) {
        _uiState.update { it.copy(hasLocationPermission = granted) }
        if (!granted) {
            locationService?.stopLocationUpdates()
            return
        }

        try {
            // 1. Primary: FusedLocationProviderClient + 2D GpsKalmanFilter via LocationService
            val service = locationService ?: LocationService(context).also { locationService = it }
            val intervalMs = (1000L / _uiState.value.selectedPlatform.gpsHz.coerceIn(1, 15)).coerceAtLeast(250L)
            service.startLocationUpdates(
                intervalMillis = intervalMs,
                minUpdateIntervalMillis = (intervalMs / 2).coerceAtLeast(150L)
            ) { update ->
                _uiState.update { state ->
                    val effectiveLat = if (state.isFixPointLocked) update.smoothedLat else update.rawLat
                    val effectiveLng = if (state.isFixPointLocked) update.smoothedLng else update.rawLng
                    state.copy(
                        isUsingRealHardwareGps = true,
                        riderLat = effectiveLat,
                        riderLng = effectiveLng,
                        rawUnfilteredLat = update.rawLat,
                        rawUnfilteredLng = update.rawLng,
                        accuracyMeters = update.accuracyMeters,
                        kalmanDriftSuppressedMeters = if (update.driftSuppressedMeters > 0.1f) {
                            ((update.driftSuppressedMeters * 10f).roundToInt() / 10f)
                        } else {
                            state.kalmanDriftSuppressedMeters
                        }
                    )
                }
            }

            // 2. Fallback: Hardware LocationManager for devices without Google Play Services
            val mgr = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return
            locationManager = mgr
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    _uiState.update { state ->
                        val kalman = state.lockMode.kalmanGain
                        val smoothedLat = if (state.isFixPointLocked) {
                            state.riderLat * kalman + location.latitude * (1f - kalman)
                        } else {
                            location.latitude
                        }
                        val smoothedLng = if (state.isFixPointLocked) {
                            state.riderLng * kalman + location.longitude * (1f - kalman)
                        } else {
                            location.longitude
                        }
                        state.copy(
                            isUsingRealHardwareGps = true,
                            riderLat = smoothedLat,
                            riderLng = smoothedLng,
                            rawUnfilteredLat = location.latitude,
                            rawUnfilteredLng = location.longitude,
                            accuracyMeters = location.accuracy.coerceIn(1.5f, 25.0f)
                        )
                    }
                }

                @Deprecated("Deprecated in Android API")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }
            locationListener = listener

            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            for (provider in providers) {
                if (mgr.isProviderEnabled(provider)) {
                    mgr.requestLocationUpdates(provider, 2000L, 1f, listener)
                }
            }
        } catch (_: Throwable) {
            // Fallback gracefully to tactical simulation telemetry
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            locationService?.stopLocationUpdates()
            locationListener?.let { locationManager?.removeUpdates(it) }
        } catch (_: Throwable) {}
    }

    private fun currentTimeStr(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
    }

    companion object {
        fun defaultHotzones(): List<HotzonePin> = listOf(
            HotzonePin(
                id = "HZ-BANGYAI",
                name = "โซนบางใหญ่ - เซ็นทรัลเวสต์เกต / ตลาดบางใหญ่",
                zoneTag = "บางใหญ่-เวสต์เกต",
                lat = 13.8768,
                lng = 100.4113,
                merchantCount = 145,
                avgOrderRate = "สูงมาก (18-25 งาน/ชม.)",
                description = "ดงร้านอาหารเซ็นทรัลเวสต์เกต ตลาดกลางบางใหญ่ และซอยวัดลาดปลาดุก งานสั้นวนเข้าหมู่บ้านเพียบ"
            ),
            HotzonePin(
                id = "HZ-BANGKRUAI",
                name = "โซนบางกรวย - พระราม 5 / ราชพฤกษ์",
                zoneTag = "บางกรวย-พระราม 5",
                lat = 13.8246,
                lng = 100.4485,
                merchantCount = 98,
                avgOrderRate = "สูง (14-20 งาน/ชม.)",
                description = "จุดตัดเดอะวอล์ค ราชพฤกษ์ ตลาดเจ้าพระยา และคาเฟ่ริมถนนนครอินทร์ ต้องระวังงานข้ามสะพานพระราม 5"
            ),
            HotzonePin(
                id = "HZ-TALINGCHAN",
                name = "โซนตลิ่งชัน - บรมราชชนนี / ฟู้ดวิลล่า",
                zoneTag = "ตลิ่งชัน-ฟู้ดวิลล่า",
                lat = 13.7694,
                lng = 100.4462,
                merchantCount = 120,
                avgOrderRate = "สูงมาก (16-22 งาน/ชม.)",
                description = "ศูนย์รวมร้านดัง Food Villa ราชพฤกษ์ และตลาดน้ำตลิ่งชัน ส่งวนหมู่บ้านหรูระยะไม่เกิน 2 กม."
            ),
            HotzonePin(
                id = "HZ-LAKSI",
                name = "โซนหลักสี่ - แจ้งวัฒนะ / ไอทีสแควร์",
                zoneTag = "หลักสี่-แจ้งวัฒนะ",
                lat = 13.8887,
                lng = 100.5768,
                merchantCount = 134,
                avgOrderRate = "สูงมาก (17-24 งาน/ชม.)",
                description = "ศูนย์ราชการแจ้งวัฒนะ ไอทีสแควร์ และซอยแจ้งวัฒนะ 14 คอนโดหนาแน่น ทำรอบสั้นคุ้มมาก"
            ),
            HotzonePin(
                id = "HZ-BANTHATTHONG",
                name = "โซนสยาม - บรรทัดทอง / สามย่าน",
                zoneTag = "บรรทัดทอง-สามย่าน",
                lat = 13.7423,
                lng = 100.5231,
                merchantCount = 210,
                avgOrderRate = "สูงสุด (25-35 งาน/ชม.)",
                description = "ดงสตรีทฟู้ดบรรทัดทอง-สามย่านมิตรทาวน์ ส่งวนหอพักจุฬาฯ-คอนโดพระราม 4"
            )
        )

        fun defaultMerchantsForPin(pin: HotzonePin): List<MerchantPoint> = listOf(
            MerchantPoint(
                id = "M-1",
                name = "สุกี้ตี๋น้อย & ชาบูหม้อไฟ (${pin.zoneTag})",
                category = "ชาบู / ปิ้งย่าง",
                lat = pin.lat + 0.0014,
                lng = pin.lng - 0.0011,
                weight = 1.3f
            ),
            MerchantPoint(
                id = "M-2",
                name = "KFC / McDonald's โซนหน้าห้าง",
                category = "ฟาสต์ฟู้ดยอดฮิต",
                lat = pin.lat - 0.0012,
                lng = pin.lng + 0.0015,
                weight = 1.2f
            ),
            MerchantPoint(
                id = "M-3",
                name = "ข้าวมันไก่ตอน & กะเพราถาดเตาถ่าน",
                category = "สตรีทฟู้ดสั่งบ่อย",
                lat = pin.lat + 0.0009,
                lng = pin.lng + 0.0012,
                weight = 1.1f
            ),
            MerchantPoint(
                id = "M-4",
                name = "Amazon Cafe & ชานมไข่มุก",
                category = "เครื่องดื่ม / คาเฟ่",
                lat = pin.lat - 0.0010,
                lng = pin.lng - 0.0013,
                weight = 1.0f
            )
        )

        fun defaultIncomingOrders(): List<IncomingOrderCard> {
            val pin = defaultHotzones().first()
            return listOf(
                IncomingOrderCard(
                    id = "ORD-S901",
                    platform = RiderPlatform.LINE_MAN,
                    merchantName = "ข้าวมันไก่เจ๊หมวย & กะเพราถาด (ตลาดบางใหญ่)",
                    dropoffName = "คอนโด พลัม เซ็นทรัล สเตชั่น (เฟส 2)",
                    pickupLat = pin.lat + 0.0022,  // ~0.28 km from pin
                    pickupLng = pin.lng + 0.0015,
                    dropoffLat = pin.lat - 0.0078, // ~1.05 km from pin
                    dropoffLng = pin.lng + 0.0056,
                    payoutBaht = 48,
                    estMinutes = 9,
                    crossesBridgeOrZone = false
                ),
                IncomingOrderCard(
                    id = "ORD-A902",
                    platform = RiderPlatform.GRAB,
                    merchantName = "บาร์บีคิวพลาซ่า & บอนชอน (เวสต์เกต ชั้น 3)",
                    dropoffName = "หมู่บ้านพฤกษาวิลล์ ซอยวัดลาดปลาดุก",
                    pickupLat = pin.lat - 0.0045,  // ~0.58 km from pin
                    pickupLng = pin.lng - 0.0031,
                    dropoffLat = pin.lat + 0.0135, // ~1.82 km from pin
                    dropoffLng = pin.lng - 0.0098,
                    payoutBaht = 58,
                    estMinutes = 14,
                    crossesBridgeOrZone = false
                ),
                IncomingOrderCard(
                    id = "ORD-R903",
                    platform = RiderPlatform.SHOPEE_FOOD,
                    merchantName = "ส้มตำปูปลาร้ารสเด็ด & ไก่ย่างวิเชียรบุรี",
                    dropoffName = "ข้ามสะพานพระราม 5 -> ท่าน้ำนนทบุรี (ออกนอกโซน)",
                    pickupLat = pin.lat + 0.0052,  // ~0.64 km from pin
                    pickupLng = pin.lng - 0.0030,
                    dropoffLat = pin.lat - 0.0390, // ~5.35 km from pin (Out of zone!)
                    dropoffLng = pin.lng + 0.0310,
                    payoutBaht = 69,
                    estMinutes = 26,
                    crossesBridgeOrZone = true
                )
            )
        }

        fun defaultSatellites(): List<SatelliteSnr> = listOf(
            SatelliteSnr("GPS (USA)", "G04", 46, true, CrimsonRed),
            SatelliteSnr("GPS (USA)", "G12", 43, true, CrimsonRed),
            SatelliteSnr("GPS (USA)", "G24", 41, true, CrimsonRed),
            SatelliteSnr("GLONASS", "R08", 39, true, EmeraldSRank),
            SatelliteSnr("GLONASS", "R17", 37, true, EmeraldSRank),
            SatelliteSnr("Galileo", "E11", 44, true, CyanTelemetry),
            SatelliteSnr("Galileo", "E26", 40, true, CyanTelemetry),
            SatelliteSnr("BeiDou", "B19", 45, true, AmberWarning),
            SatelliteSnr("BeiDou", "B32", 38, true, AmberWarning)
        )

        fun defaultDriftLogs(): List<LockDriftLog> = listOf(
            LockDriftLog(
                id = "LOG-1",
                timestamp = "18:42:10",
                eventTitle = "LM • ข้าวมันไก่เจ๊หมวย -> คอนโดพลัม",
                pinName = "บางใหญ่-เวสต์เกต",
                pickupDistKm = 0.28,
                dropoffDistKm = 1.05,
                statusTag = "S-RANK วนหมุด",
                isPositive = true
            ),
            LockDriftLog(
                id = "LOG-2",
                timestamp = "18:35:44",
                eventTitle = "SPF • ส้มตำปูปลาร้า -> ข้ามสะพานพระราม 5",
                pinName = "บางใหญ่-เวสต์เกต",
                pickupDistKm = 0.64,
                dropoffDistKm = 5.35,
                statusTag = "REJECT หลุดโซน",
                isPositive = false
            ),
            LockDriftLog(
                id = "LOG-3",
                timestamp = "18:20:15",
                eventTitle = "ENGAGED Pin-Center Micro Lock [10Hz]",
                pinName = "บางใหญ่-เวสต์เกต",
                pickupDistKm = 0.03,
                dropoffDistKm = 2.00,
                statusTag = "LOCKED ±1.8m",
                isPositive = true
            )
        )
    }
}
