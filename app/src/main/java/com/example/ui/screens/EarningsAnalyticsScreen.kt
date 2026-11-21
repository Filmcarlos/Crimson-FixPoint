package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.RiderIntelligenceEngine
import com.example.model.DailyBreakEvenState
import com.example.model.EarningsHistoryFilter
import com.example.model.EarningsPeriod
import com.example.model.EarningsRecordType
import com.example.model.EarningsTransaction
import com.example.model.SmartEarningsCalculator
import com.example.ui.components.CrimsonHeroCard
import com.example.ui.components.ExpenseBreakdownCard
import com.example.ui.components.IncomeExpenseChartCard
import com.example.ui.components.MetricStatCard
import com.example.ui.components.PremiumCard
import com.example.ui.components.PremiumPrimaryButton
import com.example.ui.components.PremiumSecondaryButton
import com.example.ui.components.SectionHeaderRow
import com.example.ui.components.StatusPillBadge
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonNeon
import com.example.ui.theme.CrimsonRed
import com.example.ui.theme.CyanTelemetry
import com.example.ui.theme.EmeraldSRank
import com.example.ui.theme.HairlineBorder
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.SurfaceLevel1
import com.example.ui.theme.SurfaceLevel2
import com.example.ui.theme.TextHudMuted
import com.example.ui.theme.TextHudPrimary
import com.example.ui.theme.TextHudSecondary
import com.example.ui.theme.VioletInsight
import com.example.viewmodel.FixPointUiState
import java.util.Locale

