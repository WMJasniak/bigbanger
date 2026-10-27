package com.wiktor.bigbanger.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val SCAN_TIMEOUT_MS = 15_000L
private const val MAX_HISTORY_SAMPLES = 1500

/**
 * Talks to a BigBanger / Tindeq-Progressor-compatible BLE scale using Android's
 * native Bluetooth LE APIs. All public methods are safe to call from the main thread;
 * the caller is responsible for checking runtime Bluetooth permissions first.
 */
@SuppressLint("MissingPermission")
class ProgressorBleManager(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(Dispatchers.Main.immediate + Job())

    private val bluetoothManager = appContext.getSystemService(BluetoothManager::class.java)
    private val adapter get() = bluetoothManager?.adapter

    private var gatt: BluetoothGatt? = null
    private var controlCharacteristic: BluetoothGattCharacteristic? = null
    private var scanTimeoutJob: Job? = null

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState = _connectionState.asStateFlow()

    private val _scanResults = MutableStateFlow<List<BleDevice>>(emptyList())
    val scanResults = _scanResults.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _isMeasuring = MutableStateFlow(false)
    val isMeasuring = _isMeasuring.asStateFlow()

    private val _latestWeightKg = MutableStateFlow<Float?>(null)
    val latestWeightKg = _latestWeightKg.asStateFlow()

    private val _weightHistory = MutableStateFlow<List<WeightSample>>(emptyList())
    val weightHistory = _weightHistory.asStateFlow()

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = result.scanRecord?.deviceName ?: device.name ?: return
            if (!name.startsWith(ProgressorProtocol.DEVICE_NAME_PREFIX)) return
            val found = BleDevice(device = device, name = name, address = device.address, rssi = result.rssi)
            _scanResults.update { current ->
                if (current.any { it.address == found.address }) {
                    current.map { if (it.address == found.address) found else it }
                } else {
                    current + found
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _isScanning.value = false
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(bluetoothGatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> bluetoothGatt.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    bluetoothGatt.close()
                    gatt = null
                    controlCharacteristic = null
                    _isMeasuring.value = false
                    _connectionState.value = ConnectionState.Disconnected
                }
            }
        }

        override fun onServicesDiscovered(bluetoothGatt: BluetoothGatt, status: Int) {
            val device = (_connectionState.value as? ConnectionState.Connecting)?.device ?: return
            val service = bluetoothGatt.getService(ProgressorProtocol.SERVICE_UUID)
            val data = service?.getCharacteristic(ProgressorProtocol.DATA_CHARACTERISTIC_UUID)
            val control = service?.getCharacteristic(ProgressorProtocol.CONTROL_CHARACTERISTIC_UUID)
            if (data == null || control == null) {
                bluetoothGatt.disconnect()
                return
            }
            controlCharacteristic = control
            enableWeightNotifications(bluetoothGatt, data)
            _connectionState.value = ConnectionState.Connected(device)
        }

        override fun onCharacteristicChanged(
            bluetoothGatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleNotification(characteristic, value)
        }

        @Deprecated("Deprecated in Java, kept for devices targeting < API 33")
        override fun onCharacteristicChanged(bluetoothGatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return // handled by the overload above
            @Suppress("DEPRECATION")
            handleNotification(characteristic, characteristic.value ?: return)
        }
    }

    private fun handleNotification(characteristic: BluetoothGattCharacteristic, value: ByteArray) {
        if (characteristic.uuid != ProgressorProtocol.DATA_CHARACTERISTIC_UUID) return
        val sample = ProgressorProtocol.decodeWeight(value) ?: return
        _latestWeightKg.value = sample.kilograms
        _weightHistory.update { history ->
            (history + sample).let { if (it.size > MAX_HISTORY_SAMPLES) it.takeLast(MAX_HISTORY_SAMPLES) else it }
        }
    }

    fun startScan() {
        val scanner = adapter?.bluetoothLeScanner ?: return
        _scanResults.value = emptyList()
        _isScanning.value = true
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        // No ScanFilter: the Progressor service UUID isn't present in the advertising
        // packet on this hardware, only the device name is reliable (see ProgressorProtocol).
        scanner.startScan(emptyList(), settings, scanCallback)
        scanTimeoutJob?.cancel()
        scanTimeoutJob = scope.launch {
            delay(SCAN_TIMEOUT_MS)
            stopScan()
        }
    }

    fun stopScan() {
        scanTimeoutJob?.cancel()
        if (!_isScanning.value) return
        adapter?.bluetoothLeScanner?.stopScan(scanCallback)
        _isScanning.value = false
    }

    fun connect(device: BleDevice) {
        stopScan()
        _connectionState.value = ConnectionState.Connecting(device)
        gatt = device.device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
    }

    fun disconnect() {
        gatt?.disconnect()
    }

    fun tare() = writeControlOpcode(ProgressorProtocol.ControlOpcode.TARE)

    fun startMeasurement() {
        if (writeControlOpcode(ProgressorProtocol.ControlOpcode.START_MEASUREMENT)) {
            _weightHistory.value = emptyList()
            _isMeasuring.value = true
        }
    }

    fun stopMeasurement() {
        if (writeControlOpcode(ProgressorProtocol.ControlOpcode.STOP_MEASUREMENT)) {
            _isMeasuring.value = false
        }
    }

    fun shutdown() {
        stopScan()
        gatt?.close()
        gatt = null
        scope.coroutineContext[Job]?.cancel()
    }

    private fun writeControlOpcode(opcode: Byte): Boolean {
        val bluetoothGatt = gatt ?: return false
        val characteristic = controlCharacteristic ?: return false
        return writeCharacteristicCompat(bluetoothGatt, characteristic, byteArrayOf(opcode))
    }

    private fun enableWeightNotifications(bluetoothGatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        bluetoothGatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(ProgressorProtocol.CLIENT_CHARACTERISTIC_CONFIG_UUID) ?: return
        writeDescriptorCompat(bluetoothGatt, descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
    }

    private fun writeCharacteristicCompat(
        bluetoothGatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray,
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bluetoothGatt.writeCharacteristic(
                characteristic,
                value,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = value
            @Suppress("DEPRECATION")
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            bluetoothGatt.writeCharacteristic(characteristic)
        }
    }

    private fun writeDescriptorCompat(bluetoothGatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, value: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bluetoothGatt.writeDescriptor(descriptor, value)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            bluetoothGatt.writeDescriptor(descriptor)
        }
    }
}
