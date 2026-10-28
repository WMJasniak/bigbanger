package com.wiktor.bigbanger.training

/**
 * The 4 built-in programs. Max Hangs (85% of max, 10s x 6 by default, 2 min rest) and
 * Max Testing (7s holds, progressive load, 2 min rest, up to 8 attempts by default)
 * follow Lattice Training's published testing/training instructions. Warm Up's ramp has
 * no single authoritative source. No Hangs follows the peer-reviewed Abrahangs protocol
 * (Gilmore, Klimek, Abrahamsson & Baar, "Effects of Different Loading Programs on
 * Finger Strength in Rock Climbers", Sports Medicine - Open, 2024): a fixed sequence of
 * 6 grip positions, 10s hold / 20s rest by default, at roughly 40% of each grip's own
 * max -- the paper doesn't measure load precisely, only instructing "a small strain,"
 * so 40% is the closest documented figure. The paper itself hangs both hands at once
 * off a bar; BigBanger's scale only measures one point of load, so each grip is trained
 * one hand at a time instead -- left then right -- which the paper doesn't call for but
 * is this app's own necessary adaptation to a single-load-cell device.
 *
 * Every program's reps/sets and work/rest timing is user-adjustable (see
 * [ProgramTiming], set via ui/ProgramGripConfigScreen.kt) -- the numbers above are just
 * each program's own default, from [defaultTimingFor].
 */
object Programs {
    private const val DEFAULT_TOLERANCE = 0.05f
    const val NO_HANGS_PREP_SECONDS = 10
    const val NO_HANGS_HOLD_TYPE = "20mm Edge"
    private const val GRIP_SEQUENCE_PREP_SECONDS = 10

    /**
     * No Hangs' default grip order and per-grip rep count, from the Abrahangs protocol --
     * used both to seed its grip sequence the first time (see
     * [TrainingRepo.ensureNoHangsDefaultSeeded]) and, in [buildNoHangs], as the per-grip
     * rep counts when [ProgramTiming.reps] is the "use protocol defaults" sentinel (0).
     */
    val NO_HANGS_DEFAULT_GRIPS = listOf(
        "4-Finger Half Crimp" to 6,
        "Front-3 Open" to 6,
        "Front-2 Open" to 2,
        "Middle-2 Open" to 2,
        "Front-2 Half Crimp" to 2,
        "Middle-2 Half Crimp" to 2,
    )

    private fun noHangsProtocolRepsFor(gripName: String): Int =
        NO_HANGS_DEFAULT_GRIPS.firstOrNull { it.first == gripName }?.second ?: 2

    /** Applies a [TrainingMode]'s volume cut to a reps/attempts/sets count, rounding up
     * and never going below 1 -- a cut rep count is still at least one rep, never zero. */
    fun scaleReps(reps: Int, mode: TrainingMode): Int =
        kotlin.math.ceil(reps * mode.repsFraction).toInt().coerceAtLeast(1)

    /** Each program's own built-in numbers, used until the user changes them in its settings. */
    fun defaultTimingFor(id: ProgramId): ProgramTiming = when (id) {
        ProgramId.MAX_HANGS -> ProgramTiming(reps = 6, workSeconds = 10, restSeconds = 120)
        ProgramId.MAX_TESTING -> ProgramTiming(reps = 8, workSeconds = 7, restSeconds = 120)
        ProgramId.WARM_UP -> ProgramTiming(reps = 1, workSeconds = 8, restSeconds = 60)
        ProgramId.NO_HANGS -> ProgramTiming(reps = 0, workSeconds = 10, restSeconds = 20)
    }

    /** Display-only stubs for the Home list and settings screens -- the steps actually
     * run come from this object's build functions, driven by each program's configured
     * grip sequence and [ProgramTiming]. */
    val MAX_HANGS = ProgramDefinition(ProgramId.MAX_HANGS, "Max Hangs", hasNoTarget = false, steps = emptyList())
    val MAX_TESTING = ProgramDefinition(ProgramId.MAX_TESTING, "Max Testing", hasNoTarget = true, steps = emptyList())
    val WARM_UP = ProgramDefinition(ProgramId.WARM_UP, "Warm Up", hasNoTarget = false, steps = emptyList())
    val NO_HANGS = ProgramDefinition(ProgramId.NO_HANGS, "No Hangs", hasNoTarget = false, steps = emptyList())

    val all = listOf(WARM_UP, NO_HANGS, MAX_HANGS, MAX_TESTING)

    /** Max Hangs' one-leg template: [timing.reps] reps (cut by [mode]) at 85% of max, separated by rest. */
    fun buildMaxHangsBase(timing: ProgramTiming, mode: TrainingMode): ProgramDefinition {
        val reps = scaleReps(timing.reps.coerceAtLeast(1), mode)
        return ProgramDefinition(
            id = ProgramId.MAX_HANGS,
            title = "Max Hangs",
            hasNoTarget = false,
            steps = buildList {
                repeat(reps) { i ->
                    add(
                        ProgramStep(
                            kind = StepKind.WORK,
                            durationSeconds = timing.workSeconds,
                            percentOfMax = 0.85f,
                            toleranceBandPoints = DEFAULT_TOLERANCE,
                            label = "Rep ${i + 1}/$reps",
                        ),
                    )
                    if (i < reps - 1) add(ProgramStep(StepKind.REST, timing.restSeconds))
                }
            },
        )
    }

