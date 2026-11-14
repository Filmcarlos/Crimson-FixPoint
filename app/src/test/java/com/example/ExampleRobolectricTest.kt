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
    fun `test all screens and interactive buttons end to end`() {
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
        composeTestRule.onNodeWithTag("readout_latitude").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("readout_longitude").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("metric_accuracy").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("metric_altitude").performScrollTo().assertIsDisplayed()

        // 2. Deliver a real Location fix from sensor and verify Copy, Share, Map, and Save buttons
        val gpsFix = Location("gps").apply {
            latitude = 13.756331
            longitude = 100.501762
            accuracy = 2.8f
            altitude = 18.0
            speed = 4.5f
            bearing = 90f
            time = System.currentTimeMillis()
        }
        viewModel.onHardwareSensorFixReceived(gpsFix)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("btn_copy_coordinates").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // Open Save Location Panel and save a location
        composeTestRule.onNodeWithTag("btn_save_location").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("dialog_save_location").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("input_save_location_name").performScrollTo().performTextInput("จุดทดสอบระบบ")
        composeTestRule.onNodeWithTag("btn_confirm_save_location").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, viewModel.uiState.value.savedLocations.size)
        val savedId = viewModel.uiState.value.savedLocations.first().id

        // 3. Navigate to Saved Locations Screen and test Copy & Delete buttons
        composeTestRule.onNodeWithTag("nav_tab_saved_locations").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("saved_location_card_$savedId").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_copy_saved_$savedId").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("btn_delete_saved_$savedId").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertTrue(viewModel.uiState.value.savedLocations.isEmpty())

        // 4. Navigate to Settings Screen and test Unit, Frequency, Coordinate Format, and Keep Screen On buttons
        composeTestRule.onNodeWithTag("nav_tab_settings").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("setting_unit_imperial").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.model.MeasurementUnit.IMPERIAL, viewModel.uiState.value.measurementUnit)

        composeTestRule.onNodeWithTag("setting_freq_high_precision").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.model.UpdateFrequency.HIGH_PRECISION, viewModel.uiState.value.updateFrequency)

        composeTestRule.onNodeWithTag("setting_coord_dms").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertEquals(com.example.model.CoordinateFormat.DMS, viewModel.uiState.value.coordinateFormat)

        val initialKeepOn = viewModel.uiState.value.keepScreenOn
        composeTestRule.onNodeWithTag("setting_keep_screen_on_toggle").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        assertFalse(viewModel.uiState.value.keepScreenOn == initialKeepOn)
    }
}
