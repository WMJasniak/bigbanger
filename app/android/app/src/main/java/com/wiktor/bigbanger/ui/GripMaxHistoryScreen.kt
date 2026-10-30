package com.wiktor.bigbanger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wiktor.bigbanger.BigBangerViewModel
import com.wiktor.bigbanger.training.GripVariant
import com.wiktor.bigbanger.training.Hand
import com.wiktor.bigbanger.training.MaxTestResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Okabe-Ito's blue/orange pair -- a categorical pair that stays distinguishable under
// every common form of colorblindness, not just normal vision.
private val LEFT_HAND_COLOR = Color(0xFF0072B2)
private val RIGHT_HAND_COLOR = Color(0xFFE69F00)

private val axisDateFormat = SimpleDateFormat("MMM d", Locale.getDefault())

/** All of one grip's max-testing history, both hands, as peak kg vs date -- the
 * cleanup (deleting a wrong reading) already happens right after the session that
 * recorded it, so this view is read-only. */
@Composable
fun GripMaxHistoryScreen(viewModel: BigBangerViewModel, variant: GripVariant, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val allResults by viewModel.trainingRepo.maxResults.collectAsStateWithLifecycle()
    val leftResults = remember(allResults, variant) {
        allResults.filter { it.gripVariantId == variant.id && it.hand == Hand.LEFT }.sortedBy { it.timestampMs }
    }
    val rightResults = remember(allResults, variant) {
        allResults.filter { it.gripVariantId == variant.id && it.hand == Hand.RIGHT }.sortedBy { it.timestampMs }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(variant.name, style = MaterialTheme.typography.titleLarge)
        Text(
            "Max pull vs date",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        if (leftResults.isEmpty() && rightResults.isEmpty()) {
            Text("No max tests recorded yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            MaxHistoryChart(
                leftResults = leftResults,
                rightResults = rightResults,
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                if (leftResults.isNotEmpty()) LegendEntry(LEFT_HAND_COLOR, "Left hand")
                if (leftResults.isNotEmpty() && rightResults.isNotEmpty()) Spacer(Modifier.width(24.dp))
                if (rightResults.isNotEmpty()) LegendEntry(RIGHT_HAND_COLOR, "Right hand")
            }
        }
    }
}

@Composable
private fun LegendEntry(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color = color, shape = CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MaxHistoryChart(leftResults: List<MaxTestResult>, rightResults: List<MaxTestResult>, modifier: Modifier = Modifier) {
    val panelColor = MaterialTheme.colorScheme.surfaceVariant
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val axisTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()

    val all = leftResults + rightResults
    val minTime = all.minOf { it.timestampMs }
    val maxTime = all.maxOf { it.timestampMs }
    val minPeak = all.minOf { it.peakKg }
    val maxPeak = all.maxOf { it.peakKg }
    val rawMin = listOf(0f, minPeak).min()
    val margin = ((maxPeak - rawMin) * 0.15f).coerceAtLeast(0.2f)
    val minWeight = rawMin - margin
    val maxWeight = maxPeak + margin

    Canvas(modifier = modifier) {
        drawRoundRect(color = panelColor, cornerRadius = CornerRadius(20f, 20f))

        val leftPad = 16f
        val rightPad = 16f
        val topPad = 28f
        val bottomPad = 36f
        val plotWidth = (size.width - leftPad - rightPad).coerceAtLeast(1f)
        val plotHeight = (size.height - topPad - bottomPad).coerceAtLeast(1f)

        fun xOf(timeMs: Long): Float =
            if (maxTime == minTime) leftPad + plotWidth / 2f else leftPad + (timeMs - minTime).toFloat() / (maxTime - minTime) * plotWidth
        fun yOf(kg: Float): Float = topPad + plotHeight - (kg - minWeight) / (maxWeight - minWeight) * plotHeight

        val gridLines = 3
        for (i in 0..gridLines) {
            val y = topPad + plotHeight * i / gridLines
            drawLine(gridColor, Offset(leftPad, y), Offset(size.width - rightPad, y), strokeWidth = 1.5f)
        }

        fun drawSeries(results: List<MaxTestResult>, color: Color) {
            if (results.size >= 2) {
                val path = Path()
                results.forEachIndexed { i, r ->
                    val x = xOf(r.timestampMs)
                    val y = yOf(r.peakKg)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path = path, color = color, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            results.forEach { r -> drawCircle(color = color, radius = 9f, center = Offset(xOf(r.timestampMs), yOf(r.peakKg))) }
        }
        drawSeries(leftResults, LEFT_HAND_COLOR)
        drawSeries(rightResults, RIGHT_HAND_COLOR)

        // Selective direct labels -- just the latest reading of each series, not every point.
        fun labelLatest(results: List<MaxTestResult>, color: Color, above: Boolean) {
            val latest = results.maxByOrNull { it.timestampMs } ?: return
            val layout = textMeasurer.measure(
                AnnotatedString("${"%.1f".format(latest.peakKg)} kg"),
                style = TextStyle(fontSize = 12.sp, color = color, fontWeight = FontWeight.Bold),
            )
            val x = xOf(latest.timestampMs).coerceIn(layout.size.width / 2f, size.width - layout.size.width / 2f)
            val y = yOf(latest.peakKg)
            val labelY = if (above) y - layout.size.height - 10f else y + 10f
            drawText(layout, topLeft = Offset(x - layout.size.width / 2f, labelY))
        }
        labelLatest(leftResults, LEFT_HAND_COLOR, above = true)
        labelLatest(rightResults, RIGHT_HAND_COLOR, above = false)

        // X-axis: just the earliest and latest date, not one label per point.
        val startLabel = textMeasurer.measure(AnnotatedString(axisDateFormat.format(Date(minTime))), style = TextStyle(fontSize = 11.sp, color = axisTextColor))
        drawText(startLabel, topLeft = Offset(leftPad, size.height - bottomPad + 8f))
        if (maxTime != minTime) {
            val endLabel = textMeasurer.measure(AnnotatedString(axisDateFormat.format(Date(maxTime))), style = TextStyle(fontSize = 11.sp, color = axisTextColor))
            drawText(endLabel, topLeft = Offset(size.width - rightPad - endLabel.size.width, size.height - bottomPad + 8f))
        }
    }
}