@Composable
fun EarningsAnalyticsScreen(
    uiState: FixPointUiState,
    onSelectPeriod: (EarningsPeriod) -> Unit,
    onShiftDate: (Int) -> Unit,
    onResetDateToToday: () -> Unit,
    onSelectHistoryFilter: (EarningsHistoryFilter) -> Unit,
    onUpdateGoals: (Double, Double, Double) -> Unit,
    onUpdateDailyWorkHours: (String, Double) -> Unit,
    onAddTransaction: (
        type: EarningsRecordType,
        dateKey: String,
        title: String,
        category: String,
        amountBaht: Double,
        distanceKm: Double,
        durationMinutes: Int
    ) -> Boolean,
    onDeleteTransaction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val todayKey = remember { DailyBreakEvenState.todayDateKey() }
    val summary = remember(
        uiState.smartEarningsState,
        uiState.fuelState.distanceDrivenSinceRefuelKm
    ) {
        SmartEarningsCalculator.computeSummary(
            state = uiState.smartEarningsState,
            todayKey = todayKey,
            todayLiveFuelDistanceKm = uiState.fuelState.distanceDrivenSinceRefuelKm
        )
    }

    val expenseBreakdown = remember(summary.filteredTransactions, uiState.smartEarningsState) {
        val periodAll = SmartEarningsCalculator.computeSummary(
            state = uiState.smartEarningsState.copy(historyFilter = EarningsHistoryFilter.EXPENSE),
            todayKey = todayKey,
            todayLiveFuelDistanceKm = uiState.fuelState.distanceDrivenSinceRefuelKm
        ).filteredTransactions
        RiderIntelligenceEngine.computeExpenseBreakdown(periodAll)
    }

    var addDialogType by remember { mutableStateOf<EarningsRecordType?>(null) }
    var showGoalsDialog by remember { mutableStateOf(false) }
    var showWorkHoursDialog by remember { mutableStateOf(false) }

    addDialogType?.let { recordType ->
        AddTransactionDialog(
            type = recordType,
            dateKey = uiState.smartEarningsState.selectedDateKey,
            onDismiss = { addDialogType = null },
            onSave = { title, category, amount, dist, mins ->
                val ok = onAddTransaction(
                    recordType,
                    uiState.smartEarningsState.selectedDateKey,
                    title,
                    category,
                    amount,
                    dist,
                    mins
                )
                if (ok) addDialogType = null
            }
        )
    }

    if (showGoalsDialog) {
        EditGoalsDialog(
            daily = uiState.smartEarningsState.goals.dailyGoalBaht,
            weekly = uiState.smartEarningsState.goals.weeklyGoalBaht,
            monthly = uiState.smartEarningsState.goals.monthlyGoalBaht,
            onDismiss = { showGoalsDialog = false },
            onSave = { d, w, m ->
                onUpdateGoals(d, w, m)
                showGoalsDialog = false
            }
        )
    }

    if (showWorkHoursDialog) {
        val curMins = uiState.smartEarningsState.dailyWorkMetrics[uiState.smartEarningsState.selectedDateKey]?.workMinutes ?: 0
        EditWorkHoursDialog(
            dateKey = uiState.smartEarningsState.selectedDateKey,
            initialHours = curMins / 60.0,
            onDismiss = { showWorkHoursDialog = false },
            onSave = { hrs ->
                onUpdateDailyWorkHours(uiState.smartEarningsState.selectedDateKey, hrs)
                showWorkHoursDialog = false
            }
        )
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 720.dp)
                .testTag("earnings_analytics_screen_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Period Switcher (Daily / Weekly / Monthly) & Date Navigator
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceLevel1)
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        EarningsPeriod.entries.forEach { period ->
                            val selected = uiState.smartEarningsState.selectedPeriod == period
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) CrimsonRed else Color.Transparent)
                                    .clickable { onSelectPeriod(period) }
                                    .padding(vertical = 10.dp)
                                    .testTag("earnings_period_${period.name.lowercase(Locale.ROOT)}")
                            ) {
                                Text(
                                    text = period.labelTh,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = if (selected) Color.White else TextHudMuted,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceLevel2)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = { onShiftDate(-1) },
                            modifier = Modifier.testTag("btn_earnings_prev_period")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "ช่วงก่อนหน้า", tint = TextHudPrimary)
                        }
                        Text(
                            text = summary.periodLabelTh,
                            style = MaterialTheme.typography.titleSmall,
                            color = TextHudPrimary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable(onClick = onResetDateToToday)
                        )
                        IconButton(
                            onClick = { onShiftDate(1) },
                            modifier = Modifier.testTag("btn_earnings_next_period")
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "ช่วงถัดไป", tint = TextHudPrimary)
                        }
                    }
                }
            }

            // 2. Hero Financial Summary Card
            item {
                CrimsonHeroCard(modifier = Modifier.testTag("earnings_summary_hero_card")) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text("กำไรสุทธิ (${summary.period.labelTh})", style = MaterialTheme.typography.bodySmall, color = TextHudSecondary)
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "%s฿%,.0f",
                                    if (summary.netProfitBaht >= 0) "" else "-",
                                    kotlin.math.abs(summary.netProfitBaht)
                                ),
                                style = MaterialTheme.typography.displayLarge.copy(
                                    fontFamily = JetBrainsMonoFamily,
                                    fontSize = 34.sp
                                ),
                                color = if (summary.netProfitBaht >= 0) EmeraldSRank else CrimsonNeon,
                                modifier = Modifier.testTag("earnings_net_profit_value")
                            )
                        }
                        StatusPillBadge(
                            text = "${summary.jobCount} งาน / ${summary.expenseCount} จ่าย",
                            accentColor = CrimsonNeon
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x6607070A))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("รายรับรวม", style = MaterialTheme.typography.labelSmall, color = TextHudMuted)
                            Text(
                                text = String.format(Locale.US, "฿%,.0f", summary.totalIncomeBaht),
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
                                color = EmeraldSRank,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("รายจ่ายรวม", style = MaterialTheme.typography.labelSmall, color = TextHudMuted)
                            Text(
                                text = String.format(Locale.US, "฿%,.0f", summary.totalExpenseBaht),
                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFamily),
                                color = CrimsonNeon,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Goal progress
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = String.format(Locale.US, "เป้าหมาย: ฿%,.0f", summary.goalBaht),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextHudSecondary
                        )
                        Text(
                            text = "ปรับเป้าหมาย",
                            style = MaterialTheme.typography.labelSmall,
                            color = CrimsonNeon,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { showGoalsDialog = true }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .testTag("btn_edit_earnings_goals")
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(SurfaceElevated)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(summary.goalProgressFraction.coerceIn(0.02f, 1f))
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (summary.isGoalReached) EmeraldSRank else CrimsonRed)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PremiumPrimaryButton(
                            text = "เพิ่มรายได้",
                            icon = Icons.Default.Add,
                            containerColor = EmeraldSRank,
                            onClick = { addDialogType = EarningsRecordType.INCOME },
                            modifier = Modifier.weight(1f),
                            testTagName = "btn_add_income_txn"
                        )
                        PremiumSecondaryButton(
                            text = "เพิ่มค่าใช้จ่าย",
                            icon = Icons.Default.LocalGasStation,
                            accentColor = CrimsonNeon,
                            onClick = { addDialogType = EarningsRecordType.EXPENSE },
                            modifier = Modifier.weight(1f),
                            testTagName = "btn_add_expense_txn"
                        )
                    }
                }
            }

            // 3. Performance KPIs 2x2
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricStatCard(
                            label = "รายได้/ชั่วโมง",
                            value = String.format(Locale.US, "฿%.0f", summary.avgIncomePerHour),
                            subtext = String.format(Locale.US, "%.1f ชม. (แตะปรับ)", summary.totalWorkHours),
                            icon = Icons.Default.Schedule,
                            accentColor = CyanTelemetry,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showWorkHoursDialog = true }
                                .testTag("card_work_hours_kpi")
                        )
                        MetricStatCard(
                            label = "รายได้/กม.",
                            value = String.format(Locale.US, "฿%.1f", summary.avgIncomePerKm),
                            subtext = String.format(Locale.US, "รวม %.1f กม.", summary.totalDistanceKm),
                            icon = Icons.Default.Route,
                            accentColor = EmeraldSRank,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MetricStatCard(
                            label = "ต้นทุน/กม.",
                            value = String.format(Locale.US, "฿%.2f", summary.costPerKm),
                            subtext = "รวมน้ำมัน & ค่าใช้จ่าย",
                            icon = Icons.Default.Speed,
                            accentColor = AmberWarning,
                            modifier = Modifier.weight(1f)
                        )
                        MetricStatCard(
                            label = "เป้าหมายคงเหลือ",
                            value = if (summary.isGoalReached) "ครบเป้า!" else String.format(Locale.US, "฿%,.0f", summary.remainingToGoalBaht),
                            subtext = String.format(Locale.US, "สำเร็จ %.0f%%", summary.goalProgressFraction * 100f),
                            icon = Icons.Default.Flag,
                            accentColor = VioletInsight,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // 4. Income vs Expense Chart
            item {
                IncomeExpenseChartCard(buckets = summary.chartBuckets)
            }

            // 5. Expense Breakdown Chart
            item {
                ExpenseBreakdownCard(breakdown = expenseBreakdown)
            }

            // 6. Transaction Ledger with Filter Chips
            item {
                SectionHeaderRow(
                    title = "ประวัติรายการรับงานและค่าใช้จ่าย",
                    subtitle = "ข้อมูลบันทึกถาวรในฐานข้อมูล Room ออฟไลน์"
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EarningsHistoryFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = uiState.smartEarningsState.historyFilter == filter,
                            onClick = { onSelectHistoryFilter(filter) },
                            label = { Text(filter.labelTh) },
                            modifier = Modifier.testTag("filter_history_${filter.name.lowercase(Locale.ROOT)}")
                        )
                    }
                }
            }

            if (summary.filteredTransactions.isEmpty()) {
                item {
                    PremiumCard {
                        Text(
                            text = "ยังไม่มีรายการในช่วงเวลานี้ — กด 'เพิ่มรายได้' หรือ 'เพิ่มค่าใช้จ่าย' เพื่อเริ่มต้น",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextHudMuted
                        )
                    }
                }
            } else {
                items(summary.filteredTransactions.size, key = { summary.filteredTransactions[it].id }) { idx ->
                    TransactionRowCard(
                        txn = summary.filteredTransactions[idx],
                        onDelete = { onDeleteTransaction(summary.filteredTransactions[idx].id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionRowCard(
    txn: EarningsTransaction,
    onDelete: () -> Unit
) {
    val isIncome = txn.type == EarningsRecordType.INCOME
    val accent = if (isIncome) EmeraldSRank else CrimsonNeon
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("txn_row_${txn.id}"),
        shape = RoundedCornerShape(16.dp),
        color = SurfaceLevel1,
        border = BorderStroke(1.dp, HairlineBorder)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.16f))
            ) {
                Icon(
                    imageVector = if (isIncome) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = txn.type.labelTh,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = txn.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextHudPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(txn.category)
                        append(" • ")
                        append(txn.dateKey)
                        if (txn.distanceKm > 0.0) append(String.format(Locale.US, " • %.1f กม.", txn.distanceKm))
                        if (txn.durationMinutes > 0) append(" • ${txn.durationMinutes} น.")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextHudMuted
                )
            }
            Text(
                text = String.format(Locale.US, "%s฿%,.0f", if (isIncome) "+" else "-", txn.amountBaht),
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFamily),
                color = accent,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "ลบรายการ",
                    tint = TextHudMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun AddTransactionDialog(
    type: EarningsRecordType,
    dateKey: String,
    onDismiss: () -> Unit,
    onSave: (title: String, category: String, amount: Double, distKm: Double, durationMins: Int) -> Unit
) {
    val defaultCategories = if (type == EarningsRecordType.INCOME) {
        listOf("รับงาน", "ทิป", "โบนัส/อินเซนทีฟ")
    } else {
        listOf("น้ำมัน", "อาหาร/เครื่องดื่ม", "ซ่อมบำรุง", "ค่าโทรศัพท์", "อื่นๆ")
    }
    var title by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(defaultCategories.first()) }
    var amountText by remember { mutableStateOf("") }
    var distText by remember { mutableStateOf("") }
    var minsText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceLevel1,
        titleContentColor = TextHudPrimary,
        textContentColor = TextHudSecondary,
        title = {
            Text(
                text = if (type == EarningsRecordType.INCOME) "บันทึกรายได้ ($dateKey)" else "บันทึกค่าใช้จ่าย ($dateKey)",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    defaultCategories.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("รายละเอียด (เช่น งาน Grab / เติม E20)") },
                    singleLine = true,
                    colors = crimsonFieldColors(),
                    modifier = Modifier.fillMaxWidth().testTag("input_txn_title")
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("จำนวนเงิน (บาท) *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = crimsonFieldColors(),
                    modifier = Modifier.fillMaxWidth().testTag("input_txn_amount")
                )
                if (type == EarningsRecordType.INCOME) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = distText,
                            onValueChange = { distText = it },
                            label = { Text("ระยะ (กม.)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = crimsonFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = minsText,
                            onValueChange = { minsText = it },
                            label = { Text("เวลา (นาที)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = crimsonFieldColors(),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            PremiumPrimaryButton(
                text = "บันทึก",
                enabled = (amountText.toDoubleOrNull() ?: 0.0) > 0.0,
                onClick = {
                    val amt = amountText.toDoubleOrNull() ?: return@PremiumPrimaryButton
                    onSave(
                        title,
                        category,
                        amt,
                        distText.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0,
                        minsText.toIntOrNull()?.coerceAtLeast(0) ?: 0
                    )
                },
                testTagName = "btn_confirm_add_txn"
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ยกเลิก", color = TextHudMuted) }
        }
    )
}

@Composable
private fun EditGoalsDialog(
    daily: Double,
    weekly: Double,
    monthly: Double,
    onDismiss: () -> Unit,
    onSave: (Double, Double, Double) -> Unit
) {
    var d by remember { mutableStateOf(daily.toInt().toString()) }
    var w by remember { mutableStateOf(weekly.toInt().toString()) }
    var m by remember { mutableStateOf(monthly.toInt().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceLevel1,
        titleContentColor = TextHudPrimary,
        textContentColor = TextHudSecondary,
        title = { Text("ตั้งเป้าหมายรายได้", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = d,
                    onValueChange = { d = it },
                    label = { Text("เป้ารายวัน (บาท)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = crimsonFieldColors()
                )
                OutlinedTextField(
                    value = w,
                    onValueChange = { w = it },
                    label = { Text("เป้ารายสัปดาห์ (บาท)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = crimsonFieldColors()
                )
                OutlinedTextField(
                    value = m,
                    onValueChange = { m = it },
                    label = { Text("เป้ารายเดือน (บาท)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = crimsonFieldColors()
                )
            }
        },
        confirmButton = {
            PremiumPrimaryButton(
                text = "บันทึกเป้า",
                onClick = {
                    onSave(
                        d.toDoubleOrNull() ?: daily,
                        w.toDoubleOrNull() ?: weekly,
                        m.toDoubleOrNull() ?: monthly
                    )
                },
                testTagName = "btn_confirm_goals"
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ยกเลิก", color = TextHudMuted) }
        }
    )
}

@Composable
private fun EditWorkHoursDialog(
    dateKey: String,
    initialHours: Double,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit
) {
    var hoursText by remember { mutableStateOf(String.format(Locale.US, "%.1f", initialHours)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceLevel1,
        titleContentColor = TextHudPrimary,
        textContentColor = TextHudSecondary,
        title = { Text("ชั่วโมงทำงาน ($dateKey)", fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = hoursText,
                onValueChange = { hoursText = it },
                label = { Text("จำนวนชั่วโมง (เช่น 6.5)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                colors = crimsonFieldColors()
            )
        },
        confirmButton = {
            PremiumPrimaryButton(
                text = "บันทึก",
                onClick = { onSave(hoursText.toDoubleOrNull() ?: initialHours) }
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("ยกเลิก", color = TextHudMuted) }
        }
    )
}
