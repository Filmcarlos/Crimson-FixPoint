package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Thunderstorm
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.RainForecastState
import com.example.model.RainLeadTimeOption
import com.example.model.WeatherForecastStatus
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonBorderGlow
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.DeepCrimsonGlow
import com.example.ui.theme.EmeraldSRank
import com.example.ui.theme.TextHudMuted
import com.example.ui.theme.TextHudPrimary
import com.example.ui.theme.TextHudSecondary
import kotlin.math.roundToInt

/**
 * Home screen card for Feature 6: เตือนฝนใกล้ตก (Open-Meteo)
 * Displays "ไม่มีฝน", "ฝนจะตกในอีก X นาที โอกาส Y%", or "ดึงพยากรณ์ไม่ได้".
 */
@Composable
fun RainForecastHomeCard(
    rainForecast: RainForecastState,
    onRefreshForecast: () -> Unit,
    modifier: Modifier = Modifier
) {
    val status = rainForecast.status
    val borderColor = when (status) {
        WeatherForecastStatus.RAIN_COMING -> CrimsonNeon
        WeatherForecastStatus.NO_RAIN -> EmeraldSRank.copy(alpha = 0.8f)
        WeatherForecastStatus.ERROR -> AmberWarning
        else -> CrimsonBorderGlow
    }
    val statusColor = when (status) {
        WeatherForecastStatus.RAIN_COMING -> CrimsonNeon
        WeatherForecastStatus.NO_RAIN -> EmeraldSRank
        WeatherForecastStatus.ERROR -> AmberWarning
        else -> TextHudSecondary
    }
    val statusIcon = when (status) {
        WeatherForecastStatus.RAIN_COMING -> Icons.Default.Thunderstorm
        WeatherForecastStatus.NO_RAIN -> Icons.Default.WbSunny
        else -> Icons.Default.Cloud
    }

    CrimsonPrecisionPanel(
        borderColor = borderColor,
        glowAlpha = if (status == WeatherForecastStatus.RAIN_COMING) 0.28f else 0.12f,
        modifier = modifier.testTag("weather_rain_forecast_card")
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
                        .size(36.dp)
                        .clip(PrecisionButtonShape)
                        .background(statusColor.copy(alpha = 0.16f))
                        .border(1.dp, statusColor, PrecisionButtonShape)
                ) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column {
                    Text(
                        text = "พยากรณ์ฝนล่วงหน้า (Open-Meteo)",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextHudSecondary
                    )
                    Text(
                        text = rainForecast.cardHeadlineTh,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.5.sp),
                        color = statusColor,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.testTag("weather_rain_headline_text")
                    )
                }
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(PrecisionButtonShape)
                    .background(Color(0xFF141016))
                    .border(1.dp, CrimsonBorderGlow, PrecisionButtonShape)
                    .clickable { onRefreshForecast() }
                    .testTag("btn_refresh_rain_forecast")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "อัปเดตพยากรณ์อากาศ",
                    tint = CrimsonNeon,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Settings panel for Feature 6: ตั้งค่าเปิด/ปิด เวลาเตือนล่วงหน้า (15/30/60 นาที) และเกณฑ์ความน่าจะเป็นฝนตก
 */
@Composable
fun RainAlertSettingsPanel(
    rainForecast: RainForecastState,
    onToggleRainAlert: (Boolean) -> Unit,
    onSelectLeadTime: (RainLeadTimeOption) -> Unit,
    onSetProbabilityThreshold: (Int) -> Unit
) {
    CrimsonPrecisionPanel {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Thunderstorm,
                contentDescription = null,
                tint = CrimsonNeon,
                modifier = Modifier.size(19.dp)
            )
            Text(
                text = "การแจ้งเตือนฝนใกล้ตก (Open-Meteo)",
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.5.sp),
                color = TextHudPrimary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Toggle Rain Alert On/Off
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .minimumInteractiveComponentSize()
                .clip(PrecisionButtonShape)
                .background(Color(0xFF0A090E))
                .border(
                    width = 1.dp,
                    color = if (rainForecast.alertEnabled) CrimsonNeon else CrimsonBorderGlow,
                    shape = PrecisionButtonShape
                )
                .clickable { onToggleRainAlert(!rainForecast.alertEnabled) }
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag("setting_rain_alert_toggle"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "เตือนเมื่อฝนใกล้ตก (สั่นและแจ้งเตือนแม้ปิดหน้าจอ)",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextHudPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (rainForecast.alertEnabled) "สถานะ: เปิดการแจ้งเตือน" else "สถานะ: ปิดการแจ้งเตือน",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = if (rainForecast.alertEnabled) CrimsonNeon else TextHudMuted
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(PrecisionButtonShape)
                    .background(if (rainForecast.alertEnabled) CrimsonRed else Color(0xFF1E1E26))
                    .border(
                        1.dp,
                        if (rainForecast.alertEnabled) CrimsonNeon else Color(0xFF3F3F46),
                        PrecisionButtonShape
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = if (rainForecast.alertEnabled) "เปิด" else "ปิด",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (rainForecast.alertEnabled) Color.White else TextHudMuted
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Lead time options: 15 / 30 / 60 minutes
        Text(
            text = "เวลาเตือนล่วงหน้าก่อนฝนตก",
            style = MaterialTheme.typography.titleMedium,
            color = TextHudPrimary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RainLeadTimeOption.entries.forEach { opt ->
                val isSelected = rainForecast.leadTimeOption == opt
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .height(40.dp)
                        .clip(PrecisionButtonShape)
                        .background(
                            if (isSelected) Brush.linearGradient(listOf(CrimsonRed, DeepCrimsonGlow))
                            else Brush.linearGradient(listOf(Color(0xFF120F15), Color(0xFF0A090D)))
                        )
                        .border(1.dp, if (isSelected) CrimsonNeon else CrimsonBorderGlow, PrecisionButtonShape)
                        .clickable { onSelectLeadTime(opt) }
                        .testTag("setting_rain_lead_${opt.minutes}")
                ) {
                    Text(
                        text = opt.labelTh,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) Color.White else TextHudSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Probability threshold slider
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "เกณฑ์โอกาสเกิดฝนที่จะเตือน",
                style = MaterialTheme.typography.titleMedium,
                color = TextHudPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${rainForecast.probabilityThresholdPercent}%",
                style = MaterialTheme.typography.titleMedium,
                color = CrimsonNeon,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.testTag("setting_rain_prob_label")
            )
        }

        Slider(
            value = rainForecast.probabilityThresholdPercent.toFloat(),
            onValueChange = { onSetProbabilityThreshold(it.roundToInt()) },
            valueRange = 10f..95f,
            colors = SliderDefaults.colors(
                thumbColor = CrimsonNeon,
                activeTrackColor = CrimsonRed,
                inactiveTrackColor = Color(0xFF271B22)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("slider_rain_prob_threshold")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf(30, 50, 70, 80).forEach { pct ->
                val isSel = rainForecast.probabilityThresholdPercent == pct
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .height(36.dp)
                        .clip(PrecisionButtonShape)
                        .background(
                            if (isSel) Brush.linearGradient(listOf(CrimsonRed, DeepCrimsonGlow))
                            else Brush.linearGradient(listOf(Color(0xFF120F15), Color(0xFF0A090D)))
                        )
                        .border(1.dp, if (isSel) CrimsonNeon else CrimsonBorderGlow, PrecisionButtonShape)
                        .clickable { onSetProbabilityThreshold(pct) }
                        .testTag("btn_rain_prob_$pct")
                ) {
                    Text(
                        text = "$pct%",
                        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                        color = if (isSel) Color.White else TextHudSecondary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
