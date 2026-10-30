package com.wiktor.bigbanger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wiktor.bigbanger.BigBangerViewModel
import com.wiktor.bigbanger.training.GripVariant
import com.wiktor.bigbanger.training.Hand
import com.wiktor.bigbanger.training.MaxTestResult
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

@Composable
fun StatsScreen(viewModel: BigBangerViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val holdTypes by viewModel.trainingRepo.holdTypes.collectAsStateWithLifecycle()
    val variants by viewModel.trainingRepo.gripVariants.collectAsStateWithLifecycle()
    val sessions by viewModel.trainingRepo.sessions.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("Stats", style = MaterialTheme.typography.titleLarge)

        Text(
            "Activity",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        )
        ContributionGraph(sessionTimestampsMs = sessions.map { it.timestampMs })

        if (holdTypes.isEmpty()) {
            Text(
                "Add a hold in Settings -> Holds & grips, then run Max Testing to start tracking.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 24.dp),
            )
        }

        holdTypes.forEach { holdType ->
            Text(
                holdType.name,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
            )
            variants.filter { it.holdTypeId == holdType.id }.forEach { variant ->
                GripVariantStatsCard(
                    variant = variant,
                    currentMaxKg = { hand -> viewModel.trainingRepo.currentMaxKg(variant.id, hand) },
                    resultsFor = { hand -> viewModel.trainingRepo.resultsFor(variant.id, hand) },
                    onDeleteResult = { resultId -> viewModel.trainingRepo.deleteMaxResult(resultId) },
                )
            }
        }
    }
}

@Composable
private fun GripVariantStatsCard(
    variant: GripVariant,
    currentMaxKg: (Hand) -> Float?,
    resultsFor: (Hand) -> List<MaxTestResult>,
    onDeleteResult: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(variant.name, style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                HandStatsColumn(
                    hand = Hand.LEFT,
                    currentMaxKg = currentMaxKg(Hand.LEFT),
                    results = resultsFor(Hand.LEFT),
                    onDeleteResult = onDeleteResult,
                    modifier = Modifier.weight(1f),
                )
                HandStatsColumn(
                    hand = Hand.RIGHT,
                    currentMaxKg = currentMaxKg(Hand.RIGHT),
                    results = resultsFor(Hand.RIGHT),
                    onDeleteResult = onDeleteResult,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun HandStatsColumn(
    hand: Hand,
    currentMaxKg: Float?,
    results: List<MaxTestResult>,
    onDeleteResult: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(hand.displayLabel, style = MaterialTheme.typography.bodyMedium)
        Text(
            if (currentMaxKg != null) "${"%.1f".format(currentMaxKg)} kg (avg of last ${results.size.coerceAtMost(3)})" else "No max yet",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 6.dp),
        )
        // The place to clean up a wrong reading (slipped grip, false start, etc.) --
        // Max Testing itself no longer stops to ask after every attempt, it just keeps
        // recording; this is where to delete one that shouldn't count.
        results.take(5).forEach { result ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row {
                    Text(dateFormat.format(Date(result.timestampMs)), style = MaterialTheme.typography.labelSmall)
                    Text("  ${"%.1f".format(result.peakKg)} kg", style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { onDeleteResult(result.id) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete this reading")
                }
            }
        }
    }
}

