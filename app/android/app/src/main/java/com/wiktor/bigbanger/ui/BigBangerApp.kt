package com.wiktor.bigbanger.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
fun BigBangerApp(viewModel: BigBangerViewModel, updateStatus: String?, onCheckUpdates: () -> Unit) {
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

    var screen by remember { mutableStateOf<Screen>(Screen.Home) }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            viewModel = viewModel,
            onSelectProgram = { program ->
                viewModel.ensureFixedGripSequenceSeeded(program)
                val connected = viewModel.connectionState.value is ConnectionState.Connected
                screen = when {
                    !connected -> Screen.Connect(program)
                    program.usesFixedGripSequence -> Screen.Running(program, null)
                    else -> Screen.GripTypeSelect(program)
                }
            },
            onFreeMeasure = { screen = Screen.Connect(null) },
            onStats = { screen = Screen.Stats },
            onSettings = { screen = Screen.Settings },
        )

        is Screen.Connect -> {
            val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
            when (val cs = connectionState) {
                is ConnectionState.Connected -> {
                    val pending = s.pendingProgram
                    if (pending != null) {
                        LaunchedEffect(pending) {
                            screen = if (pending.usesFixedGripSequence) {
                                Screen.Running(pending, null)
                            } else {
                                Screen.GripTypeSelect(pending)
                            }
                        }
                    } else {
                        DeviceScreen(viewModel = viewModel, device = cs.device, onBack = { screen = Screen.Home })
                    }
                }
                is ConnectionState.Connecting ->
                    DeviceScreen(viewModel = viewModel, device = cs.device, onBack = { screen = Screen.Home })
                is ConnectionState.Reconnecting ->
                    DeviceScreen(viewModel = viewModel, device = cs.device, onBack = { screen = Screen.Home })
                ConnectionState.Disconnected -> ScanScreen(viewModel = viewModel, onBack = { screen = Screen.Home })
            }
        }

        is Screen.GripTypeSelect -> GripTypeSelectScreen(
            viewModel = viewModel,
            program = s.program,
            onBack = { screen = Screen.Home },
            onSelect = { grip -> screen = Screen.Running(s.program, grip) },
        )

        is Screen.Running -> ProgramRunnerScreen(
            viewModel = viewModel,
            program = s.program,
            gripType = s.gripType,
            onFinished = { screen = Screen.Home },
        )

        Screen.Stats -> StatsScreen(viewModel = viewModel, onBack = { screen = Screen.Home })
        Screen.Settings -> SettingsScreen(
            viewModel = viewModel,
            updateStatus = updateStatus,
            onCheckUpdates = onCheckUpdates,
            onBack = { screen = Screen.Home },
        )
    }
}
