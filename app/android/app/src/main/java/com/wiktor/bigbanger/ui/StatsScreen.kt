package com.wiktor.bigbanger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wiktor.bigbanger.BigBangerViewModel
import com.wiktor.bigbanger.training.GripType
import com.wiktor.bigbanger.training.MaxTestResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

@Composable
fun StatsScreen(viewModel: BigBangerViewModel, onBack: () -> Unit) {
    val gripTypes by viewModel.trainingRepo.gripTypes.collectAsStateWithLifecycle()
    val sessions by viewModel.trainingRepo.sessions.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Stats", style = MaterialTheme.typography.titleLarge)
        }

        Text(
            "Activity",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        ContributionGraph(sessionTimestampsMs = sessions.map { it.timestampMs })

        if (gripTypes.isEmpty()) {
            Text(
                "Add a grip type in Settings, then run Max Testing to start tracking.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 24.dp),
            )
        }

        gripTypes.forEach { grip ->
            GripStatsCard(
                grip = grip,
                currentMaxKg = viewModel.trainingRepo.currentMaxKg(grip.id),
                results = viewModel.trainingRepo.resultsFor(grip.id),
            )
        }
    }
}

@Composable
private fun GripStatsCard(grip: GripType, currentMaxKg: Float?, results: List<MaxTestResult>) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(grip.name, style = MaterialTheme.typography.titleMedium)
            Text(
                if (currentMaxKg != null) "Current max: ${"%.1f".format(currentMaxKg)} kg (avg of last ${results.size.coerceAtMost(3)})" else "No max recorded yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            if (results.isEmpty()) {
                Text("No Max Testing results yet.", style = MaterialTheme.typography.bodySmall)
            } else {
                results.forEach { result ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(dateFormat.format(Date(result.timestampMs)), style = MaterialTheme.typography.bodySmall)
                        Text("${"%.1f".format(result.peakKg)} kg", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
