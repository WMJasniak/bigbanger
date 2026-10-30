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

/**
 * One stretch of program time, as an offset in seconds from "now" (negative = past,
 * positive = future, computed by [ProgramRunner.targetTimeline]). [centerKg]/[halfWidthKg]
 * describe a band of constant thickness that moves between the WORK target level and
 * zero for REST/PREP, rather than appearing/disappearing or changing thickness --
 * [ui.ForceChart] then smooths the handoff between adjacent segments itself.
 *
 * [legCenterKg] is this segment's leg's *real* WORK level regardless of the segment's
 * own kind -- unlike [centerKg], it does not drop to zero for REST/PREP -- so the
 * chart's y-axis can stay fixed to "this leg's work level and zero" across a work/rest
 * transition instead of jumping between them. Null if the leg has no real target at
 * all (e.g. Max Testing).
 */
data class TargetSegment(
    val startOffsetSeconds: Float,
    val endOffsetSeconds: Float,
    val centerKg: Float,
    val halfWidthKg: Float,
    val legCenterKg: Float?,
)

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
    data object Finished : RunnerPhase
}

/**
 * Sequences a [ProgramDefinition] through its steps. Plain Kotlin, not a
 * second ViewModel -- this app has exactly one; this is `remember`-scoped
 * and driven by the caller's CoroutineScope (cancelled when it leaves
 * composition), matching the house style (see Morse's Repo/MorsePlayer).
 *
 * Measurement is started exactly once, in [start], and stopped only in [cancel] or on
 * natural completion -- so the live force trace keeps plotting continuously through
 * every WORK/REST/PREP step, instead of going blank between holds. [onSetMeasuring]
 * must not be called again mid-run for an ordinary step transition: on the real scale
 * it re-sends the BLE start-measurement command, which resets the device's own clock
 * and wipes the sample history, breaking the rolling chart's continuity right at that
 * transition -- restarting measurement is only ever correct at the very start of a run
 * (or after a real disconnect, in [resumeAfterReconnect], where the device's clock has
 * already reset on its own).
 *
 * A no-target WORK step (Max Testing) auto-records its own peak via
 * [onAttemptCompleted] and moves straight on -- no per-attempt decision pause -- scoped
 * to just that hold by filtering on [stepStartTimestampSeconds] rather than by relying
 * on history being empty at the start of the step.
 */
