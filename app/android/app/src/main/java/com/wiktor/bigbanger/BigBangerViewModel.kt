package com.wiktor.bigbanger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.wiktor.bigbanger.ble.BleDevice
import com.wiktor.bigbanger.ble.ConnectionState
import com.wiktor.bigbanger.ble.ProgressorBleManager
import com.wiktor.bigbanger.ble.WeightSample
import kotlinx.coroutines.flow.StateFlow

class BigBangerViewModel(application: Application) : AndroidViewModel(application) {
    private val bleManager = ProgressorBleManager(application)

    val connectionState: StateFlow<ConnectionState> = bleManager.connectionState
    val scanResults: StateFlow<List<BleDevice>> = bleManager.scanResults
    val isScanning: StateFlow<Boolean> = bleManager.isScanning
    val isMeasuring: StateFlow<Boolean> = bleManager.isMeasuring
    val latestWeightKg: StateFlow<Float?> = bleManager.latestWeightKg
    val weightHistory: StateFlow<List<WeightSample>> = bleManager.weightHistory

    fun startScan() = bleManager.startScan()
    fun stopScan() = bleManager.stopScan()
    fun connect(device: BleDevice) = bleManager.connect(device)
    fun disconnect() = bleManager.disconnect()
    fun tare() = bleManager.tare()

    fun toggleMeasurement() {
        if (isMeasuring.value) bleManager.stopMeasurement() else bleManager.startMeasurement()
    }

    override fun onCleared() {
        bleManager.shutdown()
        super.onCleared()
    }
}
