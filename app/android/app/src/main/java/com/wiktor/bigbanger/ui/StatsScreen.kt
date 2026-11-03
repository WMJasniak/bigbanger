package com.wiktor.bigbanger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wiktor.bigbanger.BigBangerViewModel
import com.wiktor.bigbanger.training.GripVariant
import com.wiktor.bigbanger.training.Hand

/** Just the headline numbers -- tap a grip to see its full max-testing history (see
 * GripMaxHistoryScreen.kt), which is also where every reading's date lives; repeating
 * "avg of last 3" on every single row would just be noise once said here, once. */
@Composable
fun StatsScreen(viewModel: BigBangerViewModel, onBack: () -> Unit, onOpenGripHistory: (GripVariant) -> Unit) {
    BackHandler(onBack = onBack)

    val holdTypes by viewModel.trainingRepo.holdTypes.collectAsStateWithLifecycle()
    val variants by viewModel.trainingRepo.gripVariants.collectAsStateWithLifecycle()
    val sessions by viewModel.trainingRepo.sessions.collectAsStateWithLifecycle()
    var showDeleteOldConfirm by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Stats", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { showDeleteOldConfirm = true }) { Text("Keep only today's") }
        }

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
        } else {
            Text(
                "Current max is the average of the last 3 Max Testing results. Tap a grip to see its full history.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
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
                    onClick = { onOpenGripHistory(variant) },
                )
            }
        }
    }

    if (showDeleteOldConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteOldConfirm = false },
            title = { Text("Keep only today's max tests?") },
            text = { Text("This permanently deletes every recorded max test from before today, across every grip. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.trainingRepo.deleteMaxResultsBeforeToday()
                        showDeleteOldConfirm = false
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteOldConfirm = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun GripVariantStatsCard(variant: GripVariant, currentMaxKg: (Hand) -> Float?, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(variant.name, style = MaterialTheme.typography.titleMedium)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                HandMaxPreview(hand = Hand.LEFT, currentMaxKg = currentMaxKg(Hand.LEFT), modifier = Modifier.weight(1f))
                HandMaxPreview(hand = Hand.RIGHT, currentMaxKg = currentMaxKg(Hand.RIGHT), modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HandMaxPreview(hand: Hand, currentMaxKg: Float?, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(hand.displayLabel, style = MaterialTheme.typography.bodyMedium)
        Text(
            if (currentMaxKg != null) "${"%.1f".format(currentMaxKg)} kg" else "No max yet",
            style = MaterialTheme.typography.titleMedium,
            color = if (currentMaxKg != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
