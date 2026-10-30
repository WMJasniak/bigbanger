package com.wiktor.bigbanger.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.wiktor.bigbanger.training.ProgramDefinition

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
    var unconfiguredProgram by remember { mutableStateOf<ProgramDefinition?>(null) }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            viewModel = viewModel,
            onSelectProgram = { baseProgram ->
                if (!viewModel.hasGripsConfigured(baseProgram.id)) {
                    unconfiguredProgram = baseProgram
                } else {
                    // The actual grip sequence/timing/mode resolution happens on the Ready
                    // gate (ProgramRunnerScreen), once the volume mode is picked -- this
                    // only checks there is something to run at all.
                    val connected = viewModel.connectionState.value is ConnectionState.Connected
                    screen = if (connected) Screen.Running(baseProgram) else Screen.Connect(baseProgram)
                }
            },
            onOpenProgramGripConfig = { program -> screen = Screen.ProgramGripConfig(program) },
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
                        LaunchedEffect(pending) { screen = Screen.Running(pending) }
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

        is Screen.Running -> ProgramRunnerScreen(
            viewModel = viewModel,
            program = s.program,
            onFinished = { screen = Screen.Home },
        )

        is Screen.ProgramGripConfig -> ProgramGripConfigScreen(
            viewModel = viewModel,
            program = s.program,
            onBack = { screen = Screen.Home },
        )

        Screen.Stats -> StatsScreen(
            viewModel = viewModel,
            onBack = { screen = Screen.Home },
            onOpenGripHistory = { variant -> screen = Screen.GripMaxHistory(variant) },
        )
        is Screen.GripMaxHistory -> GripMaxHistoryScreen(
            viewModel = viewModel,
            variant = s.variant,
            onBack = { screen = Screen.Stats },
        )
        Screen.Settings -> SettingsScreen(
            viewModel = viewModel,
            updateStatus = updateStatus,
            onCheckUpdates = onCheckUpdates,
            onOpenHolds = { screen = Screen.HoldsSettings },
            onBack = { screen = Screen.Home },
        )
        Screen.HoldsSettings -> HoldsSettingsScreen(viewModel = viewModel, onBack = { screen = Screen.Settings })
    }

    unconfiguredProgram?.let { program ->
        AlertDialog(
            onDismissRequest = { unconfiguredProgram = null },
            title = { Text("No grips configured") },
            text = { Text("No grips added to ${program.title}. Add it in the settings.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        unconfiguredProgram = null
                        screen = Screen.ProgramGripConfig(program)
                    },
                ) { Text("Open settings") }
            },
            dismissButton = { TextButton(onClick = { unconfiguredProgram = null }) { Text("Cancel") } },
        )
    }
}
