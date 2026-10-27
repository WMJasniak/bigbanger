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
import com.wiktor.bigbanger.training.GripType

@Composable
fun SettingsScreen(
    viewModel: BigBangerViewModel,
    updateStatus: String?,
    onCheckUpdates: () -> Unit,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)

    val gripTypes by viewModel.trainingRepo.gripTypes.collectAsStateWithLifecycle()
    var newName by remember { mutableStateOf("") }

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
            "Grip / edge types",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp, bottom = 16.dp),
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

        if (gripTypes.isEmpty()) {
            Text(
                "No grip types yet -- add one above before running a program.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 24.dp),
            )
        }

        LazyColumn(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
            items(gripTypes, key = { it.id }) { grip ->
                GripTypeRow(
                    grip = grip,
                    currentMaxKg = viewModel.trainingRepo.currentMaxKg(grip.id),
                    onDelete = { viewModel.trainingRepo.deleteGripType(grip.id) },
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
private fun GripTypeRow(grip: GripType, currentMaxKg: Float?, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(grip.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (currentMaxKg != null) "Current max: ${"%.1f".format(currentMaxKg)} kg" else "No max recorded yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onDelete) { Text("Delete") }
        }
    }
}
