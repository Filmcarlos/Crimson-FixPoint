package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.TacticalTab
import com.example.service.GpsKeepAliveService
import com.example.ui.components.TacticalTopHeader
import com.example.ui.screens.BikeMountFullscreenHud
import com.example.ui.screens.FixPointCoreTab
import com.example.ui.screens.FloatingHudDiagnosticsTab
import com.example.ui.screens.HotzonePinsTab
import com.example.ui.screens.SmartOrderFilterTab
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CrimsonBorderGlow
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextHudMuted
import com.example.ui.theme.TextHudPrimary
import com.example.viewmodel.FixPointViewModel

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

@Composable
fun CrimsonFixPointApp(viewModel: FixPointViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.bindHardwareLocationUpdates(context, granted)
        if (granted) {
            ContextCompat.startForegroundService(context, Intent(context, GpsKeepAliveService::class.java))
        } else {
            context.stopService(Intent(context, GpsKeepAliveService::class.java))
        }
    }

    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (hasFine || hasCoarse) {
            viewModel.bindHardwareLocationUpdates(context, true)
            ContextCompat.startForegroundService(context, Intent(context, GpsKeepAliveService::class.java))
        }
    }

    BackHandler(enabled = uiState.activeTab != TacticalTab.FIXPOINT_CORE && !uiState.isFullBikeMountHudOpen) {
        viewModel.selectTab(TacticalTab.FIXPOINT_CORE)
    }

    if (uiState.isFullBikeMountHudOpen) {
        BikeMountFullscreenHud(
            uiState = uiState,
            onCloseHud = { viewModel.setFullBikeMountHudOpen(false) },
            onSimulateNextOrder = { viewModel.simulateNextOrderCard() }
        )
        return
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack),
        containerColor = ObsidianBlack,
        contentWindowInsets = WindowInsets.safeDrawing,
        bottomBar = {
            NavigationBar(
                containerColor = CarbonDark,
                tonalElevation = 8.dp,
                windowInsets = WindowInsets.navigationBars,
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CrimsonBorderGlow)
            ) {
                TacticalTab.entries.forEach { tab ->
                    val selected = uiState.activeTab == tab
                    val icon = when (tab) {
                        TacticalTab.FIXPOINT_CORE -> Icons.Default.GpsFixed
                        TacticalTab.SMART_FILTER -> Icons.Default.FilterCenterFocus
                        TacticalTab.HOTZONE_PINS -> Icons.Default.PinDrop
                        TacticalTab.FLOATING_HUD -> Icons.Default.Speed
                    }
                    NavigationBarItem(
                        selected = selected,
                        onClick = { viewModel.selectTab(tab) },
                        icon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = tab.titleTh
                            )
                        },
                        label = {
                            Text(
                                text = tab.titleTh,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.5.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                ),
                                maxLines = 1
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = TextHudPrimary,
                            selectedTextColor = CrimsonNeon,
                            indicatorColor = CrimsonRed.copy(alpha = 0.35f),
                            unselectedIconColor = TextHudMuted,
                            unselectedTextColor = TextHudMuted
                        ),
                        modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TacticalTopHeader(
                uiState = uiState,
                onToggleMute = { viewModel.toggleSoundMute() },
                onToggleFloatingHudStrip = { viewModel.toggleFloatingHudStrip() },
                onOpenBikeMountHud = { viewModel.setFullBikeMountHudOpen(true) }
            )

            Box(modifier = Modifier.weight(1f)) {
                when (uiState.activeTab) {
                    TacticalTab.FIXPOINT_CORE -> FixPointCoreTab(
                        uiState = uiState,
                        onSelectPlatform = { viewModel.selectPlatform(it) },
                        onUpdatePickupRadius = { viewModel.updatePickupRadius(it) },
                        onUpdateDropoffRadius = { viewModel.updateDropoffRadius(it) },
                        onSelectLockMode = { viewModel.selectLockMode(it) },
                        onToggleEngageLock = { viewModel.toggleEngageFixPointLock() },
                        onMapPinClicked = { lat, lng -> viewModel.setPinFromMapClick(lat, lng) },
                        onRequestGpsPermission = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                    Manifest.permission.POST_NOTIFICATIONS
                                )
                            )
                        }
                    )

                    TacticalTab.SMART_FILTER -> SmartOrderFilterTab(
                        uiState = uiState,
                        onSimulateNewOrder = { viewModel.simulateNextOrderCard() },
                        onSelectPresetOrder = { viewModel.selectPresetOrder(it) },
                        onEvaluateCustomOrder = { m, d, pKm, dKm, baht, bridge ->
                            viewModel.evaluateCustomOrderInput(m, d, pKm, dKm, baht, bridge)
                        },
                        onUpdateAutoFilterRules = { notifyDrop, blockBridge, shortLoop, minBaht ->
                            viewModel.updateAutoFilterRules(notifyDrop, blockBridge, shortLoop, minBaht)
                        }
                    )

                    TacticalTab.HOTZONE_PINS -> HotzonePinsTab(
                        uiState = uiState,
                        onSelectPin = { viewModel.selectHotzonePin(it) },
                        onMapPinClicked = { lat, lng -> viewModel.setPinFromMapClick(lat, lng) },
                        onUseCurrentGpsAsPin = { viewModel.setCurrentGpsAsActivePin() },
                        onAddCustomPin = { name, tag, lat, lng, desc ->
                            viewModel.addCustomHotzonePin(name, tag, lat, lng, desc)
                        },
                        onAddMerchantToCluster = { name, cat, offN, offE ->
                            viewModel.addMerchantToCluster(name, cat, offN, offE)
                        },
                        onRemoveMerchantFromCluster = { id ->
                            viewModel.removeMerchantFromCluster(id)
                        },
                        onSnapPinToGoldenCentroid = { viewModel.snapPinToGoldenCentroid() }
                    )

                    TacticalTab.FLOATING_HUD -> FloatingHudDiagnosticsTab(
                        uiState = uiState,
                        onOpenFullBikeMountHud = { viewModel.setFullBikeMountHudOpen(true) },
                        onToggleFloatingHudStrip = { viewModel.toggleFloatingHudStrip() }
                    )
                }
            }
        }
    }
}
