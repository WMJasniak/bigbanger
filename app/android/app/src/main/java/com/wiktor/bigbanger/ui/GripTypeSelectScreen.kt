package com.wiktor.bigbanger.ui

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
import com.wiktor.bigbanger.training.GripType
import com.wiktor.bigbanger.training.ProgramDefinition

@Composable
fun GripTypeSelectScreen(
    viewModel: BigBangerViewModel,
    program: ProgramDefinition,
    onBack: () -> Unit,
    onSelect: (GripType) -> Unit,
) {
    val gripTypes by viewModel.trainingRepo.gripTypes.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Text(program.title, style = MaterialTheme.typography.titleLarge)
        }
        Text(
            "Pick a grip type",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("New grip type") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = {
                    val trimmed = newName.trim()
                    if (trimmed.isNotEmpty()) {
                        viewModel.trainingRepo.addGripType(trimmed)
                        newName = ""
                    }
                },
            ) { Text("Add") }
        }

        LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
            items(gripTypes, key = { it.id }) { grip ->
                val maxKg = viewModel.trainingRepo.currentMaxKg(grip.id)
                val needsMax = !program.hasNoTarget && maxKg == null
                GripRow(
                    name = grip.name,
                    subtitle = when {
                        needsMax -> "No max recorded yet -- run Max Testing first"
                        maxKg != null -> "Current max: ${"%.1f".format(maxKg)} kg"
                        else -> "No max needed for this program"
                    },
                    enabled = !needsMax,
                    onClick = { onSelect(grip) },
                )
            }
        }
    }
}

@Composable
private fun GripRow(name: String, subtitle: String, enabled: Boolean, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
