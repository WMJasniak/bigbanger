package com.wiktor.bigbanger.ble

import android.bluetooth.BluetoothDevice

data class BleDevice(
    val device: BluetoothDevice,
    val name: String,
    val address: String,
    val rssi: Int,
)

data class WeightSample(
    val timestampSeconds: Float,
    val kilograms: Float,
)

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connecting(val device: BleDevice) : ConnectionState
    data class Connected(val device: BleDevice) : ConnectionState
}
