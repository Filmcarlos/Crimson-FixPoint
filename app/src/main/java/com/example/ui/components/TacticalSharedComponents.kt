package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.GeoMath
import com.example.model.OrderGrade
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CardGlassSurface
import com.example.ui.theme.CrimsonBorderGlow
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.DeepCrimsonGlow
import com.example.ui.theme.EmeraldSRank
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.TextHudMuted
import com.example.ui.theme.TextHudPrimary
import com.example.ui.theme.TextHudSecondary
import com.example.viewmodel.FixPointUiState

/**
 * Dark Glassmorphism Card with glowing crimson border (`1px solid rgba(225, 29, 72, 0.32)`).
 */
@Composable
fun CrimsonGlassCard(
    modifier: Modifier = Modifier,
    borderColor: Color = CrimsonBorderGlow,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        CardGlassSurface,
                        CarbonDark.copy(alpha = 0.94f)
                    )
                )
            )
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(14.dp),
        content = content
    )
}

/**
 * Top Tactical Header Bar with Crimson FixPoint branding, Platform Tag, Floating HUD toggle, and Web Audio/PCM Mute/Unmute toggle.
 */
@Composable
fun TacticalTopHeader(
    uiState: FixPointUiState,
    onToggleMute: () -> Unit,
    onToggleFloatingHudStrip: () -> Unit,
    onOpenBikeMountHud: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1A080E),
                        ObsidianBlack
                    )
                )
            )
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(CrimsonRed, DeepCrimsonGlow)
                            )
                        )
                        .border(1.dp, CrimsonNeon, RoundedCornerShape(10.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.GpsFixed,
                        contentDescription = "Crimson FixPoint Icon",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "CRIMSON FIXPOINT",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 17.sp),
                            color = TextHudPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(uiState.selectedPlatform.accentColor.copy(alpha = 0.2f))
                                .border(1.dp, uiState.selectedPlatform.accentColor, RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = uiState.selectedPlatform.shortTag,
                                style = MaterialTheme.typography.labelSmall,
                                color = uiState.selectedPlatform.accentColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text(
                        text = "Tactical GPS Stabilizer & Short-Loop Filter",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.sp),
                        color = TextHudMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onToggleFloatingHudStrip,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("toggle_floating_hud_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "สลับแถบ Floating HUD ลอยด้านบน",
                        tint = if (uiState.showFloatingHudStrip) CrimsonNeon else TextHudMuted
                    )
                }

                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("mute_unmute_audio_button")
                ) {
                    Icon(
                        imageVector = if (uiState.isSoundMuted) {
                            Icons.AutoMirrored.Filled.VolumeOff
                        } else {
                            Icons.AutoMirrored.Filled.VolumeUp
                        },
                        contentDescription = if (uiState.isSoundMuted) "เปิดเสียงเตือนยุทธวิธี" else "ปิดเสียงเตือนยุทธวิธี",
                        tint = if (uiState.isSoundMuted) AmberWarning else EmeraldSRank
                    )
                }
            }
        }

        // Persistent Compact Floating HUD Strip (visible across tabs when enabled)
        AnimatedVisibility(
            visible = uiState.showFloatingHudStrip,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val eval = uiState.currentOrderEvaluation
            val gradeColor = when (eval.grade) {
                OrderGrade.S_RANK_LOOP -> EmeraldSRank
                OrderGrade.A_RANK_IN_ZONE -> CrimsonNeon
                OrderGrade.REJECT_OUT_OF_ZONE -> AmberWarning
            }
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                color = Color(0xE6120D11),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, CrimsonNeon.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .clickable { onOpenBikeMountHud() }
                    .testTag("compact_floating_hud_strip")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (uiState.isFixPointLocked) EmeraldSRank else AmberWarning)
                        )
                        Text(
                            text = if (uiState.isFixPointLocked) "LOCKED" else "STANDBY",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (uiState.isFixPointLocked) EmeraldSRank else AmberWarning
                        )
                        Text(
                            text = "• ห่างหมุด ${GeoMath.formatKm(uiState.distanceToPinKm)} km",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextHudPrimary
                        )
                        Text(
                            text = "• ส่ง ≤ ${GeoMath.format1Dec(uiState.maxDropoffRadiusKm.toDouble())} km",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextHudSecondary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = when (eval.grade) {
                                OrderGrade.S_RANK_LOOP -> "S-RANK"
                                OrderGrade.A_RANK_IN_ZONE -> "A-RANK"
                                OrderGrade.REJECT_OUT_OF_ZONE -> "OUT-ZONE"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = gradeColor,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = "ขยายหน้าปัดมอเตอร์ไซค์เต็มจอ",
                            tint = CrimsonNeon,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}
