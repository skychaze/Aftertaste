package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BentoTextPrimary
import com.example.ui.theme.BentoTextSecondary
import com.example.ui.theme.ProposalPanel
import com.example.util.TimeFormatUtils

/** Weekday totals for the selected year, with details only for the selected weekday. */
@Composable
fun WeekdayListeningView(
    year: Int,
    seconds: List<Long>,
    listeningDays: List<Int>,
    modifier: Modifier = Modifier,
) {
    val names = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    val totals = names.indices.map { seconds.getOrElse(it) { 0L }.coerceAtLeast(0L) }
    var chosenDay by rememberSaveable(year) { mutableStateOf<Int?>(null) }
    var explanationOpen by remember(year) { mutableStateOf(false) }
    val selectedDay = chosenDay ?: totals.indices.maxByOrNull { totals[it] } ?: 0
    val maximum = totals.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    val dayCount = listeningDays.getOrElse(selectedDay) { 0 }.coerceAtLeast(0)
    val dayName = names[selectedDay]
    val duration = weekdayDuration(totals[selectedDay])

    Column(modifier.fillMaxWidth().testTag("weekday_listening"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Listening by weekday", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = BentoTextPrimary)
        Text("Total listening time in $year", fontSize = 13.sp, color = BentoTextSecondary)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Keep touch targets usable on small screens and at larger font sizes.
            val dayWidth = maxOf(48.dp, (maxWidth - 24.dp) / 7)
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                totals.forEachIndexed { day, total ->
                    val isSelected = selectedDay == day
                    Column(
                        Modifier.width(dayWidth)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) Color(0xFFE4EEE6) else Color.Transparent)
                            .clickable(role = Role.Button) { chosenDay = day }
                            .padding(vertical = 8.dp)
                            .testTag("weekday_$day")
                            .semantics(mergeDescendants = true) {
                                selected = isSelected
                                contentDescription = "${names[day]}, ${weekdayDuration(total)} total listening in $year"
                            },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            weekdayDuration(total),
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                            minLines = 2,
                            color = if (isSelected) Color(0xFF197A72) else BentoTextSecondary,
                        )
                        Box(Modifier.width(22.dp).height(76.dp), contentAlignment = Alignment.BottomCenter) {
                            if (total > 0L) {
                                Box(
                                    Modifier.fillMaxWidth()
                                        .height(maxOf(2.dp, (76f * total / maximum).dp))
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (isSelected) Color(0xFF197A72) else Color(0xFFBCDCD0))
                                )
                            }
                        }
                        Text(names[day].take(3), fontSize = 11.sp, color = BentoTextSecondary)
                    }
                }
            }
        }
        Column(
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(ProposalPanel)
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)
                .testTag("weekday_detail"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("$dayName · $duration total", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = BentoTextPrimary)
            if (totals[selectedDay] == 0L) {
                Text("No listening recorded on ${dayName}s in $year.", fontSize = 13.sp, color = BentoTextSecondary)
            } else if (dayCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val average = weekdayDuration(totals[selectedDay] / dayCount)
                    Text(
                        "Listened on $dayCount ${if (dayCount == 1) dayName else "${dayName}s"} · $average average",
                        Modifier.weight(1f),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = BentoTextSecondary,
                    )
                    IconButton(onClick = { explanationOpen = true }, modifier = Modifier.testTag("weekday_average_info")) {
                        Icon(Icons.Outlined.Info, "How the weekday average is calculated", Modifier.size(18.dp), tint = BentoTextSecondary)
                    }
                }
            }
        }
    }
    if (explanationOpen) {
        AlertDialog(
            onDismissRequest = { explanationOpen = false },
            title = { Text("Average listening time") },
            text = {
                Text("Total listening time on ${dayName}s in $year divided by the number of ${dayName}s with recorded listening. Days without recorded listening do not count.")
            },
            confirmButton = { TextButton(onClick = { explanationOpen = false }) { Text("Got it") } },
        )
    }
}

private fun weekdayDuration(seconds: Long): String =
    TimeFormatUtils.formatCompactDuration(seconds).removeSuffix(" 0m")
