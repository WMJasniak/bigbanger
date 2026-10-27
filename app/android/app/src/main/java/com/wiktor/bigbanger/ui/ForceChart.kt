package com.wiktor.bigbanger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.wiktor.bigbanger.ble.WeightSample

/** Fixed scrolling window, not "stretch to fit all data so far" -- the latter made the
 * whole trace visibly compress on every new sample, which read as choppy/jittery. */
private const val DEFAULT_WINDOW_SECONDS = 12f

@Composable
fun ForceChart(
    samples: List<WeightSample>,
    targetRangeKg: ClosedFloatingPointRange<Float>? = null,
    windowSeconds: Float = DEFAULT_WINDOW_SECONDS,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val bandColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)

    Canvas(modifier = modifier.fillMaxWidth().height(220.dp)) {
        val latestTime = samples.lastOrNull()?.timestampSeconds ?: 0f
        val windowStart = (latestTime - windowSeconds).coerceAtLeast(0f)
        val visible = samples.filter { it.timestampSeconds >= windowStart }

        val sampleMin = visible.minOfOrNull { it.kilograms }
        val sampleMax = visible.maxOfOrNull { it.kilograms }
        val minWeight = minOf(0f, sampleMin ?: 0f, targetRangeKg?.start ?: 0f)
        val maxWeight = maxOf(
            minWeight + 1f,
            sampleMax ?: (minWeight + 1f),
            targetRangeKg?.endInclusive ?: (minWeight + 1f),
        )

        fun yOf(kg: Float) = size.height - (kg - minWeight) / (maxWeight - minWeight) * size.height
        fun xOf(t: Float) = ((t - windowStart) / windowSeconds * size.width).coerceIn(0f, size.width)

        // Horizontal gridlines, including the zero line.
        val gridLines = 4
        for (i in 0..gridLines) {
            val y = size.height * i / gridLines
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        if (minWeight < 0f && maxWeight > 0f) {
            val zeroY = yOf(0f)
            drawLine(gridColor, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 2f)
        }

        targetRangeKg?.let { range ->
            val top = yOf(range.endInclusive)
            val bottom = yOf(range.start)
            drawRect(color = bandColor, topLeft = Offset(0f, top), size = Size(size.width, bottom - top))
        }

        if (visible.size >= 2) {
            var previous = visible.first()
            for (sample in visible.drop(1)) {
                drawLine(
                    color = lineColor,
                    start = Offset(xOf(previous.timestampSeconds), yOf(previous.kilograms)),
                    end = Offset(xOf(sample.timestampSeconds), yOf(sample.kilograms)),
                    strokeWidth = 4f,
                    cap = StrokeCap.Round,
                )
                previous = sample
            }
        }
    }
}
