package com.wiktor.bigbanger.ble

import android.bluetooth.BluetoothDevice

data class BleDevice(
    val device: BluetoothDevice?,
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

    /**
     * The link dropped unexpectedly (e.g. the scale was switched off) and a
     * background reconnect to the same device is in progress. Distinct from
     * [Disconnected], which only follows a deliberate [ScaleSource.disconnect] call.
     */
    data class Reconnecting(val device: BleDevice) : ConnectionState
}
