package com.example

import com.example.model.AutoFilterRules
import com.example.model.GeoMath
import com.example.model.IncomingOrderCard
import com.example.model.MerchantPoint
import com.example.model.OrderGrade
import com.example.model.RiderPlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun haversineAndOrderEvaluation_areAccurate() {
        val pinLat = 13.8768
        val pinLng = 100.4113
        val dist = GeoMath.haversineKm(pinLat, pinLng, 13.8790, 100.4130)
        assertTrue(dist in 0.2..0.4)

        val sRankOrder = IncomingOrderCard(
            id = "TEST-1",
            platform = RiderPlatform.LINE_MAN,
            merchantName = "Test Merchant",
            dropoffName = "Test Dropoff",
            pickupLat = pinLat + 0.002,
            pickupLng = pinLng + 0.001,
            dropoffLat = pinLat - 0.006,
            dropoffLng = pinLng + 0.004,
            payoutBaht = 50,
            estMinutes = 9
        )

        val eval = GeoMath.evaluateOrder(
            order = sRankOrder,
            pinLat = pinLat,
            pinLng = pinLng,
            maxPickupRadiusKm = 0.8f,
            maxDropoffRadiusKm = 2.0f,
            rules = AutoFilterRules()
        )
        assertEquals(OrderGrade.S_RANK_LOOP, eval.grade)
        assertTrue(eval.isPickupWithinRadius)
        assertTrue(eval.isDropoffWithinRadius)
    }

    @Test
    fun goldenCentroid_computesAveragePoint() {
        val merchants = listOf(
            MerchantPoint("1", "A", "Food", 13.8700, 100.4100, 1.0f),
            MerchantPoint("2", "B", "Food", 13.8800, 100.4200, 1.0f)
        )
        val (cLat, cLng, avgDist) = GeoMath.calculateGoldenCentroid(merchants)
        assertEquals(13.8750, cLat, 0.0001)
        assertEquals(100.4150, cLng, 0.0001)
        assertTrue(avgDist > 0.0)
    }
}
