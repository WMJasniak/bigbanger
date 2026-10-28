package com.wiktor.bigbanger.training

/** [PREP] shows no chart and starts no measurement, like [REST] -- it exists purely
 * to give a heads-up of the grip/hand the next [WORK] step will need. */
enum class StepKind { WORK, REST, PREP }

enum class Hand {
    LEFT, RIGHT;

    val displayLabel: String get() = if (this == LEFT) "Left hand" else "Right hand"
}

/**
 * One timed phase of a program. [percentOfMax] == null means "no target" --
 * the signal [training.ProgramRunner] uses to switch a WORK step into
 * attempt-decision mode (used by Max Testing, but not hardcoded to it).
 *
 * [gripTypeName] is set only by programs with [ProgramDefinition.usesFixedGripSequence]:
 * it names a specific grip position (e.g. "Front-2 Open (Left hand)") that this
 * step always targets, overriding whatever grip the user would otherwise pick
 * before running -- it's the lookup key into [TrainingRepo], not shown as-is.
 * [gripLabel]/[hand] are the human-readable versions of that, shown prominently
 * during the step itself and, one step ahead, during the [REST] before it.
 */
data class ProgramStep(
    val kind: StepKind,
    val durationSeconds: Int,
    val percentOfMax: Float? = null,
    val toleranceBandPoints: Float = 0f,
    val label: String? = null,
    val gripTypeName: String? = null,
    val gripLabel: String? = null,
    val hand: Hand? = null,
)

enum class ProgramId { MAX_HANGS, MAX_TESTING, WARM_UP, NO_HANGS }

/**
 * [usesFixedGripSequence] means the program itself dictates which grip each
 * step targets (via [ProgramStep.gripTypeName]), so the normal "pick one grip
 * before you start" screen is skipped entirely for it.
 */
data class ProgramDefinition(
    val id: ProgramId,
    val title: String,
    val hasNoTarget: Boolean,
    val steps: List<ProgramStep>,
    val usesFixedGripSequence: Boolean = false,
)
