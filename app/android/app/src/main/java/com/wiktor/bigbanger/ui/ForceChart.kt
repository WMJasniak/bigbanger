package com.wiktor.bigbanger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.wiktor.bigbanger.training.TargetSegment
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Fixed scrolling window, not "stretch to fit all data so far" -- the latter made the
 * whole trace visibly compress on every new sample, which read as choppy/jittery. */
private const val DEFAULT_WINDOW_SECONDS = 12f

@Composable
fun ForceChart(
    samples: List<WeightSample>,
    /** A rolling target strip: tall over an upcoming/recent WORK step's band, collapsed
     * near zero over REST/PREP, scrolling at the same rate as the data. Empty = no strip. */
    targetTimeline: List<TargetSegment> = emptyList(),
    /** Where "now" sits across the chart's width, 0f (left edge) to 1f (right edge).
     * 1f (the default) matches a plain live reading: the latest point hugs the edge
     * and everything visible is past data. A smaller fraction (e.g. 1/3) leaves room
     * to the right for [targetTimeline]'s future segments, and keeps the live point
     * fixed in place -- with the data scrolling underneath it -- instead of pinned to
     * an edge. */
    anchorFraction: Float = 1f,
    windowSeconds: Float = DEFAULT_WINDOW_SECONDS,
    modifier: Modifier = Modifier,
) {
    val panelColor = MaterialTheme.colorScheme.surfaceVariant
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val bandFillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
    val bandEdgeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)

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

        val now = (lastSample?.timestampSeconds ?: 0f) + idleElapsedSeconds.floatValue
        val windowStart = now - windowSeconds * anchorFraction
        val visible = samples.filter { it.timestampSeconds >= windowStart }

        val targetExtremes = targetTimeline.flatMap { listOfNotNull(it.rangeKg?.start, it.rangeKg?.endInclusive) }
        val sampleMin = visible.minOfOrNull { it.kilograms }
        val sampleMax = visible.maxOfOrNull { it.kilograms }
        val minWeight = (listOf(0f, sampleMin ?: 0f) + targetExtremes).min()
        val maxWeight = (listOf(minWeight + 1f, sampleMax ?: (minWeight + 1f)) + targetExtremes).max()

        fun yOf(kg: Float) = size.height - (kg - minWeight) / (maxWeight - minWeight) * size.height

        // "now" always lands at anchorFraction across, regardless of windowStart -- the
        // live point stays put and the data rolls underneath it, rather than the point
        // itself sliding toward an edge as samples arrive.
        fun xOf(t: Float) = (anchorFraction + (t - now) / windowSeconds) * size.width

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

        for (segment in targetTimeline) {
            val left = xOf(now + segment.startOffsetSeconds).coerceIn(0f, size.width)
            val right = xOf(now + segment.endOffsetSeconds).coerceIn(0f, size.width)
            if (right <= left) continue
            val range = segment.rangeKg ?: (0f..0f)
            val top = yOf(range.endInclusive)
            val bottom = (yOf(range.start)).coerceAtLeast(top + 3f)
            drawRect(color = bandFillColor, topLeft = Offset(left, top), size = Size(right - left, bottom - top))
            drawLine(bandEdgeColor, Offset(left, top), Offset(right, top), strokeWidth = 2f)
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
