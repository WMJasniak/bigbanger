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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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

/** One layer deeper than the main Settings menu: add, edit or remove the hold types and
 * grip variants every program draws from. */
@Composable
fun HoldsSettingsScreen(viewModel: BigBangerViewModel, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val holdTypes by viewModel.trainingRepo.holdTypes.collectAsStateWithLifecycle()
    val variants by viewModel.trainingRepo.gripVariants.collectAsStateWithLifecycle()
    var newHoldName by remember { mutableStateOf("") }
    var pendingDeleteHold by remember { mutableStateOf<HoldType?>(null) }
    var pendingDeleteVariant by remember { mutableStateOf<GripVariant?>(null) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Holds & grips", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 16.dp))

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = newHoldName,
                onValueChange = { newHoldName = it },
                label = { Text("New hold (e.g. 20mm Edge)") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    val trimmed = newHoldName.trim()
                    if (trimmed.isNotEmpty()) {
                        viewModel.trainingRepo.addHoldType(trimmed)
                        newHoldName = ""
                    }
                },
            ) { Icon(Icons.Filled.Add, contentDescription = "Add hold") }
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
                    onDeleteHold = { pendingDeleteHold = holdType },
                    onAddVariant = { name -> viewModel.trainingRepo.addGripVariant(holdType.id, name) },
                    onDeleteVariant = { variant -> pendingDeleteVariant = variant },
                )
            }
        }
    }

    // Both delete every recorded max test underneath, permanently -- a slip of the
    // thumb here otherwise destroys real history with no way back, unlike removing a
    // grip from a program's own list (just unchecking or re-adding it later).
    pendingDeleteHold?.let { holdType ->
        AlertDialog(
            onDismissRequest = { pendingDeleteHold = null },
            title = { Text("Delete ${holdType.name}?") },
            text = { Text("This deletes every grip under it and all of their recorded max tests. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.trainingRepo.deleteHoldType(holdType.id)
                        pendingDeleteHold = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteHold = null }) { Text("Cancel") } },
        )
    }
    pendingDeleteVariant?.let { variant ->
        AlertDialog(
            onDismissRequest = { pendingDeleteVariant = null },
            title = { Text("Delete ${variant.name}?") },
            text = { Text("This deletes all of its recorded max tests. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.trainingRepo.deleteGripVariant(variant.id)
                        pendingDeleteVariant = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteVariant = null }) { Text("Cancel") } },
        )
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
                IconButton(onClick = onDeleteHold) { Icon(Icons.Filled.Delete, contentDescription = "Delete ${holdType.name}") }
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
                    IconButton(onClick = { onDeleteVariant(variant) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete ${variant.name}")
                    }
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
                IconButton(
                    onClick = {
                        val trimmed = newVariantName.trim()
                        if (trimmed.isNotEmpty()) {
                            onAddVariant(trimmed)
                            newVariantName = ""
                        }
                    },
                ) { Icon(Icons.Filled.Add, contentDescription = "Add grip") }
            }
        }
    }
}

private fun formatMax(kg: Float?): String = if (kg != null) "${"%.1f".format(kg)} kg" else "no max"
