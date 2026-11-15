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
    fun `test all screens pinning radius geofence alerts and interactive buttons end to end`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val viewModel = FixPointViewModel()
        viewModel.initialize(context)

        composeTestRule.setContent {
            MyApplicationTheme {
                CrimsonFixPointApp(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // 1. Verify Main Screen & Central Power Button exist
        composeTestRule.onNodeWithTag("main_power_toggle_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("zone_status_banner_panel").performScrollTo().assertIsDisplayed()
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

        // 3. Navigate to Pin & Zone Screen (หมุดและโซน) and test Leaflet Map, OSM Attribution, Radius Presets & Paste Coordinates
        composeTestRule.onNodeWithTag("nav_tab_saved_locations").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("interactive_zone_map_canvas").assertIsDisplayed()
        composeTestRule.onNodeWithTag("osm_attribution_badge").assertIsDisplayed()

        composeTestRule.onNodeWithTag("btn_radius_preset_1").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(1.0f, viewModel.uiState.value.zoneRadiusKm, 0.01f)

        composeTestRule.onNodeWithTag("btn_radius_preset_10").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(10.0f, viewModel.uiState.value.zoneRadiusKm, 0.01f)

        // Set radius to 1.0 km and verify radius input & confirm button are visible and clickable
        composeTestRule.onNodeWithTag("btn_radius_preset_1").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("input_zone_radius_km").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_apply_radius_input").performScrollTo().assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()

        // Pin Method 3: Paste Coordinates to add a second pin and select between pins
        composeTestRule.onNodeWithTag("btn_open_paste_coords").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("input_paste_coordinates").performScrollTo().performTextInput("13.756331, 100.501762")
        composeTestRule.onNodeWithTag("input_paste_pin_name").performScrollTo().performTextInput("หมุดวางพิกัด")
        composeTestRule.onNodeWithTag("btn_confirm_paste_new_pin").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(2, viewModel.uiState.value.savedLocations.size)

        // Delete the newly added pin so 1 active pin remains at (13.756331, 100.501762)
        val firstPinId = viewModel.uiState.value.savedLocations.first().id
        composeTestRule.onNodeWithTag("btn_delete_saved_$firstPinId").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, viewModel.uiState.value.savedLocations.size)

        // 4. Test Out-of-Zone Alert & Mute Button on Home Screen, then Return-to-Zone
        composeTestRule.onNodeWithTag("nav_tab_home").performClick()
        composeTestRule.waitForIdle()

        // Deliver a real GPS fix ~3.5 km away (outside the 1.0 km zone!)
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

        // Click Mute Alert button ("ปิดเสียงเตือน")
        composeTestRule.onNodeWithTag("btn_mute_zone_alert").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue(viewModel.uiState.value.isZoneAlertMuted)

        // Deliver a real GPS fix back inside the 1.0 km zone ("กลับเข้าโซนแล้ว")
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

        // 5. Navigate to Settings Screen and test Vibration, Sound, Alert Tone, Volume, GPS Buffer, and Unit settings
        composeTestRule.onNodeWithTag("nav_tab_settings").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("setting_tone_high_pulse").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(ZoneAlertTone.HIGH_PULSE, viewModel.uiState.value.alertTone)

        composeTestRule.onNodeWithTag("btn_vol_50").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(50, viewModel.uiState.value.alertVolumePercent)

        composeTestRule.onNodeWithTag("btn_buffer_50").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(50, viewModel.uiState.value.gpsBufferMeters)

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
