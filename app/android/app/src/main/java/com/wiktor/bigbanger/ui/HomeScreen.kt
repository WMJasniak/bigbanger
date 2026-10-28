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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wiktor.bigbanger.BigBangerViewModel
import com.wiktor.bigbanger.ble.ConnectionState
import com.wiktor.bigbanger.training.ProgramDefinition
import com.wiktor.bigbanger.training.Programs

@Composable
fun HomeScreen(
    viewModel: BigBangerViewModel,
    onSelectProgram: (ProgramDefinition) -> Unit,
    onOpenProgramGripConfig: (ProgramDefinition) -> Unit,
    onFreeMeasure: () -> Unit,
    onStats: () -> Unit,
    onSettings: () -> Unit,
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("BigBanger", style = MaterialTheme.typography.headlineMedium)
        Text(
            connectionStatusText(connectionState),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(Programs.all) { program ->
                ProgramCard(
                    program = program,
                    onClick = { onSelectProgram(program) },
                    // No Hangs already dictates its own grips; nothing to configure.
                    onOpenGripConfig = if (program.usesFixedGripSequence) null else ({ onOpenProgramGripConfig(program) }),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onStats, modifier = Modifier.weight(1f)) { Text("Stats") }
            OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f)) { Text("Settings") }
        }
        TextButton(onClick = onFreeMeasure, modifier = Modifier.fillMaxWidth()) {
            Text("Raw data")
        }
    }
}

@Composable
private fun ProgramCard(program: ProgramDefinition, onClick: () -> Unit, onOpenGripConfig: (() -> Unit)?) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(program.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            onOpenGripConfig?.let {
                TextButton(onClick = it) {
                    Text("⚙", style = MaterialTheme.typography.titleMedium)
                }
            }
            Text(">", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun connectionStatusText(state: ConnectionState): String = when (state) {
    ConnectionState.Disconnected -> "Not connected"
    is ConnectionState.Connecting -> "Connecting to ${state.device.name}..."
    is ConnectionState.Reconnecting -> "Reconnecting to ${state.device.name}..."
    is ConnectionState.Connected -> "Connected to ${state.device.name}"
}
