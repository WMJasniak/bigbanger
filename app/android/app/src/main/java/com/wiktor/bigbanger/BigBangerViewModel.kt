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
import com.wiktor.bigbanger.training.ProgramId
import com.wiktor.bigbanger.training.Programs
import com.wiktor.bigbanger.training.TrainingMode
import com.wiktor.bigbanger.training.TrainingRepo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class BigBangerViewModel(application: Application) : AndroidViewModel(application) {
    private val realSource = ProgressorBleManager(application)
    private val simulatedSource = SimulatedScaleSource()
    private val _activeSource = MutableStateFlow<ScaleSource>(realSource)
    private val activeSource: ScaleSource get() = _activeSource.value

    val trainingRepo = TrainingRepo(application).also {
        it.load()
        it.ensureNoHangsDefaultSeeded()
    }

    private val themeSettings = ThemeSettings(application)
    private val _isDarkTheme = MutableStateFlow(themeSettings.darkMode)
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme.asStateFlow()

    fun setDarkTheme(enabled: Boolean) {
        themeSettings.darkMode = enabled
        _isDarkTheme.value = enabled
    }

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

    /** True once at least one of a program's configured grips is enabled -- used before
     * ever navigating anywhere to decide whether to prompt the user to its settings
     * instead. Independent of [TrainingMode], which only ever scales reps, never grips. */
    fun hasGripsConfigured(programId: ProgramId): Boolean {
        val disabled = trainingRepo.disabledGripsFor(programId)
        return trainingRepo.gripSequenceFor(programId).any { it.id !in disabled }
    }

    /**
     * Expands [program] into a run through its configured, enabled grips (set via its
     * own settings screen) -- every grip trained one hand at a time, same shape as No
     * Hangs always used -- with [mode] cutting reps/attempts/sets for a quick "I'm in a
     * hurry" override. Returns null if nothing enabled is configured, so the caller can
     * prompt the user to set it up instead of starting a run with no grip to target. The
     * result always starts with a PREP step (every expansion already leads with one per
     * leg), so there is never a run that goes straight from the Ready gate into a WORK
     * step with no countdown to get into position first.
     */
    fun resolveProgramForRun(program: ProgramDefinition, mode: TrainingMode): ProgramDefinition? {
        val disabled = trainingRepo.disabledGripsFor(program.id)
        val legs = trainingRepo.gripSequenceFor(program.id)
            .filter { it.id !in disabled }
            .mapNotNull { variant -> trainingRepo.holdTypes.value.firstOrNull { it.id == variant.holdTypeId }?.let { it to variant } }
        if (legs.isEmpty()) return null
        val timing = trainingRepo.timingFor(program.id)
        return when (program.id) {
            ProgramId.NO_HANGS -> Programs.buildNoHangs(legs, timing, mode)
            ProgramId.MAX_HANGS -> Programs.expandWithGripSequence(Programs.buildMaxHangsBase(timing, mode), legs)
            ProgramId.MAX_TESTING -> Programs.expandWithGripSequence(Programs.buildMaxTestingBase(timing, mode), legs)
            ProgramId.WARM_UP -> Programs.expandWithGripSequence(Programs.buildWarmUpBase(timing, mode), legs)
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
