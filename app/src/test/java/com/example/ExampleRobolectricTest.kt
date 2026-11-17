package com.example

import android.content.Context
import android.location.Location
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.model.CoordinateFormat
import com.example.model.MeasurementUnit
import com.example.model.RainLeadTimeOption
import com.example.model.StopPoiCategory
import com.example.model.UpdateFrequency
import com.example.model.ZoneAlertTone
import com.example.model.ZoneMonitorStatus
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.FixPointViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Crimson FixPoint", appName)
    }

    @Test
    fun `test all 4 tabs pinning radius geofence alerts route optimizer breakeven fuel poi and weather end to end`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val viewModel = FixPointViewModel()
        viewModel.initialize(context)

        composeTestRule.setContent {
            MyApplicationTheme {
                CrimsonFixPointApp(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // 1. Verify Main Screen, Central Power Button, and Weather Rain Forecast Card exist
        composeTestRule.onNodeWithTag("main_power_toggle_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("zone_status_banner_panel").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("weather_rain_forecast_card").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("readout_latitude").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("readout_longitude").performScrollTo().assertIsDisplayed()

        // 2. Deliver a real Location fix from sensor and pin current GPS location
        val gpsFix = Location("gps").apply {
            latitude = 13.756331
            longitude = 100.501762
            accuracy = 2.5f
            altitude = 18.0
            speed = 4.5f
            bearing = 90f
            time = System.currentTimeMillis()
        }
        viewModel.onHardwareSensorFixReceived(gpsFix)
        composeTestRule.waitForIdle()

        // Pin Method 1: Save current GPS location from Home Screen
        composeTestRule.onNodeWithTag("btn_save_location").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_save_location").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_save_location_name").performScrollTo().performTextInput("โซนสยาม")
        composeTestRule.onNodeWithTag("btn_confirm_save_location").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, viewModel.uiState.value.savedLocations.size)
        assertEquals(ZoneMonitorStatus.INSIDE_ZONE, viewModel.uiState.value.zoneStatus)

        // 3. Navigate to Pin & Zone Screen (หมุดและโซน) and test Leaflet Map, OSM Attribution, Radius Presets & Fuel/Rest POIs
        composeTestRule.onNodeWithTag("nav_tab_saved_locations").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("interactive_zone_map_canvas").assertIsDisplayed()
        composeTestRule.onNodeWithTag("osm_attribution_badge").assertIsDisplayed()

        composeTestRule.onNodeWithTag("btn_radius_preset_1").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(1.0f, viewModel.uiState.value.zoneRadiusKm, 0.01f)

        composeTestRule.onNodeWithTag("input_zone_radius_km").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_apply_radius_input").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // 4. Navigate to 4th Tab: งานและต้นทุน (WORK_AND_COST) and test Route Optimizer, Daily Break-Even, and Fuel Estimator
        composeTestRule.onNodeWithTag("nav_tab_work_and_cost").performClick()
        composeTestRule.waitForIdle()

        // Add delivery stops and run Nearest Neighbor + 2-opt route optimization
        viewModel.addDeliveryStop("จุดส่งที่ 1 (ไกล)", 13.7900, 100.5017)
        viewModel.addDeliveryStop("จุดส่งที่ 2 (ใกล้)", 13.7600, 100.5017)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_optimize_delivery_route").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals("จุดส่งที่ 2 (ใกล้)", viewModel.uiState.value.deliveryStops.first().name)

        // Mark first stop delivered
        val firstStopId = viewModel.uiState.value.deliveryStops.first().id
        composeTestRule.onNodeWithTag("btn_toggle_delivered_$firstStopId").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue(viewModel.uiState.value.deliveryStops.first { it.id == firstStopId }.isDelivered)

        // Test Daily Break-Even & Profit
        viewModel.updateFixedDailyCosts(100.0)
        viewModel.addCustomExpense("ค่าทางด่วน", 50.0)
        viewModel.addJobIncome("งานส่งด่วน #1", 200.0)
        composeTestRule.waitForIdle()
        assertTrue(viewModel.uiState.value.breakEvenState.hasReachedBreakEven)
        assertEquals(50.0, viewModel.uiState.value.breakEvenState.netProfitBaht, 0.01)

        // Test Fuel Estimator & Refuel button
        viewModel.updateFuelSettings(tankCapacityLiters = 5.5f, consumptionKmPerLiter = 40.0f, lowFuelWarningLiters = 1.0f)
        composeTestRule.onNodeWithTag("btn_refuel_full_tank").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(0.0, viewModel.uiState.value.fuelState.distanceDrivenSinceRefuelKm, 0.001)

        // 5. Test Out-of-Zone Alert & Mute Button on Home Screen, then Return-to-Zone
        composeTestRule.onNodeWithTag("nav_tab_home").performClick()
        composeTestRule.waitForIdle()

        val outsideFix = Location("gps").apply {
            latitude = 13.795000
            longitude = 100.501762
            accuracy = 2.0f
            time = System.currentTimeMillis() + 5000L
        }
        viewModel.onHardwareSensorFixReceived(outsideFix)
        composeTestRule.waitForIdle()

        assertEquals(ZoneMonitorStatus.OUTSIDE_ZONE, viewModel.uiState.value.zoneStatus)
        assertTrue(viewModel.uiState.value.excessDistanceMeters > 100.0)

        composeTestRule.onNodeWithTag("btn_mute_zone_alert").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue(viewModel.uiState.value.isZoneAlertMuted)

        val returnFix = Location("gps").apply {
            latitude = 13.756331
            longitude = 100.501762
            accuracy = 1.5f
            time = System.currentTimeMillis() + 15000L
        }
        viewModel.onHardwareSensorFixReceived(returnFix)
        composeTestRule.waitForIdle()
        assertEquals(ZoneMonitorStatus.INSIDE_ZONE, viewModel.uiState.value.zoneStatus)
        assertFalse(viewModel.uiState.value.isZoneAlertMuted)

        // 6. Navigate to Settings Screen and test Zone Alert, Unit, and Rain Alert Settings
        composeTestRule.onNodeWithTag("nav_tab_settings").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("setting_tone_high_pulse").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(ZoneAlertTone.HIGH_PULSE, viewModel.uiState.value.alertTone)

        composeTestRule.onNodeWithTag("setting_rain_lead_60").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(RainLeadTimeOption.MIN_60, viewModel.uiState.value.rainForecast.leadTimeOption)

        composeTestRule.onNodeWithTag("btn_rain_prob_70").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(70, viewModel.uiState.value.rainForecast.probabilityThresholdPercent)

        composeTestRule.onNodeWithTag("setting_unit_imperial").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(MeasurementUnit.IMPERIAL, viewModel.uiState.value.measurementUnit)

        composeTestRule.onNodeWithTag("setting_freq_high_precision").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(UpdateFrequency.HIGH_PRECISION, viewModel.uiState.value.updateFrequency)

        composeTestRule.onNodeWithTag("setting_coord_dms").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(CoordinateFormat.DMS, viewModel.uiState.value.coordinateFormat)
    }
}
