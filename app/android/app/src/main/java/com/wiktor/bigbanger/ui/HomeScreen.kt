package com.wiktor.bigbanger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
    var showDeviceInfo by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("BigBanger", style = MaterialTheme.typography.headlineMedium)
            ConnectionIcon(
                state = connectionState,
                // Connected (or on the way there) opens the info/disconnect dialog below;
                // disconnected opens the same scan/connect flow Raw Data uses -- that flow
                // *is* the "connection menu".
                onClick = { if (connectionState == ConnectionState.Disconnected) onFreeMeasure() else showDeviceInfo = true },
            )
        }

        LazyColumn(modifier = Modifier.weight(1f)) {
            items(Programs.all) { program ->
                ProgramCard(
                    program = program,
                    onClick = { onSelectProgram(program) },
                    onOpenGripConfig = { onOpenProgramGripConfig(program) },
                )
            }
            item {
                HomeTile(title = "Raw data", onClick = onFreeMeasure)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onStats, modifier = Modifier.weight(1f)) { Text("Stats") }
            OutlinedButton(onClick = onSettings, modifier = Modifier.weight(1f)) { Text("Settings") }
        }
    }

    if (showDeviceInfo && connectionState != ConnectionState.Disconnected) {
        DeviceInfoDialog(
            state = connectionState,
            onDisconnect = {
                viewModel.disconnect()
                showDeviceInfo = false
            },
            onDismiss = { showDeviceInfo = false },
        )
    }
}

/** Replaces a standing "Connected to X" text row (which cost vertical space on every
 * visit) with a tap target: its icon alone says connected/connecting/not connected, and
 * tapping it surfaces the rest -- device info when there's a link, the connect flow
 * when there isn't. */
@Composable
private fun ConnectionIcon(state: ConnectionState, onClick: () -> Unit) {
    val (icon, tint) = when (state) {
        ConnectionState.Disconnected -> Icons.Filled.BluetoothDisabled to MaterialTheme.colorScheme.onSurfaceVariant
        is ConnectionState.Connecting -> Icons.AutoMirrored.Filled.BluetoothSearching to MaterialTheme.colorScheme.onSurfaceVariant
        is ConnectionState.Reconnecting -> Icons.AutoMirrored.Filled.BluetoothSearching to MaterialTheme.colorScheme.error
        is ConnectionState.Connected -> Icons.Filled.BluetoothConnected to MaterialTheme.colorScheme.primary
    }
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = connectionStatusText(state), tint = tint)
    }
}

@Composable
private fun DeviceInfoDialog(state: ConnectionState, onDisconnect: () -> Unit, onDismiss: () -> Unit) {
    val deviceName = when (state) {
        is ConnectionState.Connected -> state.device.name
        is ConnectionState.Connecting -> state.device.name
        is ConnectionState.Reconnecting -> state.device.name
        ConnectionState.Disconnected -> return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(deviceName) },
        text = { Text(connectionStatusText(state)) },
        confirmButton = { TextButton(onClick = onDisconnect) { Text("Disconnect") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun ProgramCard(program: ProgramDefinition, onClick: () -> Unit, onOpenGripConfig: () -> Unit) {
    HomeTile(
        title = program.title,
        onClick = onClick,
        trailing = {
            IconButton(onClick = onOpenGripConfig) {
                Icon(Icons.Filled.Settings, contentDescription = "${program.title} settings")
            }
        },
    )
}

/** A full-width, tap-anywhere card -- the whole tile navigates, so no trailing arrow
 * is needed to say so. Shared by program tiles and the Raw Data entry for a
 * consistent look across Home. */
@Composable
private fun HomeTile(title: String, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            trailing?.invoke()
        }
    }
}

private fun connectionStatusText(state: ConnectionState): String = when (state) {
    ConnectionState.Disconnected -> "Not connected"
    is ConnectionState.Connecting -> "Connecting to ${state.device.name}..."
    is ConnectionState.Reconnecting -> "Reconnecting to ${state.device.name}..."
    is ConnectionState.Connected -> "Connected to ${state.device.name}"
}