class ProgramRunner(
    val program: ProgramDefinition,
    private val maxKgForStep: (ProgramStep) -> Float?,
    private val scope: CoroutineScope,
    private val onSetMeasuring: (Boolean) -> Unit,
    private val currentHistory: () -> List<WeightSample>,
    private val onAttemptCompleted: (ProgramStep, Float) -> Unit,
) {
    var stepIndex by mutableStateOf(0)
        private set
    var remainingSeconds by mutableStateOf(0)
        private set
    var phase: RunnerPhase by mutableStateOf(RunnerPhase.NotStarted)
        private set

    private var tickJob: Job? = null
    private var stepStartTimestampSeconds = 0f

    val currentStep: ProgramStep get() = program.steps[stepIndex]

    private fun bandFor(step: ProgramStep): ClosedFloatingPointRange<Float>? {
        val pct = step.percentOfMax ?: return null
        val max = maxKgForStep(step) ?: return null
        val tol = step.toleranceBandPoints
        val lo = max * (pct - tol)
        val hi = max * (pct + tol)
        return if (lo <= hi) lo..hi else hi..lo
    }

    /** The real target for the current step only -- what the simulator should chase. */
    val targetRangeKg: ClosedFloatingPointRange<Float>? get() = bandFor(currentStep)

    /**
     * The target band for a window of program time around "now", expressed as a
     * constant-thickness band whose position moves between a WORK step's target and
     * zero for REST/PREP (so those render the same width as the WORK step they
     * border). [nowTimestampSeconds] anchors "now" to the same device-clock reading
     * the chart's real data uses, rather than this runner's own once-a-second
     * countdown, so the two scroll in lockstep instead of drifting apart.
     */
    fun targetTimeline(pastSeconds: Float, futureSeconds: Float, nowTimestampSeconds: Float): List<TargetSegment> {
        fun centerOf(step: ProgramStep): Float = bandFor(step)?.let { (it.start + it.endInclusive) / 2f } ?: 0f
        fun halfWidthOf(step: ProgramStep): Float = bandFor(step)?.let { (it.endInclusive - it.start) / 2f } ?: 0f

        // A REST/PREP step has no band of its own: borrow one from whichever neighbor
        // actually has it. REST borrows from the WORK step it just finished (the one
        // before it); PREP borrows from the WORK step it's about to start (the one
        // after it) -- looking the other way would show the wrong leg's width for a
        // program with a fixed grip sequence, where neighboring legs differ.
        fun resolvedHalf(index: Int): Float {
            val step = program.steps[index]
            if (bandFor(step) != null) return halfWidthOf(step)
            val neighborIndex = if (step.kind == StepKind.REST) index - 1 else index + 1
            return program.steps.getOrNull(neighborIndex)?.let(::halfWidthOf) ?: 0f
        }

        fun resolvedLegCenter(index: Int): Float? {
            val step = program.steps[index]
            bandFor(step)?.let { return (it.start + it.endInclusive) / 2f }
            val neighborIndex = if (step.kind == StepKind.REST) index - 1 else index + 1
            val neighbor = program.steps.getOrNull(neighborIndex) ?: return null
            return bandFor(neighbor)?.let { (it.start + it.endInclusive) / 2f }
        }

        val elapsed = (nowTimestampSeconds - stepStartTimestampSeconds)
            .coerceIn(0f, currentStep.durationSeconds.toFloat())
        val remaining = currentStep.durationSeconds - elapsed

        val segments = mutableListOf<TargetSegment>()
        segments.add(
            TargetSegment(-elapsed, remaining, centerOf(currentStep), resolvedHalf(stepIndex), resolvedLegCenter(stepIndex)),
        )

        var cursor = -elapsed
        var i = stepIndex - 1
        while (cursor > -pastSeconds && i >= 0) {
            val step = program.steps[i]
            val start = cursor - step.durationSeconds
            segments.add(TargetSegment(start, cursor, centerOf(step), resolvedHalf(i), resolvedLegCenter(i)))
            cursor = start
            i -= 1
        }

        cursor = remaining
        i = stepIndex + 1
        while (cursor < futureSeconds && i < program.steps.size) {
            val step = program.steps[i]
            val end = cursor + step.durationSeconds
            segments.add(TargetSegment(cursor, end, centerOf(step), resolvedHalf(i), resolvedLegCenter(i)))
            cursor = end
            i += 1
        }

        return segments
    }

    fun start() {
        onSetMeasuring(true)
        runStep()
    }

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
        onSetMeasuring(false)
        phase = RunnerPhase.ConnectionLost
    }

    /** Call once the scale reconnects. No-op unless currently paused for a connection loss. */
    fun resumeAfterReconnect() {
        if (phase != RunnerPhase.ConnectionLost) return
        onSetMeasuring(true)
        // The device's own clock (and its history) resets with the fresh connection.
        stepStartTimestampSeconds = currentHistory().lastOrNull()?.timestampSeconds ?: 0f
        phase = RunnerPhase.Running
        startTicking()
    }

    fun cancel() {
        tickJob?.cancel()
        onSetMeasuring(false)
        phase = RunnerPhase.Finished
    }

    private fun runStep() {
        tickJob?.cancel()
        remainingSeconds = currentStep.durationSeconds
        stepStartTimestampSeconds = currentHistory().lastOrNull()?.timestampSeconds ?: 0f
        phase = RunnerPhase.Running
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
        if (step.kind == StepKind.WORK && step.percentOfMax == null) {
            val peak = currentHistory()
                .filter { it.timestampSeconds >= stepStartTimestampSeconds }
                .maxOfOrNull { it.kilograms } ?: 0f
            onAttemptCompleted(step, peak)
        }
        advance()
    }

    private fun advance() {
        if (stepIndex >= program.steps.lastIndex) {
            onSetMeasuring(false)
            phase = RunnerPhase.Finished
            return
        }
        stepIndex += 1
        runStep()
    }
}
