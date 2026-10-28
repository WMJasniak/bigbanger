package com.wiktor.bigbanger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.wiktor.bigbanger.training.HoldType
import com.wiktor.bigbanger.training.ProgramDefinition

/** Step 2: which grip variant on this hold (e.g. "4-Finger Half Crimp"), then which hand --
 * every variant always carries both a left and a right max, never two separate rows. */
@Composable
fun GripVariantSelectScreen(
    viewModel: BigBangerViewModel,
    program: ProgramDefinition,
    holdType: HoldType,
    onBack: () -> Unit,
    onSelect: (GripVariant, Hand) -> Unit,
) {
    BackHandler(onBack = onBack)

    val variants by viewModel.trainingRepo.gripVariants.collectAsStateWithLifecycle()
    val onThisHold = variants.filter { it.holdTypeId == holdType.id }
    var newName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Text(holdType.name, style = MaterialTheme.typography.titleLarge)
        }
        Text(
            "Pick a grip, then a hand",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("New grip (e.g. 4-Finger Half Crimp)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    val trimmed = newName.trim()
                    if (trimmed.isNotEmpty()) {
                        viewModel.trainingRepo.addGripVariant(holdType.id, trimmed)
                        newName = ""
                    }
                },
            ) { Text("Add") }
        }

        LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
            items(onThisHold, key = { it.id }) { variant ->
                GripVariantCard(
                    variant = variant,
                    program = program,
                    maxForHand = { hand -> viewModel.trainingRepo.currentMaxKg(variant.id, hand) },
                    onSelectHand = { hand -> onSelect(variant, hand) },
                )
            }
        }
    }
}

@Composable
private fun GripVariantCard(
    variant: GripVariant,
    program: ProgramDefinition,
    maxForHand: (Hand) -> Float?,
    onSelectHand: (Hand) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(variant.name, style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Hand.entries.forEach { hand ->
                    val maxKg = maxForHand(hand)
                    val needsMax = !program.hasNoTarget && maxKg == null
                    OutlinedButton(
                        onClick = { onSelectHand(hand) },
                        enabled = !needsMax,
                        modifier = Modifier.weight(1f),
                    ) {
                        Column {
                            Text(hand.displayLabel, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                when {
                                    needsMax -> "No max yet"
                                    maxKg != null -> "${"%.1f".format(maxKg)} kg"
                                    else -> "No max needed"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
