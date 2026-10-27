package com.wiktor.bigbanger.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wiktor.bigbanger.BigBangerViewModel
import com.wiktor.bigbanger.ble.ConnectionState

private fun requiredBlePermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(android.Manifest.permission.BLUETOOTH_SCAN, android.Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
    }

private fun hasBlePermissions(context: android.content.Context): Boolean =
    requiredBlePermissions().all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

@Composable
fun BigBangerApp(viewModel: BigBangerViewModel) {
    val context = LocalContext.current
    var permissionsGranted by remember { mutableStateOf(hasBlePermissions(context)) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants -> permissionsGranted = grants.values.all { it } }

    val enableBtLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { /* user may retry Scan after returning */ }

    if (!permissionsGranted) {
        PermissionScreen(onRequestPermissions = { permissionLauncher.launch(requiredBlePermissions()) })
        return
    }

    val bluetoothManager = remember { context.getSystemService(BluetoothManager::class.java) }
    val adapterEnabled = bluetoothManager?.adapter?.isEnabled == true
    if (!adapterEnabled) {
        PermissionScreen(
            message = "Bluetooth is turned off.",
            buttonLabel = "Enable Bluetooth",
            onRequestPermissions = { enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
        )
        return
    }

    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    when (val state = connectionState) {
        is ConnectionState.Connected -> DeviceScreen(viewModel = viewModel, device = state.device)
        is ConnectionState.Connecting -> DeviceScreen(viewModel = viewModel, device = state.device)
        ConnectionState.Disconnected -> ScanScreen(viewModel = viewModel)
    }
}
