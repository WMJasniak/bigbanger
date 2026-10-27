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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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

@Composable
fun ScanScreen(viewModel: BigBangerViewModel, onBack: (() -> Unit)? = null) {
    val devices by viewModel.scanResults.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val sorted = devices.sortedByDescending { it.rssi }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            onBack?.let { back -> TextButton(onClick = back) { Text("Cancel") } }
            Text("Nearby scales", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { if (isScanning) viewModel.stopScan() else viewModel.startScan() }) {
                Text(if (isScanning) "Stop" else "Scan")
            }
        }

        if (isScanning) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        if (sorted.isEmpty() && !isScanning) {
            Text(
                "No devices found yet. Tap Scan and make sure your BigBanger is turned on.",
                modifier = Modifier.padding(top = 24.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(sorted, key = { it.address }) { device ->
                DeviceRow(device = device, onClick = { viewModel.connect(device) })
            }
        }

        TextButton(onClick = { viewModel.connectSimulated() }, modifier = Modifier.fillMaxWidth()) {
            Text("No scale on hand? Use a simulated scale")
        }
    }
}

@Composable
private fun DeviceRow(device: BleDevice, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(device.name, style = MaterialTheme.typography.titleMedium)
                Text(device.address, style = MaterialTheme.typography.bodySmall)
            }
            Text("${device.rssi} dBm", style = MaterialTheme.typography.bodySmall)
        }
    }
}
