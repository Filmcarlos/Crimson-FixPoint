package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.AppScreen
import com.example.model.GeoMath
import com.example.ui.components.PrecisionBottomBar
import com.example.ui.components.PrecisionButtonShape
import com.example.ui.components.PrecisionTopBar
import com.example.ui.screens.FixPointCoreTab
import com.example.ui.screens.HotzonePinsTab
import com.example.ui.screens.SmartOrderFilterTab
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextHudPrimary
import com.example.viewmodel.FixPointViewModel
import kotlinx.coroutines.delay
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                CrimsonFixPointApp()
            }
        }
    }
}

@SuppressLint("BatteryLife")
@Composable
fun CrimsonFixPointApp(viewModel: FixPointViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingStartLockAfterPermission by remember { mutableStateOf(false) }
    var localToastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    // Apply Keep Screen On window flag dynamically based on user setting
    DisposableEffect(uiState.keepScreenOn) {
        val activity = context as? Activity
        if (uiState.keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {}
    }

    // Auto-clear transient status banner after 2.8 seconds
    val activeBanner = localToastMessage ?: uiState.statusBannerMessageTh
    LaunchedEffect(activeBanner) {
        if (activeBanner != null) {
            delay(2800L)
            localToastMessage = null
            viewModel.clearStatusBanner()
        }
    }

    val foregroundPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onPermissionResult(
            context = context,
            granted = granted,
            startLockIfGranted = pendingStartLockAfterPermission
        )
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

    val requestBatteryOptimizationExemption = {
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

    val openSystemAppSettings = {
        runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
        Unit
    }

    val copyCoordinatesToClipboard: (Double, Double) -> Unit = { lat, lng ->
        val formatted = GeoMath.formatCoordinatePair(lat, lng, uiState.coordinateFormat)
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("พิกัด GPS", formatted))
        localToastMessage = "คัดลอกพิกัด $formatted แล้ว"
    }

    val shareCoordinatesIntent: (Double, Double) -> Unit = { lat, lng ->
        val rawPair = String.format(Locale.US, "%.6f, %.6f", lat, lng)
        val shareBody = "พิกัดตำแหน่ง: $rawPair"
        runCatching {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "พิกัดตำแหน่งจาก Crimson FixPoint")
                putExtra(Intent.EXTRA_TEXT, shareBody)
            }
            val chooser = Intent.createChooser(sendIntent, "แชร์พิกัดตำแหน่ง").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            localToastMessage = "เปิดหน้าต่างแชร์พิกัดแล้ว"
        }
    }

    BackHandler(enabled = uiState.currentScreen != AppScreen.HOME) {
        viewModel.selectScreen(AppScreen.HOME)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack),
        containerColor = ObsidianBlack,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            PrecisionBottomBar(
                currentScreen = uiState.currentScreen,
                savedCount = uiState.savedLocations.size,
                onSelectScreen = { viewModel.selectScreen(it) }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                PrecisionTopBar(
                    lockState = uiState.lockState,
                    satellitesUsed = uiState.telemetry.satellitesUsedInFix,
                    satellitesVisible = uiState.telemetry.satellitesVisible
                )

                // Smooth Slide + Fade Screen Transitions
                AnimatedContent(
                    targetState = uiState.currentScreen,
                    transitionSpec = {
                        val forward = targetState.ordinal > initialState.ordinal
                        val slideIn = slideInHorizontally(animationSpec = tween(260)) { width ->
                            if (forward) width / 3 else -width / 3
                        } + fadeIn(animationSpec = tween(260))
                        val slideOut = slideOutHorizontally(animationSpec = tween(220)) { width ->
                            if (forward) -width / 3 else width / 3
                        } + fadeOut(animationSpec = tween(200))
                        slideIn.togetherWith(slideOut)
                    },
                    label = "screen_slide_fade_transition",
                    modifier = Modifier.weight(1f)
                ) { screen ->
                    when (screen) {
                        AppScreen.HOME -> FixPointCoreTab(
                            uiState = uiState,
                            onTogglePower = {
                                val handled = viewModel.toggleMainPowerLock(context)
                                if (!handled) {
                                    requestForegroundPermissions(true)
                                }
                            },
                            onCopyCoordinates = copyCoordinatesToClipboard,
                            onShareCoordinates = shareCoordinatesIntent,
                            onSaveLocation = { name -> viewModel.saveCurrentLocation(name) },
                            onMapTapPin = { lat, lng -> viewModel.onMapTapPin(lat, lng) },
                            onMuteZoneAlert = { viewModel.muteZoneAlert(context) },
                            onNavigateToZoneTab = { viewModel.selectScreen(AppScreen.SAVED_LOCATIONS) },
                            onRequestLocationPermission = { requestForegroundPermissions(true) },
                            onOpenAppSettings = openSystemAppSettings
                        )

                        AppScreen.SAVED_LOCATIONS -> HotzonePinsTab(
                            uiState = uiState,
                            onSaveCurrentLocation = { name, moveActive ->
                                viewModel.saveCurrentLocation(name, moveActive)
                            },
                            onMapTapPin = { lat, lng -> viewModel.onMapTapPin(lat, lng) },
                            onSetMapTapMode = { mode -> viewModel.setMapTapActionMode(mode) },
                            onPinOrMoveFromCoordinates = { rawCoords, customName, moveActive ->
                                viewModel.pinOrMoveFromCoordinatesText(rawCoords, customName, moveActive)
                            },
                            onSelectActivePin = { id -> viewModel.selectActivePin(id) },
                            onMoveSavedPin = { id, newLat, newLng, newName ->
                                viewModel.moveSavedPin(id, newLat, newLng, newName)
                            },
                            onDeleteLocation = { id -> viewModel.deleteSavedLocation(id) },
                            onSetZoneRadiusKm = { radiusKm -> viewModel.setZoneRadiusKm(radiusKm) },
                            onCopyCoordinates = copyCoordinatesToClipboard,
                            onShareCoordinates = shareCoordinatesIntent
                        )

                        AppScreen.SETTINGS -> SmartOrderFilterTab(
                            uiState = uiState,
                            onToggleAlertVibration = { viewModel.setAlertVibrationEnabled(it) },
                            onToggleAlertSound = { viewModel.setAlertSoundEnabled(it) },
                            onSelectAlertTone = { viewModel.setAlertTone(it, playPreview = true) },
                            onSetAlertVolumePercent = { viewModel.setAlertVolumePercent(it) },
                            onSetGpsBufferMeters = { viewModel.setGpsBufferMeters(it) },
                            onSelectMeasurementUnit = { viewModel.setMeasurementUnit(it) },
                            onSelectCoordinateFormat = { viewModel.setCoordinateFormat(it) },
                            onSelectUpdateFrequency = { viewModel.setUpdateFrequency(context, it) },
                            onToggleKeepScreenOn = { viewModel.setKeepScreenOn(it) },
                            onRequestForegroundPermissions = { requestForegroundPermissions(false) },
                            onRequestBackgroundLocationPermission = requestBackgroundPermission,
                            onRequestBatteryExemption = requestBatteryOptimizationExemption,
                            onOpenSystemSettings = openSystemAppSettings
                        )
                    }
                }
            }

            // Sleek Floating Status Toast at Bottom
            if (activeBanner != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .fillMaxWidth()
                        .clip(PrecisionButtonShape)
                        .background(Color(0xF2160D11))
                        .border(1.dp, CrimsonNeon, PrecisionButtonShape)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("status_feedback_banner")
                ) {
                    Text(
                        text = activeBanner,
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextHudPrimary
                    )
                }
            }
        }
    }
}
