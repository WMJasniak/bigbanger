package com.wiktor.bigbanger.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import com.wiktor.bigbanger.ble.BleDevice
import com.wiktor.bigbanger.ble.ConnectionState
import kotlin.math.roundToInt

@Composable
fun DeviceScreen(viewModel: BigBangerViewModel, device: BleDevice) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val isMeasuring by viewModel.isMeasuring.collectAsStateWithLifecycle()
    val latestWeight by viewModel.latestWeightKg.collectAsStateWithLifecycle()
    val history by viewModel.weightHistory.collectAsStateWithLifecycle()
    val isConnected = connectionState is ConnectionState.Connected

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(device.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    when (connectionState) {
                        is ConnectionState.Connected -> "Connected"
                        is ConnectionState.Reconnecting -> "Reconnecting…"
                        else -> "Connecting…"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = { viewModel.disconnect() }) { Text("Disconnect") }
        }

        Text(
            text = formatWeight(latestWeight),
            style = MaterialTheme.typography.displayLarge,
            modifier = Modifier.padding(vertical = 32.dp),
        )

        ForceChart(samples = history, modifier = Modifier.padding(bottom = 24.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = { viewModel.tare() },
                enabled = isConnected,
                modifier = Modifier.weight(1f),
            ) {
                Text("Tare")
            }
            Button(
                onClick = { viewModel.toggleMeasurement() },
                enabled = isConnected,
                modifier = Modifier.weight(1f),
            ) {
                Text(if (isMeasuring) "Stop" else "Start")
            }
        }
    }
}

private fun formatWeight(kilograms: Float?): String {
    if (kilograms == null) return "-- kg"
    val rounded = (kilograms * 10).roundToInt() / 10f
    return "$rounded kg"
}
