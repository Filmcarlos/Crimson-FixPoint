package com.example

import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.location.Location
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.example.ai.MlKitOrderOcrEngine
import com.example.ai.OcrScanOutcome
import com.example.model.AppScreen
import com.example.model.EarningsPeriod
import com.example.model.EarningsRecordType
import com.example.model.JobOfferSource
import com.example.model.JobOfferStatus
import com.example.model.JobRankingMode
import com.example.model.OrderInputSource
import com.example.model.PinCategory
import com.example.model.ScreenReaderState
import com.example.model.SecondaryScreen
import com.example.model.ZoneMonitorStatus
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.FixPointViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        assertEquals("Crimson FixPoint", context.getString(R.string.app_name))
    }

    @Test
    fun `test 5 destination navigation secondary menu job radar map zoom regression and work zone`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val viewModel = FixPointViewModel()
        viewModel.initialize(context)

        composeTestRule.setContent {
            MyApplicationTheme {
                CrimsonFixPointApp(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // 1. Home Dashboard starts clean: Hero card visible, NO "ไม่มีหมุดโซน" alert banner
        assertEquals(AppScreen.HOME, viewModel.uiState.value.currentScreen)
        composeTestRule.onNodeWithTag("dashboard_earnings_hero_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("dashboard_active_zone_card").assertDoesNotExist()

        // Start work session from Home Dashboard
        composeTestRule.onNodeWithTag("btn_toggle_work_session").performClick()
        composeTestRule.waitForIdle()
        assertNotNull(viewModel.uiState.value.activeWorkSession)

        // Deliver real GPS fix, pin current location, verify inside-zone then out-of-zone + mute
        val gpsInside = Location("gps").apply {
            latitude = 13.756331
            longitude = 100.501762
            accuracy = 2.5f
            speed = 5.0f
            time = System.currentTimeMillis()
        }
        viewModel.onHardwareSensorFixReceived(gpsInside)
        assertTrue(viewModel.saveCurrentLocation("โซนสยาม", false, PinCategory.FIXPOINT))
        viewModel.setZoneRadiusKm(1.0f)
        composeTestRule.waitForIdle()
        assertEquals(ZoneMonitorStatus.INSIDE_ZONE, viewModel.uiState.value.zoneStatus)
        composeTestRule.onNodeWithTag("dashboard_active_zone_card").assertIsDisplayed()

        val gpsOutside = Location("gps").apply {
            latitude = 13.795000
            longitude = 100.501762
            accuracy = 2.0f
            time = System.currentTimeMillis() + 5000L
        }
        viewModel.onHardwareSensorFixReceived(gpsOutside)
        composeTestRule.waitForIdle()
        assertEquals(ZoneMonitorStatus.OUTSIDE_ZONE, viewModel.uiState.value.zoneStatus)
        composeTestRule.onNodeWithTag("btn_dashboard_mute_zone").performClick()
        composeTestRule.waitForIdle()
        assertTrue(viewModel.uiState.value.isZoneAlertMuted)

        // 2. Navigate to Job Radar (Destination 2) — verify empty state, then ingest real text, rank, and accept
        composeTestRule.onNodeWithTag("nav_tab_job_radar").performClick()
        composeTestRule.waitForIdle()
        assertEquals(AppScreen.JOB_RADAR, viewModel.uiState.value.currentScreen)
        composeTestRule.onNodeWithTag("job_radar_hero_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("job_radar_screen_list")
            .performScrollToNode(hasTestTag("job_radar_empty_state"))
        composeTestRule.onNodeWithTag("job_radar_empty_state").assertIsDisplayed()

        // Ingest two real offers from different platforms and test ranking modes
        assertTrue(
            viewModel.ingestJobOfferText(
                rawText = "LINE MAN ร้านเจ๊ไฝ ฿120 4.0 กม. ไปรับ 1.2 กม. 20 นาที",
                source = JobOfferSource.NOTIFICATION_LISTENER,
                title = "LINE MAN"
            )
        )
        assertTrue(
            viewModel.ingestJobOfferText(
                rawText = "GrabFood ร้านใกล้ ฿50 3.0 กม. ไปรับ 0.2 กม. 18 นาที",
                source = JobOfferSource.MANUAL_OR_SHARED,
                title = "GrabFood"
            )
        )
        composeTestRule.waitForIdle()
        assertEquals(2, viewModel.uiState.value.rankedActiveJobOffers.size)
        assertEquals("ร้านเจ๊ไฝ", viewModel.uiState.value.rankedActiveJobOffers.first().pickupName)

        // Scroll back to hero card and switch ranking to Nearest First -> closer pickup ranks first
        composeTestRule.onNodeWithTag("job_radar_screen_list")
            .performScrollToNode(hasTestTag("job_radar_hero_card"))
        composeTestRule.onNodeWithTag("radar_mode_nearest_first").performClick()
        composeTestRule.waitForIdle()
        assertEquals(JobRankingMode.NEAREST_FIRST, viewModel.uiState.value.jobRankingMode)
        assertEquals("ร้านใกล้", viewModel.uiState.value.rankedActiveJobOffers.first().pickupName)

        // Switch back to Smart Best Job and accept top offer -> logs to earnings & order history
        composeTestRule.onNodeWithTag("radar_mode_smart_best_job").performClick()
        composeTestRule.waitForIdle()
        val topOfferId = viewModel.uiState.value.rankedActiveJobOffers.first().id
        composeTestRule.onNodeWithTag("job_radar_screen_list")
            .performScrollToNode(hasTestTag("btn_radar_accept_$topOfferId"))
        composeTestRule.onNodeWithTag("btn_radar_accept_$topOfferId").performClick()
        composeTestRule.waitForIdle()
        assertTrue(
            viewModel.uiState.value.detectedJobOffers.any {
                it.id == topOfferId && it.status == JobOfferStatus.ACCEPTED_LOGGED
            }
        )

        // 3. Navigate to Live Map / Work Zone (Destination 3) — verify OSM map, Work Zone UI & zero tap-through on + / -
        composeTestRule.onNodeWithTag("nav_tab_live_map").performClick()
        composeTestRule.waitForIdle()
        assertEquals(AppScreen.LIVE_MAP, viewModel.uiState.value.currentScreen)
        composeTestRule.onNodeWithTag("smart_map_osm_canvas").assertIsDisplayed()
        composeTestRule.onNodeWithTag("osm_attribution_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("work_zone_control_panel").assertIsDisplayed()

        // Regression test: tapping +, -, and auto-fit MUST NOT bleed through to drop or move pins
        val pinsBeforeZoom = viewModel.uiState.value.savedLocations.size
        val activeLatBeforeZoom = viewModel.uiState.value.activePin?.latitude
        composeTestRule.onNodeWithTag("btn_map_zoom_in").performClick()
        composeTestRule.onNodeWithTag("btn_map_zoom_out").performClick()
        composeTestRule.onNodeWithTag("btn_map_auto_fit").performClick()
        composeTestRule.waitForIdle()
        assertEquals(pinsBeforeZoom, viewModel.uiState.value.savedLocations.size)
        assertEquals(activeLatBeforeZoom, viewModel.uiState.value.activePin?.latitude)

        // 4. Navigate to Earnings (Destination 4)
        composeTestRule.onNodeWithTag("nav_tab_earnings").performClick()
        composeTestRule.waitForIdle()
        assertEquals(AppScreen.EARNINGS, viewModel.uiState.value.currentScreen)
        composeTestRule.onNodeWithTag("earnings_summary_hero_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("earnings_period_weekly").performClick()
        composeTestRule.waitForIdle()
        assertEquals(EarningsPeriod.WEEKLY, viewModel.uiState.value.smartEarningsState.selectedPeriod)

        // 5. Navigate to AI Assistant (Destination 5) & verify Copilot + Screen Reader HUD
        composeTestRule.onNodeWithTag("nav_tab_ai_assistant").performClick()
        composeTestRule.waitForIdle()
        assertEquals(AppScreen.AI_ASSISTANT, viewModel.uiState.value.currentScreen)
        composeTestRule.onNodeWithTag("screen_reader_control_card").assertIsDisplayed()

        // 6. Open Secondary Menu drawer -> navigate to Cloud Sync, Job History, Membership, Profile
        composeTestRule.onNodeWithTag("btn_open_secondary_menu").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("secondary_menu_cloud_sync").performClick()
        composeTestRule.waitForIdle()
        assertEquals(SecondaryScreen.CLOUD_SYNC, viewModel.uiState.value.secondaryScreen)
        composeTestRule.onNodeWithTag("secondary_cloud_sync_screen").assertIsDisplayed()

        composeTestRule.onNodeWithTag("btn_open_secondary_menu").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("secondary_menu_profile").performClick()
        composeTestRule.waitForIdle()
        assertEquals(SecondaryScreen.PROFILE, viewModel.uiState.value.secondaryScreen)
        composeTestRule.onNodeWithTag("profile_settings_screen_list")
            .performScrollToNode(hasTestTag("btn_save_rider_profile"))
        composeTestRule.onNodeWithTag("btn_save_rider_profile").assertIsDisplayed()

        // Return from Secondary screen back to main tab
        composeTestRule.onNodeWithTag("btn_back_from_secondary").performClick()
        composeTestRule.waitForIdle()
        assertNull(viewModel.uiState.value.secondaryScreen)

        // Stop work session cleanly
        viewModel.stopWorkSession()
        assertTrue(viewModel.uiState.value.activeWorkSession == null)
    }

    @Test
    fun `test screen reader consent permission denied ocr failure protected screen and manual order edit save`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val viewModel = FixPointViewModel()
        viewModel.initialize(context)

        composeTestRule.setContent {
            MyApplicationTheme {
                CrimsonFixPointApp(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // Navigate to AI Assistant
        composeTestRule.onNodeWithTag("nav_tab_ai_assistant").performClick()
        composeTestRule.waitForIdle()

        // 1. Clicking Start Screen Reader before consent MUST open explicit consent dialog
        composeTestRule.onNodeWithTag("btn_start_screen_reader").performClick()
        composeTestRule.waitForIdle()
        assertTrue(viewModel.uiState.value.showScreenReaderConsentDialog)
        composeTestRule.onNodeWithTag("btn_confirm_screen_reader_consent").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_cancel_screen_reader_consent").performClick()
        composeTestRule.waitForIdle()
        assertFalse(viewModel.uiState.value.showScreenReaderConsentDialog)

        // 2. Denied MediaProjection result sets PERMISSION_DENIED and shows fallback guidance
        viewModel.onMediaProjectionPermissionResult(context, Activity.RESULT_CANCELED, null)
        composeTestRule.waitForIdle()
        assertEquals(ScreenReaderState.PERMISSION_DENIED, viewModel.uiState.value.screenReaderState)
        composeTestRule.onNodeWithTag("ai_assistant_screen_list")
            .performScrollToNode(hasTestTag("ocr_error_banner"))
        composeTestRule.onNodeWithTag("ocr_error_banner").assertIsDisplayed()

        // 3. Protected FLAG_SECURE (all-black) bitmap must be detected cleanly without crash
        val blackBitmap = Bitmap.createBitmap(120, 200, Bitmap.Config.ARGB_8888)
        blackBitmap.eraseColor(Color.BLACK)
        val outcome = runBlocking {
            MlKitOrderOcrEngine.recognizeBitmap(blackBitmap, OrderInputSource.SCREEN_READER)
        }
        assertTrue(outcome is OcrScanOutcome.ProtectedOrBlankScreen)
        blackBitmap.recycle()

        // 4. OCR / manual text parse + user correction + save to earnings offline
        viewModel.parseManualOrPastedOrderText("GrabFood ค่าส่ง ฿82 รวม 3.8 กม. ไปรับ 0.6 กม. 18 นาที")
        composeTestRule.waitForIdle()
        assertNotNull(viewModel.uiState.value.currentOrderAnalysis)
        composeTestRule.onNodeWithTag("ai_assistant_screen_list")
            .performScrollToNode(hasTestTag("order_analysis_result_card"))
        composeTestRule.onNodeWithTag("order_analysis_result_card").assertIsDisplayed()

        // User corrects payout before saving
        viewModel.updateOrderDraftFields(payoutText = "90", pickupName = "ร้านข้าวมันไก่", dropoffName = "อโศก")
        composeTestRule.waitForIdle()
        assertTrue(viewModel.uiState.value.currentOrderDraft.wasUserCorrected)
        assertEquals(90.0, viewModel.uiState.value.currentOrderAnalysis!!.grossPayoutBaht, 0.001)

        // Save analyzed order + log to earnings
        val ordersCountBefore = viewModel.uiState.value.analyzedOrders.size
        assertTrue(viewModel.saveCurrentAnalyzedOrder(alsoLogToEarnings = true))
        composeTestRule.waitForIdle()
        assertEquals(ordersCountBefore + 1, viewModel.uiState.value.analyzedOrders.size)
        assertTrue(
            viewModel.uiState.value.analyzedOrders.any {
                it.pickupName == "ร้านข้าวมันไก่" && it.payoutBaht == 90.0
            }
        )
        assertTrue(
            viewModel.uiState.value.smartEarningsState.transactions.any {
                it.type == EarningsRecordType.INCOME && it.amountBaht == 90.0
            }
        )
    }
}
