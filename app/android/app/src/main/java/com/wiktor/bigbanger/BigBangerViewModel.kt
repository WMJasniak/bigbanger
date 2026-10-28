package com.wiktor.bigbanger

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wiktor.bigbanger.ble.BleDevice
import com.wiktor.bigbanger.ble.ConnectionState
import com.wiktor.bigbanger.ble.ProgressorBleManager
import com.wiktor.bigbanger.ble.ScaleSource
import com.wiktor.bigbanger.ble.SimulatedScaleSource
import com.wiktor.bigbanger.ble.WeightSample
import com.wiktor.bigbanger.training.ProgramDefinition
import com.wiktor.bigbanger.training.ProgramStep
import com.wiktor.bigbanger.training.StepKind
import com.wiktor.bigbanger.training.TrainingRepo
import com.wiktor.bigbanger.training.expandWithGripSequence
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class BigBangerViewModel(application: Application) : AndroidViewModel(application) {
    private val realSource = ProgressorBleManager(application)
    private val simulatedSource = SimulatedScaleSource()
    private val _activeSource = MutableStateFlow<ScaleSource>(realSource)
    private val activeSource: ScaleSource get() = _activeSource.value

    val trainingRepo = TrainingRepo(application).also { it.load() }

    // Backed by flatMapLatest (not a plain `val x = activeSource.x`) so that
    // collectors keep receiving updates after connectSimulated() swaps the
    // underlying source -- a direct alias would keep pointing at the old one.
    val connectionState: StateFlow<ConnectionState> = _activeSource.flatMapLatest { it.connectionState }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ConnectionState.Disconnected)
    val scanResults: StateFlow<List<BleDevice>> = _activeSource.flatMapLatest { it.scanResults }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val isScanning: StateFlow<Boolean> = _activeSource.flatMapLatest { it.isScanning }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val isMeasuring: StateFlow<Boolean> = _activeSource.flatMapLatest { it.isMeasuring }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val latestWeightKg: StateFlow<Float?> = _activeSource.flatMapLatest { it.latestWeightKg }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val weightHistory: StateFlow<List<WeightSample>> = _activeSource.flatMapLatest { it.weightHistory }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun startScan() = activeSource.startScan()
    fun stopScan() = activeSource.stopScan()
    fun connect(device: BleDevice) = activeSource.connect(device)
    fun disconnect() = activeSource.disconnect()
    fun tare() = activeSource.tare()
    fun startMeasurement() = activeSource.startMeasurement()
    fun stopMeasurement() = activeSource.stopMeasurement()

    fun toggleMeasurement() {
        if (isMeasuring.value) activeSource.stopMeasurement() else activeSource.startMeasurement()
    }

    fun connectSimulated() {
        _activeSource.value = simulatedSource
        simulatedSource.connect(SimulatedScaleSource.FAKE_DEVICE)
    }

    /**
     * Programs with a fixed grip sequence (e.g. No Hangs) skip the "pick a hold,
     * grip and hand" screens entirely -- this makes sure each of their named
     * hold/grip/hand positions exists (with a seeded placeholder max, if it has
     * no real one yet) before the run starts. No-op for every other program.
     */
    fun ensureFixedGripSequenceSeeded(program: ProgramDefinition) {
        if (!program.usesFixedGripSequence) return
        program.steps
            .filter { it.holdTypeName != null && it.gripLabel != null && it.hand != null }
            .distinctBy { Triple(it.holdTypeName, it.gripLabel, it.hand) }
            .forEach { step ->
                trainingRepo.ensureGripVariantWithSeedMax(
                    holdTypeName = step.holdTypeName!!,
                    variantName = step.gripLabel!!,
                    hand = step.hand!!,
                    seedPeakKg = 1f,
                )
            }
    }

    /**
     * If the user has configured an ordered grip list for this program (via its own
     * settings screen), expands it into a run through all of them, same shape as No
     * Hangs' own hardcoded sequence; otherwise returns [program] unchanged, so a
     * program with nothing configured keeps asking which grip to use each run. Either
     * way, the result always starts with a PREP step: an expanded/fixed sequence
     * already leads with one per leg, but a plain single-grip program doesn't, so one
     * is added here -- there should never be a run that goes straight from the Ready
     * gate into a WORK step with no countdown to get into position first.
     */
    fun resolveProgramForRun(program: ProgramDefinition): ProgramDefinition {
        val resolved = if (program.usesFixedGripSequence) {
            program
        } else {
            val legs = trainingRepo.gripSequenceFor(program.id).mapNotNull { variant ->
                trainingRepo.holdTypes.value.firstOrNull { it.id == variant.holdTypeId }?.let { it to variant }
            }
            expandWithGripSequence(program, legs)
        }
        return if (resolved.steps.firstOrNull()?.kind == StepKind.PREP) {
            resolved
        } else {
            resolved.copy(steps = listOf(ProgramStep(kind = StepKind.PREP, durationSeconds = 10)) + resolved.steps)
        }
    }

    /** Lets the Program Runner nudge the simulated signal toward the active target band; no-op on the real scale. */
    fun setSimulatedTargetBand(range: ClosedFloatingPointRange<Float>?) {
        (activeSource as? SimulatedScaleSource)?.setTargetBand(range)
    }

    override fun onCleared() {
        realSource.shutdown()
        simulatedSource.shutdown()
        super.onCleared()
    }
}
