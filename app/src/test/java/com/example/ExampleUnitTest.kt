package com.example

import com.example.model.CoordinateFormat
import com.example.model.GeoMath
import com.example.model.MeasurementUnit
import com.example.model.SavedLocationItem
import com.example.service.GpsKalmanFilter
import org.junit.Assert.assertEquals
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
}
