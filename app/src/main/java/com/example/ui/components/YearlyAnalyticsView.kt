package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AnalyticsUiState
import com.example.ui.MonthChartItem
import com.example.ui.theme.BentoBackground
import com.example.ui.theme.BentoHeroContainer
import com.example.ui.theme.BentoPrimary
import com.example.ui.theme.BentoStreakIconBg
import com.example.ui.theme.BentoStreakText
import com.example.ui.theme.BentoTextMuted
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import com.example.ui.theme.ProposalPanel
import com.example.ui.theme.ProposalSegment
import com.example.ui.theme.ProposalSegmentText
import com.example.ui.theme.ProposalSelectedNav
import com.example.ui.theme.ProposalSub
import com.example.util.TimeFormatUtils
import java.util.Locale

/**
 * Insights tab in the proposal vibe: segmented year control, year total first,
 * stat tiles, monthly strip, milestones, and streak.
 */
@Composable
fun YearlyAnalyticsView(
    state: AnalyticsUiState,
    onSelectYear: (Int) -> Unit,
    onSeedData: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalSeconds = state.yearTotalSeconds
    val fullDays = totalSeconds / 86_400L
    val unlocked = state.milestones.count { it.isUnlocked }

    Column(
        modifier = modifier.fillMaxWidth().background(BentoBackground).testTag("yearly_analytics_view"),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        ProposalTitle(
            title = "Insights",
            subtitle = "Totals, streaks, and milestones for the year."
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .background(ProposalSegment)
                .padding(3.dp)
                .horizontalScroll(rememberScrollState())
                .selectableGroup()
                .testTag("year_selector_card"),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            state.availableYears.forEach { year ->
                val selected = year == state.selectedYear
                val shape = RoundedCornerShape(10.dp)
                Box(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .then(if (selected) Modifier.shadow(2.dp, shape) else Modifier)
                        .clip(shape)
                        .background(if (selected) Color.White else Color.Transparent)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelectYear(year) })
                        .padding(horizontal = 14.dp)
                        .testTag("select_year_$year"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "$year",
                        color = if (selected) ProposalSelectedNav else ProposalSegmentText,
                        fontSize = 13.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    ProposalTotal(
                        TimeFormatUtils.formatCompactDuration(totalSeconds),
                        numberSize = 40.sp,
                        unitSize = 19.sp
                    )
                    Spacer(Modifier.height(7.dp))
                    Text("${state.selectedYear} listening time", color = ProposalSub, fontSize = 12.sp)
                }
                if (state.peakMonthName != "-") {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(BentoHeroContainer)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text("Peak month", color = BentoTextSecondary, fontSize = 10.sp)
                        Text(
                            "${state.peakMonthName.take(3)} · " +
                                TimeFormatUtils.formatCompactDuration((state.peakMonthHours * 3600f).toLong()),
                            color = BentoPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InsightStat("Active days", "${state.yearActiveDays}")
                InsightStat("Avg per active day", "${state.yearAverageMinutesPerDay}m")
                InsightStat("Full days", "${fullDays}d")
            }

            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(BentoStreakIconBg.copy(alpha = 0.25f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = BentoStreakText, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Current streak", color = BentoTextSecondary, fontSize = 12.sp)
                Spacer(Modifier.weight(1f))
                Text(
                    "${state.currentStreakDays} ${if (state.currentStreakDays == 1) "day" else "days"}",
                    color = BentoStreakText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (totalSeconds == 0L) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ProposalPanel)
                    .padding(16.dp)
                    .testTag("yearly_empty_state"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "No history for ${state.selectedYear} yet",
                    color = BentoTextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text("Load sample data to preview yearly insights.", color = BentoTextSecondary, fontSize = 12.sp)
                Button(
                    onClick = onSeedData,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .semantics { contentDescription = "Seed Sample Data" },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BentoPrimary)
                ) {
                    Text("Load sample data", fontWeight = FontWeight.SemiBold)
                }
            }
        }

        if (state.monthlyBreakdown.any { it.totalSeconds > 0L }) {
            var selectedMonthNumber by remember(state.selectedYear) {
                mutableStateOf(
                    state.monthlyBreakdown
                        .firstOrNull { it.isCurrentMonth && it.totalSeconds > 0L }?.monthNumber
                        ?: state.monthlyBreakdown.maxByOrNull { it.totalSeconds }?.monthNumber
                        ?: 1
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                ProposalSectionHead(
                    title = "Monthly breakdown",
                    count = "${state.selectedYear}"
                )
                Spacer(Modifier.height(14.dp))
                MonthPicker(
                    months = state.monthlyBreakdown,
                    year = state.selectedYear,
                    yearTotalSeconds = totalSeconds,
                    selectedMonthNumber = selectedMonthNumber,
                    onSelectMonth = { selectedMonthNumber = it }
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(BentoHeroContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.EmojiEvents, contentDescription = null, tint = BentoPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Listening milestones", color = BentoTextPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (state.milestones.isEmpty()) "Progress toward each listening goal"
                        else "$unlocked of ${state.milestones.size} unlocked",
                        color = BentoTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            state.milestones.forEachIndexed { index, milestone ->
                if (index > 0) ProposalDividerLine()
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (milestone.isUnlocked) Icons.Default.Star else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (milestone.isUnlocked) BentoStreakText else BentoTextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(milestone.title, color = BentoTextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            Text(milestone.description, color = BentoTextSecondary, fontSize = 11.sp)
                        }
                        Text("${milestone.requiredHours}h", color = BentoTextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    ProposalProgressBar(
                        progress = milestone.progressFraction,
                        height = 5.dp,
                        color = if (milestone.isUnlocked) BentoStreakText else BentoPrimary
                    )
                }
            }
        }
    }
}

/**
 * Month picker: a field showing the chosen month and its total.
 * Tapping it expands an inline list of all twelve months; picking one
 * shows that month's detail below. Inline (not a popup) so the options
 * exist only while expanded.
 */
@Composable
private fun MonthPicker(
    months: List<MonthChartItem>,
    year: Int,
    yearTotalSeconds: Long,
    selectedMonthNumber: Int,
    onSelectMonth: (Int) -> Unit
) {
    val selected = months.firstOrNull { it.monthNumber == selectedMonthNumber } ?: months.first()
    var expanded by remember { mutableStateOf(false) }
    val share = if (yearTotalSeconds == 0L) 0f else selected.totalSeconds.toFloat() / yearTotalSeconds
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(ProposalPanel)
                .clickable { expanded = !expanded }
                .heightIn(min = 48.dp)
                .padding(horizontal = 14.dp)
                .testTag("month_picker_field"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
                Text(
                    selected.monthName,
                    color = BentoTextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = BentoTextSecondary,
                    modifier = Modifier.size(20.dp)
                )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(ProposalPanel)
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                months.forEachIndexed { index, month ->
                    if (index > 0) ProposalDividerLine()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelectMonth(month.monthNumber)
                                expanded = false
                            }
                            .heightIn(min = 48.dp)
                            .padding(horizontal = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            month.monthName,
                            color = BentoTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = if (month.monthNumber == selectedMonthNumber) {
                                FontWeight.Bold
                            } else {
                                FontWeight.Normal
                            }
                        )
                        Text(
                            TimeFormatUtils.formatCompactDuration(month.totalSeconds),
                            color = BentoTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        if (selected.totalSeconds == 0L) {
            Text(
                "No listening recorded in ${selected.monthName}.",
                color = BentoTextSecondary,
                fontSize = 12.sp
            )
        } else {
            ProposalTotal(
                TimeFormatUtils.formatCompactDuration(selected.totalSeconds),
                numberSize = 32.sp,
                unitSize = 15.sp
            )
            Spacer(Modifier.height(7.dp))
            Text(
                "${selected.activeDays} ${if (selected.activeDays == 1) "active day" else "active days"} · " +
                    String.format(Locale.US, "%.1f%%", share * 100f) + " of $year",
                color = ProposalSub,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(10.dp))
            ProposalProgressBar(progress = share, height = 5.dp)
        }
    }
}

@Composable
private fun RowScope.InsightStat(label: String, value: String) {
    Column(
        modifier = Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(ProposalPanel).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(value, color = BentoTextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Text(label, color = BentoTextSecondary, fontSize = 10.sp, minLines = 2)
    }
}
