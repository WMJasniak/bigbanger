package com.wiktor.bigbanger.ble

import kotlinx.coroutines.flow.StateFlow

/**
 * Common surface for anything that can act as a connected force gauge: the
 * real BLE scale ([ProgressorBleManager]) or a [SimulatedScaleSource] for
 * exercising the app without hardware present.
 */
interface ScaleSource {
    val connectionState: StateFlow<ConnectionState>
    val scanResults: StateFlow<List<BleDevice>>
    val isScanning: StateFlow<Boolean>
    val isMeasuring: StateFlow<Boolean>
    val latestWeightKg: StateFlow<Float?>
    val weightHistory: StateFlow<List<WeightSample>>

    fun startScan()
    fun stopScan()
    fun connect(device: BleDevice)
    fun disconnect()
    fun tare()
    fun startMeasurement()
    fun stopMeasurement()
    fun shutdown()
}
