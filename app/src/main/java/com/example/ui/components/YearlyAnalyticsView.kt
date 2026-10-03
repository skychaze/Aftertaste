package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AnalyticsUiState
import com.example.ui.MonthChartItem
import com.example.ui.theme.*
import com.example.util.TimeFormatUtils
import java.util.Locale

@Composable
fun YearlyAnalyticsView(
    state: AnalyticsUiState,
    onSelectYear: (Int) -> Unit,
    onSeedData: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var yearPickerOpen by remember { mutableStateOf(false) }
    var chosenMonth by rememberSaveable(state.selectedYear) { mutableStateOf<Int?>(null) }
    val selectedMonth =
        chosenMonth
            ?: state.monthlyBreakdown
                .firstOrNull { it.isCurrentMonth && it.totalSeconds > 0L }
                ?.monthNumber
            ?: state.monthlyBreakdown.maxByOrNull { it.totalSeconds }?.monthNumber
            ?: 1
    Column(
        modifier.fillMaxWidth().testTag("yearly_analytics_view"),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProposalTitle("Insights", "When music fits into your life.", Modifier.weight(1f))
            Box {
                OutlinedButton(
                    onClick = { yearPickerOpen = true },
                    modifier = Modifier.testTag("year_picker"),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text("${state.selectedYear}")
                    Icon(Icons.Default.ExpandMore, "Choose year", Modifier.size(18.dp))
                }
                DropdownMenu(
                    expanded = yearPickerOpen,
                    onDismissRequest = { yearPickerOpen = false },
                ) {
                    state.availableYears.forEach { year ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    "$year",
                                    fontWeight =
                                        if (year == state.selectedYear) FontWeight.Bold
                                        else FontWeight.Normal,
                                )
                            },
                            onClick = {
                                onSelectYear(year)
                                yearPickerOpen = false
                            },
                            modifier = Modifier.testTag("select_year_$year"),
                        )
                    }
                }
            }
        }
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(BentoHeroContainer)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Time with music", fontSize = 14.sp, color = BentoTextSecondary)
            ProposalTotal(
                TimeFormatUtils.formatCompactDuration(state.yearTotalSeconds),
                numberSize = 42.sp,
                unitSize = 20.sp,
            )
            Text(
                "Across ${state.yearActiveDays} listening days in ${state.selectedYear}",
                fontSize = 14.sp,
                color = BentoTextSecondary,
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${state.yearAverageMinutesPerDay}m",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = BentoTextPrimary,
                )
                Text(
                    "Average on days you listened",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = BentoTextSecondary,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${state.currentStreakDays} ${if (state.currentStreakDays == 1) "day" else "days"}",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = BentoTextPrimary,
                )
                Text(
                    "Current listening streak",
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = BentoTextSecondary,
                )
            }
        }
        if (state.yearTotalSeconds == 0L) {
            JournalEmptyState(
                "No history for ${state.selectedYear}",
                "Your listening patterns will appear as you listen. Choose another year or preview with sample history.",
                Modifier.testTag("yearly_empty_state"),
            )
            TextButton(
                onClick = onSeedData,
                modifier = Modifier.semantics { contentDescription = "Seed Sample Data" },
            ) {
                Text("Explore sample history")
            }
        } else {
            if (state.monthlyBreakdown.any { it.totalSeconds > 0L }) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ProposalSectionHead("Your listening rhythm", "${state.selectedYear}")
                    Text(
                        "Tap a month to inspect its listening time.",
                        fontSize = 13.sp,
                        color = BentoTextSecondary,
                    )
                    MonthlyRhythm(state.monthlyBreakdown, selectedMonth, { chosenMonth = it })
                    MonthDetail(
                        state.monthlyBreakdown,
                        state.selectedYear,
                        state.yearTotalSeconds,
                        selectedMonth,
                        { chosenMonth = it },
                    )
                }
            }
            if (state.weekdaySeconds.sum() > 0L) {
                val names =
                    listOf(
                        "Monday",
                        "Tuesday",
                        "Wednesday",
                        "Thursday",
                        "Friday",
                        "Saturday",
                        "Sunday",
                    )
                val favouriteDay =
                    state.weekdaySeconds.indices.maxByOrNull { state.weekdaySeconds[it] } ?: 0
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ProposalSectionHead("A week in your listening", "By listening time")
                    Text(
                        "${names[favouriteDay]} has the most listening time in ${state.selectedYear}.",
                        fontSize = 14.sp,
                        lineHeight = 21.sp,
                        color = BentoTextSecondary,
                    )
                    val maximum = state.weekdaySeconds.maxOrNull()?.coerceAtLeast(1L) ?: 1L
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        state.weekdaySeconds.forEachIndexed { day, seconds ->
                            Column(
                                Modifier.weight(1f).semantics(mergeDescendants = true) {
                                    contentDescription =
                                        "${names[day]}, ${TimeFormatUtils.formatCompactDuration(seconds)}"
                                },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    Modifier.width(22.dp).height(76.dp),
                                    contentAlignment = Alignment.BottomCenter,
                                ) {
                                    Box(
                                        Modifier.fillMaxWidth()
                                            .height((4 + 72f * seconds / maximum).dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                if (day == favouriteDay) Color(0xFF197A72)
                                                else Color(0xFFBCDCD0)
                                            )
                                    )
                                }
                                Text(
                                    names[day].take(3),
                                    fontSize = 11.sp,
                                    color = BentoTextSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyRhythm(
    months: List<MonthChartItem>,
    selectedMonth: Int,
    onSelectMonth: (Int) -> Unit,
) {
    val maximum = months.maxOfOrNull { it.totalSeconds }?.coerceAtLeast(1L) ?: 1L
    val scrollState = rememberScrollState()
    val initialScroll =
        with(LocalDensity.current) {
            (56.dp * (months.indexOfFirst { it.monthNumber == selectedMonth } - 2).coerceAtLeast(0))
                .roundToPx()
        }
    LaunchedEffect(Unit) { scrollState.scrollTo(initialScroll) }
    Row(
        Modifier.fillMaxWidth().horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        months.forEach { month ->
            Column(
                Modifier.width(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(role = Role.Button) { onSelectMonth(month.monthNumber) }
                    .padding(vertical = 8.dp)
                    .semantics(mergeDescendants = true) {
                        contentDescription =
                            "${month.monthName}, ${TimeFormatUtils.formatCompactDuration(month.totalSeconds)}"
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    Modifier.width(24.dp).height(88.dp),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(
                        Modifier.fillMaxWidth()
                            .height((4 + 84f * month.totalSeconds / maximum).dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (month.monthNumber == selectedMonth) Color(0xFFEBA67E)
                                else BentoPrimary.copy(alpha = 0.75f)
                            )
                    )
                }
                Text(month.monthName, fontSize = 12.sp, color = BentoTextSecondary)
            }
        }
    }
}

@Composable
private fun MonthDetail(
    months: List<MonthChartItem>,
    year: Int,
    total: Long,
    selectedMonth: Int,
    onSelectMonth: (Int) -> Unit,
) {
    val selected = months.firstOrNull { it.monthNumber == selectedMonth } ?: months.first()
    var pickerOpen by remember { mutableStateOf(false) }
    val share = if (total == 0L) 0f else selected.totalSeconds.toFloat() / total
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box {
            TextButton(
                onClick = { pickerOpen = true },
                modifier = Modifier.testTag("month_picker_field"),
                contentPadding = PaddingValues(horizontal = 0.dp),
            ) {
                Text("${selected.monthName} $year", fontWeight = FontWeight.Bold)
                Icon(Icons.Default.ExpandMore, "Choose month", Modifier.size(18.dp))
            }
            DropdownMenu(pickerOpen, { pickerOpen = false }) {
                months.forEach { month ->
                    DropdownMenuItem(
                        text = { Text(month.monthName) },
                        onClick = {
                            onSelectMonth(month.monthNumber)
                            pickerOpen = false
                        },
                        modifier = Modifier.testTag("select_month_${month.monthNumber}"),
                    )
                }
            }
        }
        if (selected.totalSeconds == 0L) {
            Text(
                "No listening recorded in ${selected.monthName}.",
                fontSize = 14.sp,
                color = BentoTextSecondary,
            )
        } else {
            ProposalTotal(
                TimeFormatUtils.formatCompactDuration(selected.totalSeconds),
                numberSize = 30.sp,
                unitSize = 16.sp,
            )
            Text(
                "${selected.activeDays} listening days. ${String.format(Locale.getDefault(), "%.1f%%", share * 100f)} of your $year listening.",
                fontSize = 13.sp,
                color = BentoTextSecondary,
            )
        }
    }
}
