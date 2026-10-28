package com.wiktor.bigbanger.training

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wiktor.bigbanger.ble.WeightSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface RunnerPhase {
    /** Before [ProgramRunner.start] has been called (e.g. still on the Ready gate). */
    data object NotStarted : RunnerPhase
    data object Running : RunnerPhase
    data object Paused : RunnerPhase

    /**
     * The scale dropped mid-program. Distinct from [Paused]: this is not
     * user-initiated, the countdown is frozen the same way, but it resumes
     * automatically (not via a user tap) once the connection is restored.
     */
    data object ConnectionLost : RunnerPhase
    data class AwaitingAttemptDecision(val peakKg: Float) : RunnerPhase
    data object Finished : RunnerPhase
}

/**
 * Sequences a [ProgramDefinition] through its steps. Plain Kotlin, not a
 * second ViewModel -- this app has exactly one; this is `remember`-scoped
 * and driven by the caller's CoroutineScope (cancelled when it leaves
 * composition), matching the house style (see Morse's Repo/MorsePlayer).
 *
 * Measurement is started/stopped once per WORK step, not once for the whole
 * program: each attempt's peak is then trivially scoped to just that hold,
 * and REST steps show no chart at all, so the live force value is never
 * rendered as a number outside a WORK step.
 */
class ProgramRunner(
    val program: ProgramDefinition,
    private val maxKgForStep: (ProgramStep) -> Float?,
    private val scope: CoroutineScope,
    private val onSetMeasuring: (Boolean) -> Unit,
    private val currentHistory: () -> List<WeightSample>,
) {
    var stepIndex by mutableStateOf(0)
        private set
    var remainingSeconds by mutableStateOf(0)
        private set
    var phase: RunnerPhase by mutableStateOf(RunnerPhase.NotStarted)
        private set

    private var measuringWhenInterrupted = false
    private var tickJob: Job? = null

    val currentStep: ProgramStep get() = program.steps[stepIndex]

    val targetRangeKg: ClosedFloatingPointRange<Float>?
        get() {
            val pct = currentStep.percentOfMax ?: return null
            val max = maxKgForStep(currentStep) ?: return null
            val tol = currentStep.toleranceBandPoints
            val lo = max * (pct - tol)
            val hi = max * (pct + tol)
            return if (lo <= hi) lo..hi else hi..lo
        }

    fun start() = runStep()

    fun togglePause() {
        when (phase) {
            RunnerPhase.Running -> {
                tickJob?.cancel()
                phase = RunnerPhase.Paused
            }
            RunnerPhase.Paused -> {
                phase = RunnerPhase.Running
                startTicking()
            }
            else -> Unit
        }
    }

    /** Call when the scale's connection drops unexpectedly. No-op if not currently running. */
    fun pauseForDisconnect() {
        if (phase != RunnerPhase.Running) return
        tickJob?.cancel()
        measuringWhenInterrupted = currentStep.kind == StepKind.WORK
        if (measuringWhenInterrupted) onSetMeasuring(false)
        phase = RunnerPhase.ConnectionLost
    }

    /** Call once the scale reconnects. No-op unless currently paused for a connection loss. */
    fun resumeAfterReconnect() {
        if (phase != RunnerPhase.ConnectionLost) return
        if (measuringWhenInterrupted) onSetMeasuring(true)
        phase = RunnerPhase.Running
        startTicking()
    }

    /** Max Testing (or any no-target step): record or discard this attempt, then move on. */
    fun resolveAttempt(save: Boolean, onSave: (Float) -> Unit) {
        val decision = phase as? RunnerPhase.AwaitingAttemptDecision ?: return
        if (save) onSave(decision.peakKg)
        advance()
    }

    /** Max Testing: redo the current attempt slot without advancing. */
    fun retryAttempt() {
        if (phase !is RunnerPhase.AwaitingAttemptDecision) return
        runStep()
    }

    /** Max Testing: stop early but cleanly (distinct from [cancel] -- a deliberate "I'm done"). */
    fun finishEarly() {
        tickJob?.cancel()
        onSetMeasuring(false)
        phase = RunnerPhase.Finished
    }

    fun cancel() {
        tickJob?.cancel()
        onSetMeasuring(false)
        phase = RunnerPhase.Finished
    }

    private fun runStep() {
        tickJob?.cancel()
        remainingSeconds = currentStep.durationSeconds
        phase = RunnerPhase.Running
        if (currentStep.kind == StepKind.WORK) onSetMeasuring(true)
        startTicking()
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (remainingSeconds > 0 && isActive) {
                delay(1000)
                if (phase != RunnerPhase.Running) return@launch
                remainingSeconds -= 1
            }
            if (isActive) onStepTimerElapsed()
        }
    }

    private fun onStepTimerElapsed() {
        val step = currentStep
        if (step.kind == StepKind.WORK) {
            onSetMeasuring(false)
            if (step.percentOfMax == null) {
                val peak = currentHistory().maxOfOrNull { it.kilograms } ?: 0f
                phase = RunnerPhase.AwaitingAttemptDecision(peak)
                return
            }
        }
        advance()
    }

    private fun advance() {
        if (stepIndex >= program.steps.lastIndex) {
            phase = RunnerPhase.Finished
            return
        }
        stepIndex += 1
        runStep()
    }
}
