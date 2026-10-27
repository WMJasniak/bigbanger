package com.wiktor.bigbanger.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.wiktor.bigbanger.ble.WeightSample

@Composable
fun ForceChart(samples: List<WeightSample>, modifier: Modifier = Modifier) {
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier.fillMaxWidth().height(220.dp)) {
        if (samples.size < 2) return@Canvas

        val minWeight = minOf(0f, samples.minOf { it.kilograms })
        val maxWeight = maxOf(minWeight + 1f, samples.maxOf { it.kilograms })
        val startTime = samples.first().timestampSeconds
        val duration = (samples.last().timestampSeconds - startTime).coerceAtLeast(0.001f)

        fun xOf(sample: WeightSample) = (sample.timestampSeconds - startTime) / duration * size.width
        fun yOf(sample: WeightSample) =
            size.height - (sample.kilograms - minWeight) / (maxWeight - minWeight) * size.height

        // Horizontal gridlines, including the zero line.
        val gridLines = 4
        for (i in 0..gridLines) {
            val y = size.height * i / gridLines
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
        }
        if (minWeight < 0f && maxWeight > 0f) {
            val zeroY = size.height - (0f - minWeight) / (maxWeight - minWeight) * size.height
            drawLine(gridColor, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 2f)
        }

        var previous = samples.first()
        for (sample in samples.drop(1)) {
            drawLine(
                color = lineColor,
                start = Offset(xOf(previous), yOf(previous)),
                end = Offset(xOf(sample), yOf(sample)),
                strokeWidth = 4f,
                cap = StrokeCap.Round,
            )
            previous = sample
        }
    }
}
