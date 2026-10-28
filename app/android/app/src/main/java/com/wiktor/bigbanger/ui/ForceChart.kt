package com.wiktor.bigbanger.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import com.wiktor.bigbanger.ble.WeightSample
import com.wiktor.bigbanger.training.TargetSegment
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import androidx.compose.ui.graphics.lerp as lerpColor

/** Fixed scrolling window, not "stretch to fit all data so far" -- the latter made the
 * whole trace visibly compress on every new sample, which read as choppy/jittery. */
private const val DEFAULT_WINDOW_SECONDS = 12f

/** How long the target strip takes to ease from one segment's position to the next. */
private const val STRIP_TRANSITION_SECONDS = 0.4f

/** Time step for sampling the strip's smoothed position -- fine enough to render the
 * transition curve itself smoothly, not just the flat parts of each segment. */
private const val STRIP_SAMPLE_STEP_SECONDS = 0.1f

// Panel background hue-shifts toward one of these during WORK vs REST/PREP, so the
// background itself (not just the strip) signals which period you're in. Blended at a
// low fraction into the theme's own panel color rather than used outright, since the
// base is nearly desaturated and a pure hue rotation on it would barely show.
private val WORK_TINT_COLOR = Color(0xFFD4B106)
private val REST_TINT_COLOR = Color(0xFF4F9D5B)
private const val PANEL_TINT_FRACTION = 0.16f

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
    /** true = tint the panel warm (actively working), false = tint it cool (resting/
     * prepping), null = no tint (plain reading, e.g. Raw Data, where neither applies). */
    isWorking: Boolean? = null,
    modifier: Modifier = Modifier,
) {
    val basePanelColor = MaterialTheme.colorScheme.surfaceVariant
    val targetPanelColor = when (isWorking) {
        true -> lerpColor(basePanelColor, WORK_TINT_COLOR, PANEL_TINT_FRACTION)
        false -> lerpColor(basePanelColor, REST_TINT_COLOR, PANEL_TINT_FRACTION)
        null -> basePanelColor
    }
    val panelColor by animateColorAsState(targetValue = targetPanelColor, label = "forceChartPanel")
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
    val bandFillColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
    val bandEdgeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    val sortedSegments = remember(targetTimeline) { targetTimeline.sortedBy { it.startOffsetSeconds } }

    // Keeps the window scrolling at a real-time pace even with no new samples (e.g. a
    // brief connection hiccup) instead of freezing on the last sample's device
    // timestamp. Resets to 0 every time a genuinely new sample arrives.
    val lastSample = samples.lastOrNull()
    val idleElapsedSeconds = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(lastSample) {
        idleElapsedSeconds.floatValue = 0f
        val startNanos = System.nanoTime()
        while (isActive) {
            delay(33)
            idleElapsedSeconds.floatValue = (System.nanoTime() - startNanos) / 1_000_000_000f
        }
    }

    // Compose does not clip a Canvas's drawing to its own bounds by default, so a
    // reading beyond the y-axis range (now fixed to the target, not the readout) would
    // otherwise paint past the chart's panel and onto whatever sits below it.
    Canvas(modifier = modifier.fillMaxSize().clipToBounds()) {
        drawRoundRect(color = panelColor, cornerRadius = CornerRadius(20f, 20f))

        val now = (lastSample?.timestampSeconds ?: 0f) + idleElapsedSeconds.floatValue
        val windowStart = now - windowSeconds * anchorFraction
        val visible = samples.filter { it.timestampSeconds >= windowStart }

        // While a real target is in view, the range is set from the *current* segment's
        // band alone -- not every segment in the window -- so a neighboring leg with a
        // much higher or lower max (a different grip/hand) doesn't stretch the axis and
        // leave a huge gap above the band actually in front of you right now. Past/future
        // segments still render (and scroll, and clip at the edge) at whatever size they
        // really are. Deliberately ignores the live readout too, so noise or overshoot
        // doesn't make the axis keep resizing. Only without a real target (Max Testing,
        // Raw Data) does the readout drive it, since there is nothing else to scale against.
        val currentSegment = sortedSegments.firstOrNull { it.startOffsetSeconds <= 0f && it.endOffsetSeconds > 0f }
        val targetExtremes = currentSegment?.let { listOf(it.centerKg - it.halfWidthKg, it.centerKg + it.halfWidthKg) } ?: emptyList()
        val hasRealTarget = (currentSegment?.halfWidthKg ?: 0f) > 0.01f
        val rawMin: Float
        val rawMax: Float
        if (hasRealTarget) {
            rawMin = (listOf(0f) + targetExtremes).min()
            rawMax = (listOf(rawMin + 1f) + targetExtremes).max()
        } else {
            val sampleMin = visible.minOfOrNull { it.kilograms }
            val sampleMax = visible.maxOfOrNull { it.kilograms }
            rawMin = listOf(0f, sampleMin ?: 0f).min()
            rawMax = listOf(rawMin + 1f, sampleMax ?: (rawMin + 1f)).max()
        }
        // Less headroom above the target's own top than below it -- the ceiling above
        // the band matters far less than the baseline/zero context underneath it.
        val range = rawMax - rawMin
        val minWeight = rawMin - range * 0.08f
        val maxWeight = rawMax + range * 0.03f

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

        if (sortedSegments.isNotEmpty()) {
            val timeStart = -windowSeconds * anchorFraction
            val timeEnd = windowSeconds * (1f - anchorFraction)
            val topEdgePath = Path()
            val bottomEdgePath = Path()
            val fillPath = Path()
            val bottomPoints = ArrayList<Offset>((((timeEnd - timeStart) / STRIP_SAMPLE_STEP_SECONDS) + 2).toInt())
            var started = false
            var t = timeStart
            while (t <= timeEnd) {
                val (center, half) = smoothedStripAt(sortedSegments, t)
                val x = xOf(now + t)
                val topY = yOf(center + half)
                val bottomY = yOf(center - half)
                if (!started) {
                    topEdgePath.moveTo(x, topY)
                    bottomEdgePath.moveTo(x, bottomY)
                    fillPath.moveTo(x, topY)
                    started = true
                } else {
                    topEdgePath.lineTo(x, topY)
                    bottomEdgePath.lineTo(x, bottomY)
                    fillPath.lineTo(x, topY)
                }
                bottomPoints.add(Offset(x, bottomY))
                t += STRIP_SAMPLE_STEP_SECONDS
            }
            for (p in bottomPoints.asReversed()) fillPath.lineTo(p.x, p.y)
            fillPath.close()

            drawPath(path = fillPath, color = bandFillColor)
            drawPath(path = topEdgePath, color = bandEdgeColor, style = Stroke(width = 5f, join = StrokeJoin.Round))
            drawPath(path = bottomEdgePath, color = bandEdgeColor, style = Stroke(width = 5f, join = StrokeJoin.Round))
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

/**
 * The strip's (center, halfWidth) at time offset [t], easing between adjacent segments
 * over [STRIP_TRANSITION_SECONDS] around each boundary instead of cutting sharply --
 * [sorted] must already be ordered by [TargetSegment.startOffsetSeconds].
 */
private fun smoothedStripAt(sorted: List<TargetSegment>, t: Float): Pair<Float, Float> {
    val index = sorted.indexOfFirst { t >= it.startOffsetSeconds && t < it.endOffsetSeconds }
        .let { if (it >= 0) it else if (t < sorted.first().startOffsetSeconds) 0 else sorted.lastIndex }
    val current = sorted[index]
    val half = STRIP_TRANSITION_SECONDS / 2f

    val distToStart = t - current.startOffsetSeconds
    if (distToStart < half && index > 0) {
        val prev = sorted[index - 1]
        val eased = smoothstep(((distToStart + half) / STRIP_TRANSITION_SECONDS).coerceIn(0f, 1f))
        return lerp(prev.centerKg, current.centerKg, eased) to lerp(prev.halfWidthKg, current.halfWidthKg, eased)
    }
    val distToEnd = current.endOffsetSeconds - t
    if (distToEnd < half && index < sorted.lastIndex) {
        val next = sorted[index + 1]
        val eased = smoothstep(((half - distToEnd) / STRIP_TRANSITION_SECONDS).coerceIn(0f, 1f))
        return lerp(current.centerKg, next.centerKg, eased) to lerp(current.halfWidthKg, next.halfWidthKg, eased)
    }
    return current.centerKg to current.halfWidthKg
}

/** An S-shaped ease from 0 to 1, reading the same as a sigmoid for this purpose. */
private fun smoothstep(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
