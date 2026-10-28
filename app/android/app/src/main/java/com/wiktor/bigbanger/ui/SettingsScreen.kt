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

@Composable
fun SettingsScreen(
    viewModel: BigBangerViewModel,
    updateStatus: String?,
    onCheckUpdates: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val holdTypes by viewModel.trainingRepo.holdTypes.collectAsStateWithLifecycle()
    val variants by viewModel.trainingRepo.gripVariants.collectAsStateWithLifecycle()
    var newHoldName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }

        Text(
            "Holds",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
        )

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = newHoldName,
                onValueChange = { newHoldName = it },
                label = { Text("New hold (e.g. 20mm Edge)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    val trimmed = newHoldName.trim()
                    if (trimmed.isNotEmpty()) {
                        viewModel.trainingRepo.addHoldType(trimmed)
                        newHoldName = ""
                    }
                },
            ) { Text("Add") }
        }

        if (holdTypes.isEmpty()) {
            Text(
                "No holds yet -- add one above (e.g. \"20mm Edge\"), then add its grips below.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 24.dp),
            )
        }

        LazyColumn(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
            items(holdTypes, key = { it.id }) { holdType ->
                HoldTypeCard(
                    holdType = holdType,
                    variants = variants.filter { it.holdTypeId == holdType.id },
                    maxForHand = { variant, hand -> viewModel.trainingRepo.currentMaxKg(variant.id, hand) },
                    onDeleteHold = { viewModel.trainingRepo.deleteHoldType(holdType.id) },
                    onAddVariant = { name -> viewModel.trainingRepo.addGripVariant(holdType.id, name) },
                    onDeleteVariant = { variant -> viewModel.trainingRepo.deleteGripVariant(variant.id) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("App updates", style = MaterialTheme.typography.titleMedium)
                updateStatus?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            OutlinedButton(onClick = onCheckUpdates) { Text("Check for updates") }
        }
    }
}

@Composable
private fun HoldTypeCard(
    holdType: HoldType,
    variants: List<GripVariant>,
    maxForHand: (GripVariant, Hand) -> Float?,
    onDeleteHold: () -> Unit,
    onAddVariant: (String) -> Unit,
    onDeleteVariant: (GripVariant) -> Unit,
) {
    var newVariantName by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(holdType.name, style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onDeleteHold) { Text("Delete") }
            }

            variants.forEach { variant ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(variant.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "L: ${formatMax(maxForHand(variant, Hand.LEFT))} · R: ${formatMax(maxForHand(variant, Hand.RIGHT))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onDeleteVariant(variant) }) { Text("Delete") }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 12.dp),
            ) {
                OutlinedTextField(
                    value = newVariantName,
                    onValueChange = { newVariantName = it },
                    label = { Text("New grip (e.g. 4-Finger Half Crimp)") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        val trimmed = newVariantName.trim()
                        if (trimmed.isNotEmpty()) {
                            onAddVariant(trimmed)
                            newVariantName = ""
                        }
                    },
                ) { Text("Add") }
            }
        }
    }
}

private fun formatMax(kg: Float?): String = if (kg != null) "${"%.1f".format(kg)} kg" else "no max"
