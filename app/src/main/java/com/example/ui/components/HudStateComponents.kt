package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.model.SecondaryScreen
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonBorderGlow
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.CrimsonSubtleBg
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.EmeraldSRank
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.SurfaceLevel1
import com.example.ui.theme.SurfaceLevel2
import com.example.ui.theme.TextHudMuted
import com.example.ui.theme.TextHudPrimary
import com.example.ui.theme.TextHudSecondary
import com.example.ui.theme.VioletInsight
import java.util.Locale

@Composable
fun CrimsonLoadingState(
    message: String,
    modifier: Modifier = Modifier
) {
    PremiumCard(
        modifier = modifier.testTag("crimson_loading_state"),
        accentBorder = CyanTelemetry.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CircularProgressIndicator(
                color = CrimsonNeon,
                strokeWidth = 3.dp,
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextHudPrimary
            )
        }
    }
}

@Composable
fun CrimsonErrorBanner(
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    accentColor: Color = AmberWarning,
    testTagName: String = "crimson_error_banner"
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag(testTagName),
        shape = RoundedCornerShape(16.dp),
        color = accentColor.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.65f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = TextHudPrimary,
                modifier = Modifier.weight(1f)
            )
            if (actionLabel != null && onAction != null) {
                Surface(
                    onClick = onAction,
                    shape = RoundedCornerShape(10.dp),
                    color = accentColor.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, accentColor)
                ) {
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextHudPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CrimsonEmptyStateCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    accentColor: Color = CrimsonNeon,
    testTagName: String = "crimson_empty_state"
) {
    PremiumCard(
        modifier = modifier.testTag(testTagName),
        accentBorder = HairlineBorder
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.14f))
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextHudPrimary,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextHudSecondary,
                textAlign = TextAlign.Center
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.height(2.dp))
                PremiumSecondaryButton(
                    text = actionLabel,
                    accentColor = accentColor,
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun CrimsonConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "ยกเลิก",
    isDestructive: Boolean = false
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceLevel1,
        titleContentColor = TextHudPrimary,
        textContentColor = TextHudSecondary,
        title = { Text(text = title, fontWeight = FontWeight.Bold) },
        text = { Text(text = message, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            PremiumPrimaryButton(
                text = confirmLabel,
                containerColor = if (isDestructive) Color(0xFFDC2626) else CrimsonRed,
                onClick = onConfirm
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = dismissLabel, color = TextHudMuted)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecondaryMenuBottomSheet(
    currentSecondary: SecondaryScreen?,
    isMembershipProEnabled: Boolean,
    pendingSyncCount: Int,
    onSelectSecondary: (SecondaryScreen) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceLevel1,
        contentColor = TextHudPrimary,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 18.dp, vertical = 18.dp)
                .testTag("secondary_menu_sheet"),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionHeaderRow(
                title = "เมนูระบบ & บัญชีผู้ใช้",
                subtitle = "ตั้งค่า โปรไฟล์ ซิงก์ข้อมูล ประวัติงาน และสมาชิก"
            )
            Spacer(modifier = Modifier.height(2.dp))

            SecondaryScreen.entries.forEach { item ->
                val selected = item == currentSecondary
                val (icon, accent, badgeText) = when (item) {
                    SecondaryScreen.SETTINGS -> Triple(Icons.Default.Settings, CyanTelemetry, null)
                    SecondaryScreen.PROFILE -> Triple(Icons.Default.Person, CrimsonNeon, null)
                    SecondaryScreen.CLOUD_SYNC -> Triple(
                        Icons.Default.CloudSync,
                        EmeraldSRank,
                        "$pendingSyncCount รายการ"
                    )
                    SecondaryScreen.JOB_HISTORY -> Triple(Icons.Default.History, VioletInsight, null)
                    SecondaryScreen.MEMBERSHIP -> Triple(
                        Icons.Default.WorkspacePremium,
                        AmberWarning,
                        if (isMembershipProEnabled) "PRO READY" else "FREE"
                    )
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectSecondary(item) }
                        .testTag("secondary_menu_${item.name.lowercase(Locale.ROOT)}"),
                    shape = RoundedCornerShape(16.dp),
                    color = if (selected) CrimsonSubtleBg else SurfaceLevel2,
                    border = BorderStroke(1.dp, if (selected) CrimsonNeon else HairlineBorder)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(accent.copy(alpha = 0.16f))
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = item.titleTh,
                                tint = accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.titleTh,
                                style = MaterialTheme.typography.titleSmall,
                                color = TextHudPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = item.subtitleTh,
                                style = MaterialTheme.typography.labelSmall,
                                color = TextHudSecondary
                            )
                        }
                        if (badgeText != null) {
                            StatusPillBadge(text = badgeText, accentColor = accent)
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = TextHudMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
