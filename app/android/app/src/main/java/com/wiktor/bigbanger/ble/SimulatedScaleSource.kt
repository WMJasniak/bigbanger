package com.wiktor.bigbanger.ble

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

private const val TICK_MS = 100L
private const val MAX_HISTORY_SAMPLES = 1500

/**
 * Fakes a connected scale so the rest of the app (programs, charts, the
 * training repo) can be exercised without the real BLE hardware present.
 * Not aware of [com.wiktor.bigbanger.training] at all -- [setTargetBand] is
 * an optional hint a caller may use to make the demo signal drift toward
 * whatever band is currently on screen, but nothing here requires it.
 */
class SimulatedScaleSource : ScaleSource {
    private val scope = CoroutineScope(Dispatchers.Main.immediate + Job())
    private var tickJob: Job? = null
    private var currentKg = 0f
    private var noise = 0f
    private var targetBandKg: ClosedFloatingPointRange<Float>? = null
    private var startNanos = 0L

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState = _connectionState.asStateFlow()
    private val _scanResults = MutableStateFlow<List<BleDevice>>(emptyList())
    override val scanResults = _scanResults.asStateFlow()
    private val _isScanning = MutableStateFlow(false)
    override val isScanning = _isScanning.asStateFlow()
    private val _isMeasuring = MutableStateFlow(false)
    override val isMeasuring = _isMeasuring.asStateFlow()
    private val _latestWeightKg = MutableStateFlow<Float?>(null)
    override val latestWeightKg = _latestWeightKg.asStateFlow()
    private val _weightHistory = MutableStateFlow<List<WeightSample>>(emptyList())
    override val weightHistory = _weightHistory.asStateFlow()

    override fun startScan() { /* no-op: connectSimulated() bypasses scanning entirely */ }
    override fun stopScan() { }

    override fun connect(device: BleDevice) {
        scope.launch {
            _connectionState.value = ConnectionState.Connecting(device)
            delay(400)
            _connectionState.value = ConnectionState.Connected(device)
            startTicking()
        }
    }

    override fun disconnect() {
        tickJob?.cancel()
        _isMeasuring.value = false
        _connectionState.value = ConnectionState.Disconnected
    }

    override fun tare() {
        currentKg = 0f
    }

    override fun startMeasurement() {
        _weightHistory.value = emptyList()
        startNanos = System.nanoTime()
        _isMeasuring.value = true
    }

    override fun stopMeasurement() {
        _isMeasuring.value = false
    }

    override fun shutdown() {
        tickJob?.cancel()
        scope.coroutineContext[Job]?.cancel()
    }

    /** Optional: bias the wandering signal toward a target band, e.g. while a training step is active. */
    fun setTargetBand(range: ClosedFloatingPointRange<Float>?) {
        targetBandKg = range
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                currentKg = nextSample(currentKg)
                _latestWeightKg.value = currentKg
                if (_isMeasuring.value) {
                    val t = (System.nanoTime() - startNanos) / 1_000_000_000f
                    _weightHistory.update { (it + WeightSample(t, currentKg)).takeLast(MAX_HISTORY_SAMPLES) }
                }
            }
        }
    }

    private fun nextSample(previous: Float): Float {
        val target = targetBandKg?.let { (it.start + it.endInclusive) / 2f } ?: 0f
        val pull = (target - previous) * 0.08f
        // Low-pass filtered noise (correlated between ticks) reads as a believable
        // physical signal; independent per-tick noise looked jumpy/choppy on the chart.
        noise = (noise * 0.85f) + ((Random.nextFloat() - 0.5f) * 0.15f)
        return (previous + pull + noise).coerceIn(-2f, 80f)
    }

    companion object {
        val FAKE_DEVICE = BleDevice(device = null, name = "Simulated Scale", address = "SIMULATED", rssi = -40)
    }
}
