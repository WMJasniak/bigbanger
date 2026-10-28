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
 * [holdTypeName]/[gripLabel]/[hand] together name a specific hold type, grip
 * variant on it, and hand (e.g. "20mm Edge" / "Front-2 Open" / Left) that this
 * step targets -- every program is run against a grip sequence configured in
 * its own settings (see [BigBangerViewModel.resolveProgramForRun]), so every
 * WORK/PREP step in a resolved program carries these. [holdTypeName]/[gripLabel]
 * double as the lookup key into [TrainingRepo]; [gripLabel]/[hand] are also
 * shown prominently during the step itself and, one step ahead, during the
 * [REST] before it.
 */
data class ProgramStep(
    val kind: StepKind,
    val durationSeconds: Int,
    val percentOfMax: Float? = null,
    val toleranceBandPoints: Float = 0f,
    val label: String? = null,
    val holdTypeName: String? = null,
    val gripLabel: String? = null,
    val hand: Hand? = null,
)

enum class ProgramId { MAX_HANGS, MAX_TESTING, WARM_UP, NO_HANGS }

/** [steps] on [Programs.all]'s entries are display-only placeholders; the steps actually
 * run come from resolving the program's configured grip sequence (see [BigBangerViewModel.resolveProgramForRun]). */
data class ProgramDefinition(
    val id: ProgramId,
    val title: String,
    val hasNoTarget: Boolean,
    val steps: List<ProgramStep>,
)

/**
 * A program's user-adjustable sets/reps and timing, set via its own settings screen
 * (see ui/ProgramGripConfigScreen.kt) and fed into [Programs]' per-program build
 * functions to produce the steps actually run. [reps] means something slightly
 * different per program -- reps per grip for Max Hangs, attempts per grip for Max
 * Testing, ramp repeats (sets) for Warm Up, and reps per grip for No Hangs where 0 is a
 * sentinel for "use the Abrahangs protocol's own per-grip rep counts" instead of a
 * single uniform number. [workSeconds]/[restSeconds] are a hold's duration and the rest
 * between them; see [Programs.defaultTimingFor] for each program's defaults.
 */
data class ProgramTiming(
    val reps: Int,
    val workSeconds: Int,
    val restSeconds: Int,
)

/**
 * A quick, per-run volume override picked on the Ready gate right before starting --
 * deliberately not persisted like [ProgramTiming], since it is meant as a one-off "I'm
 * in a hurry today" choice rather than a standing setting that could silently stay on
 * Express next time. [repsFraction] scales whatever reps/attempts/sets count a program
 * would otherwise use (see [Programs.scaleReps]); [FULL] is a no-op.
 */
enum class TrainingMode(val label: String, val repsFraction: Float) {
    FULL("Full", 1f),
    FAST("Fast", 0.66f),
    EXPRESS("Express", 0.4f),
}
