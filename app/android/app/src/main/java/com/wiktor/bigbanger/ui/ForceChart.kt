package com.wiktor.bigbanger.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.wiktor.bigbanger.ble.WeightSample
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Fixed scrolling window, not "stretch to fit all data so far" -- the latter made the
 * whole trace visibly compress on every new sample, which read as choppy/jittery. */
private const val DEFAULT_WINDOW_SECONDS = 12f

@Composable
fun ForceChart(
    samples: List<WeightSample>,
    targetRangeKg: ClosedFloatingPointRange<Float>? = null,
    /** True while a WORK step is actively measuring. Drives a vertical marker at the
     * live edge: full height while working, animating down to nothing once it stops --
     * so it reads as a pulse settling, not an abrupt cut. */
    isWorking: Boolean = false,
    windowSeconds: Float = DEFAULT_WINDOW_SECONDS,
    modifier: Modifier = Modifier,
) {
    val panelColor = MaterialTheme.colorScheme.surfaceVariant
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val bandFillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
    val bandEdgeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val markerHeightFraction by animateFloatAsState(targetValue = if (isWorking) 1f else 0f, label = "forceChartMarker")

    // Keeps the window scrolling at a real-time pace even with no new samples (e.g.
    // during REST/PREP, when nothing is being measured) instead of freezing on the last
    // sample's device timestamp. Resets to 0 every time a genuinely new sample arrives.
    val lastSample = samples.lastOrNull()
    val idleElapsedSeconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(lastSample) {
        idleElapsedSeconds.floatValue = 0f
        val startNanos = System.nanoTime()
        while (isActive) {
            delay(100)
            idleElapsedSeconds.floatValue = (System.nanoTime() - startNanos) / 1_000_000_000f
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRoundRect(color = panelColor, cornerRadius = CornerRadius(20f, 20f))

        val latestTime = (lastSample?.timestampSeconds ?: 0f) + idleElapsedSeconds.floatValue
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
            drawLine(gridColor, Offset(24f, y), Offset(size.width - 24f, y), strokeWidth = 1.5f)
        }
        if (minWeight < 0f && maxWeight > 0f) {
            val zeroY = yOf(0f)
            drawLine(gridColor, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 2f)
        }

        targetRangeKg?.let { range ->
            val top = yOf(range.endInclusive)
            val bottom = yOf(range.start)
            drawRect(color = bandFillColor, topLeft = Offset(0f, top), size = Size(size.width, bottom - top))
            drawLine(bandEdgeColor, Offset(0f, top), Offset(size.width, top), strokeWidth = 2f)
            drawLine(bandEdgeColor, Offset(0f, bottom), Offset(size.width, bottom), strokeWidth = 2f)
        }

        if (markerHeightFraction > 0.01f) {
            val markerX = xOf(latestTime)
            val markerHeight = size.height * markerHeightFraction
            drawLine(
                lineColor.copy(alpha = 0.5f),
                Offset(markerX, size.height),
                Offset(markerX, size.height - markerHeight),
                strokeWidth = 6f,
            )
        }

        if (visible.size >= 2) {
            val linePath = Path()
            val fillPath = Path()
            var started = false
            for (sample in visible) {
                val x = xOf(sample.timestampSeconds)
                val y = yOf(sample.kilograms)
                if (!started) {
                    linePath.moveTo(x, y)
                    fillPath.moveTo(x, size.height)
                    fillPath.lineTo(x, y)
                    started = true
                } else {
                    linePath.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            fillPath.lineTo(xOf(visible.last().timestampSeconds), size.height)
            fillPath.close()

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.35f), lineColor.copy(alpha = 0f))),
            )
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )

            val last = visible.last()
            drawCircle(color = lineColor, radius = 7f, center = Offset(xOf(last.timestampSeconds), yOf(last.kilograms)))
        }
    }
}
