package com.wiktor.bigbanger.training

enum class StepKind { WORK, REST }

/**
 * One timed phase of a program. [percentOfMax] == null means "no target" --
 * the signal [training.ProgramRunner] uses to switch a WORK step into
 * attempt-decision mode (used by Max Testing, but not hardcoded to it).
 */
data class ProgramStep(
    val kind: StepKind,
    val durationSeconds: Int,
    val percentOfMax: Float? = null,
    val toleranceBandPoints: Float = 0f,
    val label: String? = null,
)

enum class ProgramId { MAX_HANGS, MAX_TESTING, WARM_UP, NO_HANGS }

data class ProgramDefinition(
    val id: ProgramId,
    val title: String,
    val description: String,
    val isApproximate: Boolean,
    val hasNoTarget: Boolean,
    val steps: List<ProgramStep>,
)
