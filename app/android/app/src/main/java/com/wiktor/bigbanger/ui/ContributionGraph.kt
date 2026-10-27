package com.wiktor.bigbanger.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private val CELL_SIZE = 14.dp
private val CELL_GAP = 3.dp
private const val WEEKS_SHOWN = 20

/** GitHub-contributions-style grid: one cell per day, colored by session count that day. */
@Composable
fun ContributionGraph(sessionTimestampsMs: List<Long>, modifier: Modifier = Modifier) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }

    val counts = remember(sessionTimestampsMs) {
        sessionTimestampsMs
            .map { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
            .groupingBy { it }
            .eachCount()
    }

    // End the grid on the Saturday of the current week, start WEEKS_SHOWN weeks before that Sunday.
    val daysAfterSaturday = (6 - (today.dayOfWeek.value % 7)).toLong() // DayOfWeek.value: Mon=1..Sun=7
    val gridEnd = today.plusDays(daysAfterSaturday)
    val gridStart = gridEnd.minusWeeks((WEEKS_SHOWN - 1).toLong()).minusDays(6)
    val maxCount = counts.values.maxOrNull() ?: 0

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(CELL_GAP),
    ) {
        var weekStart = gridStart
        while (!weekStart.isAfter(gridEnd)) {
            Column(verticalArrangement = Arrangement.spacedBy(CELL_GAP)) {
                for (d in 0..6) {
                    val date = weekStart.plusDays(d.toLong())
                    val count = counts[date] ?: 0
                    DayCell(color = cellColor(count, maxCount, date.isAfter(today)))
                }
            }
            weekStart = weekStart.plusWeeks(1)
        }
    }
}

@Composable
private fun DayCell(color: Color) {
    Box(modifier = Modifier.size(CELL_SIZE).clip(RoundedCornerShape(3.dp)).background(color))
}

@Composable
private fun cellColor(count: Int, maxCount: Int, isFuture: Boolean): Color {
    val empty = MaterialTheme.colorScheme.outlineVariant
    if (isFuture) return empty.copy(alpha = 0.25f)
    if (count == 0) return empty.copy(alpha = 0.5f)
    val accent = MaterialTheme.colorScheme.primary
    val intensity = if (maxCount <= 1) 1f else 0.4f + 0.6f * (count.toFloat() / maxCount)
    return accent.copy(alpha = intensity.coerceIn(0.35f, 1f))
}
