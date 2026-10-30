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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
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

    // Advances every display frame by real elapsed time -- not by however the BLE
    // samples themselves happen to arrive -- then nudges gently toward the latest real
    // device timestamp each frame rather than snapping straight to it. A hard snap
    // (the previous design: reset to 0 and recount from scratch on every single new
    // sample) looked fine against the simulator's steady 100ms ticks, but the real
    // scale's notifications land in bursts -- several samples processed in one frame,
    // several frames with none -- and snapping to each one individually is what read
    // as choppy. The 15%-per-frame correction still tracks the real clock (so a
    // reconnect's fresh timestamp or a long gap gets caught up within a few frames)
    // without visibly jumping on every burst.
    var nowSeconds by remember { mutableFloatStateOf(samples.lastOrNull()?.timestampSeconds ?: 0f) }
    val currentSamples by rememberUpdatedState(samples)
    LaunchedEffect(Unit) {
        var lastFrameNanos = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { frameNanos ->
                val deltaSeconds = (frameNanos - lastFrameNanos) / 1_000_000_000f
                lastFrameNanos = frameNanos
                nowSeconds += deltaSeconds
                currentSamples.lastOrNull()?.timestampSeconds?.let { latestDevice ->
                    nowSeconds += (latestDevice - nowSeconds) * 0.15f
                }
            }
        }
    }

    // Reused across every draw instead of allocating fresh Path/ArrayList objects each
    // time -- with the clock now advancing (and so redrawing) every display frame
    // rather than only on a new sample, that would otherwise be 60+ short-lived native
    // Skia objects a second for the GC to catch up on.
    val stripTopPath = remember { Path() }
    val stripBottomPath = remember { Path() }
    val stripFillPath = remember { Path() }
    val stripBottomPoints = remember { ArrayList<Offset>() }
    val linePath = remember { Path() }
    val lineFillPath = remember { Path() }

    // Compose does not clip a Canvas's drawing to its own bounds by default, so a
    // reading beyond the y-axis range (now fixed to the target, not the readout) would
    // otherwise paint past the chart's panel and onto whatever sits below it.
    Canvas(modifier = modifier.fillMaxSize().clipToBounds()) {
        drawRoundRect(color = panelColor, cornerRadius = CornerRadius(20f, 20f))

        val now = nowSeconds
        val windowStart = now - windowSeconds * anchorFraction
        val visible = samples.filter { it.timestampSeconds >= windowStart }

        // While a real target is in view, the range is normally fixed to "this leg's
        // work level and zero" -- using legCenterKg (the leg's real level regardless of
        // whether this exact segment is WORK or REST/PREP) rather than the displayed
        // centerKg (which drops to zero for REST/PREP) -- so the axis does not jump
        // every time the current step switches between work and rest. It is still
        // scoped to just the current leg, not a neighboring one with a different max:
        // legCenterKg is only ever borrowed from this same leg's own WORK step.
        // Ordinary noise around the target is ignored (that is still what keeps the
        // axis from jittering on every small overshoot), but a real peak well above it
        // does expand the axis to actually show it -- and, since that only looks at
        // [visible] (already scoped to the current scrolling window), it keeps the
        // expanded range for as long as the peak sample is still on screen and only
        // reverts once it has scrolled out, rather than the moment the live reading
        // itself drops back down. Only without a real target (Max Testing, Raw Data)
        // does the readout drive the axis unconditionally, since there is nothing else
        // to scale against.
        val currentSegment = sortedSegments.firstOrNull { it.startOffsetSeconds <= 0f && it.endOffsetSeconds > 0f }
        val legCenter = currentSegment?.legCenterKg
        val half = currentSegment?.halfWidthKg ?: 0f
        val hasRealTarget = legCenter != null && half > 0.01f
        val rawMin: Float
        val rawMax: Float
        if (hasRealTarget) {
            // Work band top is legCenter+half; rest band bottom is 0-half -- the rest
            // bottom always is the overall floor and the work top always is the overall
            // ceiling, since legCenter is never negative.
            rawMin = -half
            val targetTop = legCenter!! + half
            val visiblePeak = visible.maxOfOrNull { it.kilograms } ?: targetTop
            rawMax = maxOf(targetTop, visiblePeak).coerceAtLeast(rawMin + 0.05f)
        } else {
            // A tiny epsilon floor, not a forced 1kg-tall axis -- small real readings
            // (an idle/untared raw trace, or a light Max Testing peak) were getting
            // squashed into the bottom sliver of a much taller axis and reading as
            // "not plotted".
            val sampleMin = visible.minOfOrNull { it.kilograms }
            val sampleMax = visible.maxOfOrNull { it.kilograms }
            rawMin = listOf(0f, sampleMin ?: 0f).min()
            rawMax = listOf(rawMin + 0.05f, sampleMax ?: (rawMin + 0.05f)).max()
        }
        // Even margin on both sides: the same distance from the work target's top to
        // the graph's top as from the rest level's bottom to the graph's bottom.
        val margin = (rawMax - rawMin) * 0.08f
        val minWeight = rawMin - margin
        val maxWeight = rawMax + margin

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
            // sortedSegments' offsets are relative to whichever sample was latest when
            // the caller last computed targetTimeline -- i.e. this same lastSampleTime,
            // not this chart's own now. Positioning the strip at xOf(now + t) looked
            // right but silently canceled out: both the "now" being added here and the
            // "now" xOf itself subtracts are the exact same value read at the same
            // instant, so the result reduces to a function of t and constants alone --
            // it could not move between frames no matter how smoothly now advances.
            // xOf(lastSampleTime + t) does not cancel, since lastSampleTime only
            // updates when a real sample arrives while now keeps gliding every frame
            // in between -- which is what makes it actually scroll smoothly, in step
            // with the line instead of visibly stepping once per sample like before.
            val lastSampleTime = samples.lastOrNull()?.timestampSeconds ?: now
            val timeStart = -windowSeconds * anchorFraction
            val timeEnd = windowSeconds * (1f - anchorFraction)
            stripTopPath.reset()
            stripBottomPath.reset()
            stripFillPath.reset()
            stripBottomPoints.clear()
            var started = false
            var t = timeStart
            while (t <= timeEnd) {
                val (center, half) = smoothedStripAt(sortedSegments, t)
                val x = xOf(lastSampleTime + t)
                val topY = yOf(center + half)
                val bottomY = yOf(center - half)
                if (!started) {
                    stripTopPath.moveTo(x, topY)
                    stripBottomPath.moveTo(x, bottomY)
                    stripFillPath.moveTo(x, topY)
                    started = true
                } else {
                    stripTopPath.lineTo(x, topY)
                    stripBottomPath.lineTo(x, bottomY)
                    stripFillPath.lineTo(x, topY)
                }
                stripBottomPoints.add(Offset(x, bottomY))
                t += STRIP_SAMPLE_STEP_SECONDS
            }
            for (p in stripBottomPoints.asReversed()) stripFillPath.lineTo(p.x, p.y)
            stripFillPath.close()

            drawPath(path = stripFillPath, color = bandFillColor)
            drawPath(path = stripTopPath, color = bandEdgeColor, style = Stroke(width = 5f, join = StrokeJoin.Round))
            drawPath(path = stripBottomPath, color = bandEdgeColor, style = Stroke(width = 5f, join = StrokeJoin.Round))
        }

        if (visible.isNotEmpty()) {
            linePath.reset()
            lineFillPath.reset()
            var started = false
            for (sample in visible) {
                val x = xOf(sample.timestampSeconds)
                val y = yOf(sample.kilograms)
                if (!started) {
                    linePath.moveTo(x, y)
                    lineFillPath.moveTo(x, size.height)
                    lineFillPath.lineTo(x, y)
                    started = true
                } else {
                    linePath.lineTo(x, y)
                    lineFillPath.lineTo(x, y)
                }
            }

            // The line's tip (and the dot) used to sit exactly on the last *real*
            // sample -- which, between samples, is always a little behind "now" and
            // held perfectly still until the next one landed, then visibly jumped.
            // Real samples arrive at whatever rate the hardware/BLE link delivers them,
            // nowhere near the ~60fps "now" now advances at, so that stillness-then-jump
            // was obvious against the now-smoothly-scrolling background. Extending the
            // line to a synthetic point at exactly xOf(now) -- its kg linearly
            // projected from the recent trend -- keeps the tip gliding every frame
            // instead. The projection is a least-squares slope over the last few real
            // samples (steadier than just the last two, which noise alone can swing
            // wildly frame to frame) and the forward projection distance is capped at
            // 0.5s so a stalled connection makes it go flat, not careen on a stale slope.
            val liveX = xOf(now)
            val liveKg = extrapolatedKg(visible, now)
            val liveY = yOf(liveKg)
            linePath.lineTo(liveX, liveY)
            lineFillPath.lineTo(liveX, liveY)
            lineFillPath.lineTo(liveX, size.height)
            lineFillPath.close()

            drawPath(
                path = lineFillPath,
                brush = Brush.verticalGradient(listOf(lineColor.copy(alpha = 0.35f), lineColor.copy(alpha = 0f))),
            )
            drawPath(
                path = linePath,
                color = lineColor,
                style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            drawCircle(color = lineColor, radius = 7f, center = Offset(liveX, liveY))
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

/**
 * Projects the live point's kg value forward from [visible]'s recent trend to [now],
 * via a least-squares slope over its last few samples (at most 5 -- enough to steady
 * the estimate against single-sample noise without smearing in a real direction
 * change) capped to a 0.5s forward projection, so a real gap (a stalled connection, not
 * just the normal wait between samples) makes the line go flat instead of continuing
 * to extrapolate an increasingly stale slope indefinitely.
 */
private fun extrapolatedKg(visible: List<WeightSample>, now: Float): Float {
    val last = visible.last()
    val recent = visible.takeLast(5)
    if (recent.size < 2) return last.kilograms
    val tMean = recent.sumOf { it.timestampSeconds.toDouble() } / recent.size
    val kMean = recent.sumOf { it.kilograms.toDouble() } / recent.size
    var num = 0.0
    var den = 0.0
    for (s in recent) {
        val dt = s.timestampSeconds - tMean
        num += dt * (s.kilograms - kMean)
        den += dt * dt
    }
    if (den < 1e-6) return last.kilograms
    val slope = (num / den).toFloat()
    val gap = (now - last.timestampSeconds).coerceIn(0f, 0.5f)
    return last.kilograms + slope * gap
}

/** An S-shaped ease from 0 to 1, reading the same as a sigmoid for this purpose. */
private fun smoothstep(x: Float): Float {
    val c = x.coerceIn(0f, 1f)
    return c * c * (3f - 2f * c)
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
