package com.wiktor.bigbanger.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
fun DeviceScreen(viewModel: BigBangerViewModel, device: BleDevice, onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val latestWeight by viewModel.latestWeightKg.collectAsStateWithLifecycle()
    val history by viewModel.weightHistory.collectAsStateWithLifecycle()
    val isConnected = connectionState is ConnectionState.Connected

    // Raw Data just rolls continuously once connected -- no Start/Stop button to
    // remember to press first.
    LaunchedEffect(isConnected) {
        if (isConnected) viewModel.startMeasurement()
    }

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

        ForceChart(
            samples = history,
            modifier = Modifier.fillMaxWidth().height(220.dp).padding(bottom = 24.dp),
        )

        OutlinedButton(
            onClick = { viewModel.tare() },
            enabled = isConnected,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Tare")
        }
    }
}

private fun formatWeight(kilograms: Float?): String {
    if (kilograms == null) return "-- kg"
    val rounded = (kilograms * 10).roundToInt() / 10f
    return "$rounded kg"
}