    /** Max Testing's one-leg template: [timing.reps] no-target attempts (cut by [mode]), separated by rest. */
    fun buildMaxTestingBase(timing: ProgramTiming, mode: TrainingMode): ProgramDefinition {
        val attempts = scaleReps(timing.reps.coerceAtLeast(1), mode)
        return ProgramDefinition(
            id = ProgramId.MAX_TESTING,
            title = "Max Testing",
            hasNoTarget = true,
            steps = buildList {
                repeat(attempts) { i ->
                    add(ProgramStep(kind = StepKind.WORK, durationSeconds = timing.workSeconds, label = "Attempt ${i + 1}"))
                    if (i < attempts - 1) add(ProgramStep(StepKind.REST, timing.restSeconds))
                }
            },
        )
    }

    /** Warm Up's one-leg template: the 50/60/70/80/90% ramp, repeated [timing.reps] times
     * (sets), cut by [mode] -- though at the default of 1 set, [mode] has nothing left to
     * cut; the ramp's 5 steps themselves stay fixed regardless. */
    fun buildWarmUpBase(timing: ProgramTiming, mode: TrainingMode): ProgramDefinition {
        val sets = scaleReps(timing.reps.coerceAtLeast(1), mode)
        val percents = listOf(0.50f, 0.60f, 0.70f, 0.80f, 0.90f)
        return ProgramDefinition(
            id = ProgramId.WARM_UP,
            title = "Warm Up",
            hasNoTarget = false,
            steps = buildList {
                repeat(sets) { setIndex ->
                    percents.forEachIndexed { i, pct ->
                        add(
                            ProgramStep(
                                kind = StepKind.WORK,
                                durationSeconds = timing.workSeconds,
                                percentOfMax = pct,
                                toleranceBandPoints = DEFAULT_TOLERANCE,
                                label = "Step ${i + 1}/${percents.size}",
                            ),
                        )
                        val isLast = setIndex == sets - 1 && i == percents.lastIndex
                        if (!isLast) add(ProgramStep(StepKind.REST, timing.restSeconds))
                    }
                }
            },
        )
    }

    /**
     * No Hangs' own expansion: unlike [expandWithGripSequence]'s uniform per-leg template,
     * each grip gets its own rep count (see [NO_HANGS_DEFAULT_GRIPS]) when [timing.reps]
     * is 0 ("use protocol defaults"), or [timing.reps] uniformly for every grip otherwise
     * -- either way cut by [mode] afterward. Each grip is still trained one hand at a
     * time (left, then right) with one PREP per leg.
     */
    fun buildNoHangs(legs: List<Pair<HoldType, GripVariant>>, timing: ProgramTiming, mode: TrainingMode): ProgramDefinition {
        val handLegs = legs.flatMap { (holdType, variant) -> Hand.entries.map { hand -> Triple(holdType, variant, hand) } }
        val steps = buildList {
            handLegs.forEachIndexed { legIndex, (holdType, variant, hand) ->
                add(
                    ProgramStep(
                        kind = StepKind.PREP,
                        durationSeconds = NO_HANGS_PREP_SECONDS,
                        gripLabel = variant.name,
                        hand = hand,
                    ),
                )
                val baseReps = if (timing.reps > 0) timing.reps else noHangsProtocolRepsFor(variant.name)
                val reps = scaleReps(baseReps, mode)
                repeat(reps) { rep ->
                    add(
                        ProgramStep(
                            kind = StepKind.WORK,
                            durationSeconds = timing.workSeconds,
                            percentOfMax = 0.40f,
                            toleranceBandPoints = DEFAULT_TOLERANCE,
                            holdTypeName = holdType.name,
                            gripLabel = variant.name,
                            hand = hand,
                            label = "${rep + 1}/$reps",
                        ),
                    )
                    val isLastRepOfLastLeg = legIndex == handLegs.lastIndex && rep == reps - 1
                    if (!isLastRepOfLastLeg) add(ProgramStep(StepKind.REST, timing.restSeconds))
                }
            }
        }
        return ProgramDefinition(id = ProgramId.NO_HANGS, title = "No Hangs", hasNoTarget = false, steps = steps)
    }

    /**
     * Expands [base]'s own step template into a run through [legs] in the given order,
     * each grip trained one hand at a time (left then right) -- the same shape
     * [buildNoHangs] uses, just with [base]'s template repeated identically for every
     * leg instead of a per-grip rep count. [legs] must be non-empty; callers check for
     * an unconfigured (empty) grip sequence before resolving (see
     * [BigBangerViewModel.resolveProgramForRun]).
     */
    fun expandWithGripSequence(base: ProgramDefinition, legs: List<Pair<HoldType, GripVariant>>): ProgramDefinition {
        val handLegs = legs.flatMap { (holdType, variant) -> Hand.entries.map { hand -> Triple(holdType, variant, hand) } }
        val legGapSeconds = base.steps.firstOrNull { it.kind == StepKind.REST }?.durationSeconds ?: 60
        val steps = buildList {
            handLegs.forEachIndexed { legIndex, (holdType, variant, hand) ->
                add(ProgramStep(kind = StepKind.PREP, durationSeconds = GRIP_SEQUENCE_PREP_SECONDS, gripLabel = variant.name, hand = hand))
                base.steps.forEach { templateStep ->
                    add(
                        if (templateStep.kind == StepKind.WORK) {
                            templateStep.copy(holdTypeName = holdType.name, gripLabel = variant.name, hand = hand)
                        } else {
                            templateStep
                        },
                    )
                }
                if (legIndex != handLegs.lastIndex) add(ProgramStep(StepKind.REST, legGapSeconds))
            }
        }
        return base.copy(steps = steps)
    }
}
