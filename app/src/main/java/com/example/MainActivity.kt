package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.AppScreen
import com.example.model.GeoMath
import com.example.model.OrderInputSource
import com.example.model.SecondaryScreen
import com.example.ui.components.CrimsonBottomNavBar
import com.example.ui.components.CrimsonNavRail
import com.example.ui.components.CrimsonTopHeader
import com.example.ui.components.SecondaryMenuBottomSheet
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.CloudSyncSecondaryScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.EarningsAnalyticsScreen
import com.example.ui.screens.JobHistorySecondaryScreen
import com.example.ui.screens.JobRadarScreen
import com.example.ui.screens.MembershipSecondaryScreen
import com.example.ui.screens.ProfileSettingsScreen
import com.example.ui.screens.SmartMapScreen
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextHudPrimary
import com.example.viewmodel.FixPointViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val incomingSharedImageUri = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        extractSharedImageIntent(intent)
        setContent {
            MyApplicationTheme {
                CrimsonFixPointApp(sharedImageFlow = incomingSharedImageUri)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        extractSharedImageIntent(intent)
    }

    private fun extractSharedImageIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("image/") == true) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_STREAM)
            }
            if (uri != null) {
                incomingSharedImageUri.value = uri
            }
        }
    }
}

@SuppressLint("BatteryLife")
@Composable
fun CrimsonFixPointApp(
    viewModel: FixPointViewModel = viewModel(),
    sharedImageFlow: MutableStateFlow<Uri?>? = null
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var pendingStartLockAfterPermission by remember { mutableStateOf(false) }
    var pendingOcrSendToRadar by remember { mutableStateOf(false) }
    var showSecondaryMenuSheet by remember { mutableStateOf(false) }
    var localToastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissionFlags(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Handle images shared directly to Crimson FixPoint via ACTION_SEND
    if (sharedImageFlow != null) {
        val sharedUri by sharedImageFlow.collectAsStateWithLifecycle()
        LaunchedEffect(sharedUri) {
            val uri = sharedUri ?: return@LaunchedEffect
            viewModel.processOrderImageUri(context, uri, OrderInputSource.SHARED_IMAGE)
            sharedImageFlow.value = null
        }
    }

    // Keep screen on dynamically
    DisposableEffect(uiState.keepScreenOn) {
        val activity = context as? Activity
        if (uiState.keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {}
    }

    val activeBanner = localToastMessage ?: uiState.statusBannerMessageTh
    LaunchedEffect(activeBanner) {
        if (activeBanner != null) {
            delay(3000L)
            localToastMessage = null
            viewModel.clearStatusBanner()
        }
    }

    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onPermissionResult(context, granted, pendingStartLockAfterPermission)
        pendingStartLockAfterPermission = false
    }

    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onBackgroundLocationResult(context, granted)
    }

    val batteryOptimizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        viewModel.onBatteryExemptionResult(context)
    }

    // User-Controlled MediaProjection Permission Launcher
    val mediaProjectionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onMediaProjectionPermissionResult(context, result.resultCode, result.data)
    }

    // Zero-Permission Android Photo Picker for Screenshot OCR Import (AI Assistant & Job Radar)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        val sendToRadar = pendingOcrSendToRadar
        pendingOcrSendToRadar = false
        if (uri != null) {
            viewModel.processOrderImageUri(
                context = context,
                uri = uri,
                source = OrderInputSource.SCREENSHOT_IMPORT,
                alsoSendToJobRadar = sendToRadar
            )
        }
    }

    // Offline JSON Local Backup & Restore via Storage Access Framework
    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) viewModel.exportBackupToUri(context, uri)
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.importBackupFromUri(context, uri)
    }

    val launchMediaProjectionConsentIntent = {
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager
        if (mgr != null) {
            runCatching {
                mediaProjectionLauncher.launch(mgr.createScreenCaptureIntent())
            }.onFailure {
                localToastMessage = "อุปกรณ์ไม่รองรับการขอสิทธิ์ MediaProjection"
            }
        } else {
            localToastMessage = "ไม่พบบริการ MediaProjection ในอุปกรณ์นี้"
        }
        Unit
    }

    val requestForegroundPermissions = { startLockOnGrant: Boolean ->
        pendingStartLockAfterPermission = startLockOnGrant
        val perms = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }.toTypedArray()
        foregroundPermissionLauncher.launch(perms)
    }

    val requestBackgroundPermission = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    val requestBatteryExemption = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                batteryOptimizationLauncher.launch(intent)
            }.onFailure {
                runCatching {
                    val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(fallback)
                }
            }
        }
        Unit
    }

    val openSystemSettings = {
        runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
        Unit
    }

    val openNotificationListenerSettings = {
        runCatching {
            val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }.onFailure {
            openSystemSettings()
        }
        Unit
    }

    val copyCoordinates: (Double, Double) -> Unit = { lat, lng ->
        val formatted = GeoMath.formatCoordinatePair(lat, lng, uiState.coordinateFormat)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("พิกัด GPS", formatted))
        localToastMessage = "คัดลอกพิกัด $formatted แล้ว"
    }

    val shareCoordinates: (Double, Double) -> Unit = { lat, lng ->
        val rawPair = String.format(Locale.US, "%.6f, %.6f", lat, lng)
        runCatching {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "พิกัดจาก Crimson FixPoint")
                putExtra(Intent.EXTRA_TEXT, "พิกัดตำแหน่ง: $rawPair")
            }
            val chooser = Intent.createChooser(sendIntent, "แชร์พิกัดตำแหน่ง").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        }
    }

    BackHandler(enabled = uiState.secondaryScreen != null || uiState.currentScreen != AppScreen.HOME) {
        viewModel.navigateBack()
    }

    if (showSecondaryMenuSheet) {
        SecondaryMenuBottomSheet(
            currentSecondary = uiState.secondaryScreen,
            isMembershipProEnabled = uiState.cloudSyncState.isMembershipProEnabled,
            pendingSyncCount = uiState.cloudSyncState.pendingSyncRecordsCount,
            onSelectSecondary = { target ->
                showSecondaryMenuSheet = false
                viewModel.openSecondaryScreen(target)
            },
            onDismiss = { showSecondaryMenuSheet = false }
        )
    }

    val activeRadarCount = uiState.rankedActiveJobOffers.size

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        val useNavRail = maxWidth >= 840.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = ObsidianBlack,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useNavRail) {
                    CrimsonBottomNavBar(
                        currentScreen = uiState.currentScreen,
                        activeRadarJobsCount = activeRadarCount,
                        onSelectScreen = { viewModel.selectScreen(it) }
                    )
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    CrimsonNavRail(
                        currentScreen = uiState.currentScreen,
                        activeRadarJobsCount = activeRadarCount,
                        onSelectScreen = { viewModel.selectScreen(it) }
                    )
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        CrimsonTopHeader(
                            currentScreen = uiState.currentScreen,
                            secondaryScreen = uiState.secondaryScreen,
                            lockState = uiState.lockState,
                            satellitesUsed = uiState.telemetry.satellitesUsedInFix,
                            isWorkSessionActive = uiState.activeWorkSession != null,
                            onGpsPillClick = {
                                val handled = viewModel.toggleMainPowerLock(context)
                                if (!handled) requestForegroundPermissions(true)
                            },
                            onMenuClick = { showSecondaryMenuSheet = true },
                            onBackFromSecondary = { viewModel.openSecondaryScreen(null) }
                        )

                        AnimatedContent(
                            targetState = Pair(uiState.secondaryScreen, uiState.currentScreen),
                            transitionSpec = {
                                fadeIn(animationSpec = tween(220)).togetherWith(fadeOut(animationSpec = tween(160)))
                            },
                            label = "crimson_3_screen_transition",
                            modifier = Modifier.weight(1f)
                        ) { (secondary, screen) ->
                            if (secondary != null) {
                                when (secondary) {
                                    SecondaryScreen.SETTINGS, SecondaryScreen.PROFILE -> ProfileSettingsScreen(
                                        uiState = uiState,
                                        onUpdateRiderProfile = { viewModel.updateRiderProfile(it) },
                                        onUpdateFuelSettings = { cap, cons, warn ->
                                            viewModel.updateFuelSettings(cap, cons, warn)
                                        },
                                        onMarkRefueledFullTank = { viewModel.markRefueledFullTank() },
                                        onUpdatePrivacy = { viewModel.updatePrivacyPreferences(it) },
                                        onClearOcrHistory = { viewModel.clearAllAnalyzedOrders() },
                                        onToggleAlertVibration = { viewModel.setAlertVibrationEnabled(it) },
                                        onSetGpsBufferMeters = { viewModel.setGpsBufferMeters(it) },
                                        onSelectMeasurementUnit = { viewModel.setMeasurementUnit(it) },
                                        onSelectCoordinateFormat = { viewModel.setCoordinateFormat(it) },
                                        onSelectUpdateFrequency = { viewModel.setUpdateFrequency(context, it) },
                                        onToggleKeepScreenOn = { viewModel.setKeepScreenOn(it) },
                                        onRequestForegroundPermissions = { requestForegroundPermissions(false) },
                                        onRequestBackgroundPermission = requestBackgroundPermission,
                                        onRequestBatteryExemption = requestBatteryExemption,
                                        onRequestNotificationListenerAccess = openNotificationListenerSettings,
                                        onOpenSystemSettings = openSystemSettings,
                                        onExportLocalBackup = {
                                            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                            exportBackupLauncher.launch("crimson_fixpoint_backup_$stamp.json")
                                        },
                                        onImportLocalBackup = {
                                            importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                        }
                                    )

                                    SecondaryScreen.CLOUD_SYNC -> CloudSyncSecondaryScreen(
                                        uiState = uiState,
                                        onToggleCloudSyncOptIn = { viewModel.toggleCloudSyncOptIn(it) },
                                        onTriggerCloudSync = { viewModel.triggerCloudSyncAttempt() },
                                        onExportLocalBackup = {
                                            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                                            exportBackupLauncher.launch("crimson_fixpoint_backup_$stamp.json")
                                        },
                                        onImportLocalBackup = {
                                            importBackupLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                                        }
                                    )

                                    SecondaryScreen.JOB_HISTORY -> JobHistorySecondaryScreen(
                                        uiState = uiState,
                                        onDeleteAnalyzedOrder = { viewModel.deleteAnalyzedOrder(it) },
                                        onClearAnalyzedOrders = { viewModel.clearAllAnalyzedOrders() },
                                        onClearAllRadarOffers = { viewModel.clearAllJobOffers() }
                                    )

                                    SecondaryScreen.MEMBERSHIP -> MembershipSecondaryScreen(
                                        uiState = uiState,
                                        onToggleMembershipProReady = { viewModel.toggleMembershipProReady(it) },
                                        onNavigateToCloudSync = { viewModel.openSecondaryScreen(SecondaryScreen.CLOUD_SYNC) }
                                    )
                                }
                            } else {
                                when (screen) {
                                    AppScreen.HOME -> DashboardScreen(
                                        uiState = uiState,
                                        onToggleWorkSession = { viewModel.toggleWorkSession(context) },
                                        onToggleGpsLock = {
                                            val handled = viewModel.toggleMainPowerLock(context)
                                            if (!handled) requestForegroundPermissions(true)
                                        },
                                        onMuteZoneAlert = { viewModel.muteZoneAlert(context) },
                                        onRequestLocationPermission = { requestForegroundPermissions(true) },
                                        onNavigateToScreen = { viewModel.selectScreen(it) },
                                        onNavigateToSecondaryScreen = { viewModel.openSecondaryScreen(it) }
                                    )

                                    AppScreen.JOB_RADAR -> JobRadarScreen(
                                        uiState = uiState,
                                        onSelectRankingMode = { viewModel.setJobRankingMode(it) },
                                        onTogglePlatformAdapter = { platform, enabled ->
                                            viewModel.togglePlatformAdapter(platform, enabled)
                                        },
                                        onOpenNotificationListenerSettings = openNotificationListenerSettings,
                                        onImportScreenshotToRadar = {
                                            pendingOcrSendToRadar = true
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        onIngestManualJobText = { raw, title ->
                                            viewModel.ingestJobOfferText(rawText = raw, title = title)
                                        },
                                        onLoadOfferIntoAi = { viewModel.loadOfferIntoAiAnalyzer(it) },
                                        onAcceptAndLogJob = { viewModel.acceptAndLogJobOffer(it) },
                                        onDismissJob = { viewModel.dismissJobOffer(it) },
                                        onClearExpiredJobs = { viewModel.clearExpiredJobOffers() }
                                    )

                                    AppScreen.LIVE_MAP -> SmartMapScreen(
                                        uiState = uiState,
                                        onMapTapPin = { lat, lng -> viewModel.onMapTapPin(lat, lng) },
                                        onSetMapTapMode = { mode -> viewModel.setMapTapActionMode(mode) },
                                        onSelectActivePin = { id -> viewModel.selectActivePin(id) },
                                        onSaveCurrentLocation = { name, move, cat ->
                                            viewModel.saveCurrentLocation(name, move, cat)
                                        },
                                        onPinFromCoordinates = { coords, name, move, cat ->
                                            viewModel.pinOrMoveFromCoordinatesText(coords, name, move, cat)
                                        },
                                        onDeletePin = { id -> viewModel.deleteSavedLocation(id) },
                                        onSetZoneRadiusKm = { km -> viewModel.setZoneRadiusKm(km) },
                                        onCopyCoordinates = copyCoordinates,
                                        onShareCoordinates = shareCoordinates
                                    )

                                    AppScreen.EARNINGS -> EarningsAnalyticsScreen(
                                        uiState = uiState,
                                        onSelectPeriod = { viewModel.selectEarningsPeriod(it) },
                                        onShiftDate = { viewModel.shiftEarningsDate(it) },
                                        onResetDateToToday = { viewModel.resetEarningsDateToToday() },
                                        onSelectHistoryFilter = { viewModel.selectEarningsHistoryFilter(it) },
                                        onUpdateGoals = { d, w, m -> viewModel.updateEarningsGoals(d, w, m) },
                                        onUpdateDailyWorkHours = { dk, hrs -> viewModel.updateDailyWorkHours(dk, hrs) },
                                        onAddTransaction = { type, dk, title, cat, amt, dist, mins ->
                                            viewModel.addEarningsTransaction(type, dk, title, cat, amt, dist, mins)
                                        },
                                        onDeleteTransaction = { viewModel.deleteEarningsTransaction(it) }
                                    )

                                    AppScreen.AI_ASSISTANT -> AiAssistantScreen(
                                        uiState = uiState,
                                        onRequestStartScreenReader = {
                                            if (viewModel.requestStartScreenReader()) {
                                                launchMediaProjectionConsentIntent()
                                            }
                                        },
                                        onAcceptScreenReaderConsentAndStart = {
                                            viewModel.acceptScreenReaderConsent()
                                            launchMediaProjectionConsentIntent()
                                        },
                                        onDismissScreenReaderConsent = { viewModel.dismissScreenReaderConsentDialog() },
                                        onTriggerCaptureNow = { viewModel.triggerScreenReaderCaptureNow(context) },
                                        onStopScreenReader = { viewModel.stopScreenReader(context) },
                                        onImportScreenshot = {
                                            pendingOcrSendToRadar = false
                                            photoPickerLauncher.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        onParseManualText = { text -> viewModel.parseManualOrPastedOrderText(text) },
                                        onUpdateDraftFields = { payout, dist, dead, mins, pickup, dropoff ->
                                            viewModel.updateOrderDraftFields(payout, dist, dead, mins, pickup, dropoff)
                                        },
                                        onResetDraft = { viewModel.resetOrderDraft() },
                                        onSaveAnalyzedOrder = { logToEarnings ->
                                            viewModel.saveCurrentAnalyzedOrder(logToEarnings)
                                        },
                                        onDeleteAnalyzedOrder = { id -> viewModel.deleteAnalyzedOrder(id) },
                                        onNavigateToMapHotspot = { pinId ->
                                            viewModel.selectMapPinForSheet(pinId)
                                            viewModel.selectScreen(AppScreen.LIVE_MAP)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Sleek Floating Status Toast Banner (positioned clear of the Work Zone bottom dock on Map screen)
                    if (activeBanner != null) {
                        val isMapScreen = uiState.secondaryScreen == null && uiState.currentScreen == AppScreen.LIVE_MAP
                        Box(
                            modifier = Modifier
                                .align(if (isMapScreen) Alignment.TopCenter else Alignment.BottomCenter)
                                .padding(
                                    start = 18.dp,
                                    end = 18.dp,
                                    top = if (isMapScreen) 136.dp else 14.dp,
                                    bottom = 14.dp
                                )
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xF5181018))
                                .border(1.dp, CrimsonNeon, RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                                .testTag("status_feedback_banner")
                        ) {
                            Text(
                                text = activeBanner,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextHudPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}
