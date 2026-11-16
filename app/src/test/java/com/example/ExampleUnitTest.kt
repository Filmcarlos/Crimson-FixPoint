package com.example

import com.example.model.CoordinateFormat
import com.example.model.CustomExpenseItem
import com.example.model.DailyBreakEvenState
import com.example.model.DeliveryStopItem
import com.example.model.FuelEstimateState
import com.example.model.GeoMath
import com.example.model.JobIncomeEntry
import com.example.model.MeasurementUnit
import com.example.model.RouteOptimizer
import com.example.model.SavedLocationItem
import com.example.model.StopPoiCategory
import com.example.model.StopPoiItem
import com.example.service.GpsKalmanFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class ExampleUnitTest {
    @Test
    fun coordinateAndUnitConversions_areAccurate() {
        val lat = 13.756331
        val lng = 100.501762

        val decStr = GeoMath.formatCoordinatePair(lat, lng, CoordinateFormat.DECIMAL)
        assertEquals("13.756331, 100.501762", decStr)

        val dmsStr = GeoMath.formatCoordinatePair(lat, lng, CoordinateFormat.DMS)
        assertTrue(dmsStr.contains("13°45'"))
        assertTrue(dmsStr.contains("100°30'"))

        val metricSpeed = GeoMath.formatSpeed(10f, MeasurementUnit.METRIC)
        assertEquals("36.0 กม./ชม.", metricSpeed)

        val imperialAlt = GeoMath.formatAltitude(100.0, MeasurementUnit.IMPERIAL)
        assertEquals("328.1 ฟุต", imperialAlt)
    }

    @Test
    fun coordinateParsingAndDistanceMath_workCorrectly() {
        val parsed = GeoMath.parseCoordinateInput("พิกัด: 13.756331, 100.501762")
        assertNotNull(parsed)
        assertEquals(13.756331, parsed!!.first, 1e-6)
        assertEquals(100.501762, parsed.second, 1e-6)

        assertNull(GeoMath.parseCoordinateInput("ไม่มีตัวเลข"))

        val distMeters = GeoMath.haversineMeters(13.7500, 100.5000, 13.7600, 100.5000)
        assertTrue(distMeters in 1100.0..1125.0)
    }

    @Test
    fun savedLocationSerialization_roundTripsCleanly() {
        val item = SavedLocationItem(
            id = "LOC-101",
            name = "จุดรับสัญญาณหลัก",
            latitude = 13.7563,
            longitude = 100.5018,
            accuracyMeters = 2.4f,
            altitudeMeters = 14.5,
            timestampMillis = 1700000000000L
        )
        val rawJson = SavedLocationItem.serializeList(listOf(item))
        val restored = SavedLocationItem.deserializeList(rawJson)
        assertEquals(1, restored.size)
        assertEquals("จุดรับสัญญาณหลัก", restored[0].name)
        assertEquals(13.7563, restored[0].latitude, 1e-4)
        assertEquals(100.5018, restored[0].longitude, 1e-4)
    }

    @Test
    fun gpsKalmanFilter_dampensSuddenCoordinateSpikes() {
        val filter = GpsKalmanFilter(processNoiseMetersPerSec = 2.0f)
        val anchorLat = 13.8768
        val anchorLng = 100.4113

        val (initLat, initLng) = filter.process(anchorLat, anchorLng, accuracyMeters = 2.5f, timestampMillis = 1000L)
        assertEquals(anchorLat, initLat, 1e-6)
        assertEquals(anchorLng, initLng, 1e-6)

        val spikeLat = anchorLat + 0.0020
        val spikeLng = anchorLng + 0.0020
        val (smoothedLat, smoothedLng) = filter.process(spikeLat, spikeLng, accuracyMeters = 18.0f, timestampMillis = 2000L)

        assertTrue(abs(smoothedLat - anchorLat) < abs(spikeLat - anchorLat) * 0.25)
        assertTrue(abs(smoothedLng - anchorLng) < abs(spikeLng - anchorLng) * 0.25)
    }

    @Test
    fun routeOptimizer_nearestNeighborAndTwoOpt_ordersShortestHaversineRoute() {
        val startLat = 13.7500
        val startLng = 100.5000
        // Create stops out of order along a north-bound line
        val stops = listOf(
            DeliveryStopItem(id = "S3", name = "จุดที่ไกลสุด", latitude = 13.7900, longitude = 100.5000),
            DeliveryStopItem(id = "S1", name = "จุดที่ใกล้สุด", latitude = 13.7550, longitude = 100.5000),
            DeliveryStopItem(id = "S2", name = "จุดกลาง", latitude = 13.7700, longitude = 100.5000)
        )

        val result = RouteOptimizer.optimizeStops(startLat, startLng, stops)
        assertEquals(3, result.orderedStops.size)
        assertEquals("S1", result.orderedStops[0].id)
        assertEquals(1, result.orderedStops[0].sequenceOrder)
        assertEquals("S2", result.orderedStops[1].id)
        assertEquals(2, result.orderedStops[1].sequenceOrder)
        assertEquals("S3", result.orderedStops[2].id)
        assertEquals(3, result.orderedStops[2].sequenceOrder)
        assertTrue(result.totalPendingDistanceKm > 4.0 && result.totalPendingDistanceKm < 5.0)
    }

    @Test
    fun dailyBreakEvenAndFuelEstimator_calculateCorrectly() {
        val breakEven = DailyBreakEvenState(
            fuelCostBaht = 100.0,
            customExpenses = listOf(CustomExpenseItem("E1", "ค่าเน็ต", 50.0)),
            jobIncomes = listOf(
                JobIncomeEntry("J1", "งาน 1", 110.0, 1000L),
                JobIncomeEntry("J2", "งาน 2", 80.0, 2000L)
            )
        )
        assertEquals(150.0, breakEven.totalCostBaht, 0.01)
        assertEquals(190.0, breakEven.totalIncomeBaht, 0.01)
        assertTrue(breakEven.hasReachedBreakEven)
        assertEquals(40.0, breakEven.netProfitBaht, 0.01)

        val fuel = FuelEstimateState(
            tankCapacityLiters = 5.0f,
            consumptionKmPerLiter = 40.0f,
            distanceDrivenSinceRefuelKm = 180.0,
            lowFuelWarningLiters = 1.0f
        )
        assertEquals(4.5f, fuel.usedLiters, 0.05f)
        assertEquals(0.5f, fuel.remainingLiters, 0.05f)
        assertEquals(20.0f, fuel.estimatedRemainingRangeKm, 0.5f)
        assertTrue(fuel.isLowFuel)

        val poi = StopPoiItem("P1", "ปั๊ม ปตท.", StopPoiCategory.FUEL, 13.75, 100.50, true, 1000L)
        val restoredPoi = StopPoiItem.deserializeList(StopPoiItem.serializeList(listOf(poi)))
        assertEquals(1, restoredPoi.size)
        assertEquals("ปั๊ม ปตท.", restoredPoi[0].name)
        assertFalse(restoredPoi.isEmpty())
    }
}
