package com.wiktor.bigbanger.training

/**
 * The 4 built-in programs. Max Hangs (85% of max, 10s x 6, 2 min rest) and
 * Max Testing (7s holds, progressive load, 2 min rest, up to 8 attempts)
 * follow Lattice Training's published testing/training instructions.
 * Warm Up's ramp has no single authoritative source. No Hangs follows the
 * peer-reviewed Abrahangs protocol (Gilmore, Klimek, Abrahamsson & Baar,
 * "Effects of Different Loading Programs on Finger Strength in Rock
 * Climbers", Sports Medicine - Open, 2024): a fixed sequence of 6 grip
 * positions, 10s hold / 20s rest, at roughly 40% of each grip's own max --
 * the paper doesn't measure load precisely, only instructing "a small
 * strain," so 40% is the closest documented figure. The paper itself hangs
 * both hands at once off a bar; BigBanger's scale only measures one point of
 * load, so each grip is trained one hand at a time instead -- left then
 * right -- which the paper doesn't call for but is this app's own necessary
 * adaptation to a single-load-cell device.
 */
object Programs {
    private const val DEFAULT_TOLERANCE = 0.05f
    const val NO_HANGS_PREP_SECONDS = 10
    const val NO_HANGS_HOLD_TYPE = "20mm Edge"
    private const val NO_HANGS_DEFAULT_REPS = 2

    /**
     * No Hangs' default grip order and per-grip rep count, from the Abrahangs protocol --
     * used both to seed its grip sequence the first time (see
     * [TrainingRepo.ensureNoHangsDefaultSeeded]) and, in [buildNoHangs], to size each
     * grip's reps when running it. A grip the user adds later that isn't one of these six
     * falls back to [NO_HANGS_DEFAULT_REPS].
     */
    val NO_HANGS_DEFAULT_GRIPS = listOf(
        "4-Finger Half Crimp" to 6,
        "Front-3 Open" to 6,
        "Front-2 Open" to 2,
        "Middle-2 Open" to 2,
        "Front-2 Half Crimp" to 2,
        "Middle-2 Half Crimp" to 2,
    )

    private fun noHangsRepsFor(gripName: String): Int =
        NO_HANGS_DEFAULT_GRIPS.firstOrNull { it.first == gripName }?.second ?: NO_HANGS_DEFAULT_REPS

    val MAX_HANGS = ProgramDefinition(
        id = ProgramId.MAX_HANGS,
        title = "Max Hangs",
        hasNoTarget = false,
        steps = buildList {
            repeat(6) { i ->
                add(
                    ProgramStep(
                        kind = StepKind.WORK,
                        durationSeconds = 10,
                        percentOfMax = 0.85f,
                        toleranceBandPoints = DEFAULT_TOLERANCE,
                        label = "Rep ${i + 1}/6",
                    ),
                )
                if (i < 5) add(ProgramStep(StepKind.REST, 120))
            }
        },
    )

    val MAX_TESTING = ProgramDefinition(
        id = ProgramId.MAX_TESTING,
        title = "Max Testing",
        hasNoTarget = true,
        steps = buildList {
            repeat(8) { i ->
                add(ProgramStep(kind = StepKind.WORK, durationSeconds = 7, label = "Attempt ${i + 1}"))
                if (i < 7) add(ProgramStep(StepKind.REST, 120))
            }
        },
    )

    val WARM_UP = ProgramDefinition(
        id = ProgramId.WARM_UP,
        title = "Warm Up",
        hasNoTarget = false,
        steps = buildList {
            val percents = listOf(0.50f, 0.60f, 0.70f, 0.80f, 0.90f)
            percents.forEachIndexed { i, pct ->
                add(
                    ProgramStep(
                        kind = StepKind.WORK,
                        durationSeconds = 8,
                        percentOfMax = pct,
                        toleranceBandPoints = DEFAULT_TOLERANCE,
                        label = "Step ${i + 1}/${percents.size}",
                    ),
                )
                if (i < percents.lastIndex) add(ProgramStep(StepKind.REST, 60))
            }
        },
    )

    /** Display-only stub for the Home list and settings screens -- the steps actually run
     * come from [buildNoHangs], driven by whatever grip sequence is configured for it. */
    val NO_HANGS = ProgramDefinition(
        id = ProgramId.NO_HANGS,
        title = "No Hangs",
        hasNoTarget = false,
        steps = listOf(ProgramStep(kind = StepKind.WORK, durationSeconds = 10, percentOfMax = 0.40f)),
    )

    val all = listOf(WARM_UP, NO_HANGS, MAX_HANGS, MAX_TESTING)

    /**
     * No Hangs' own expansion: unlike [expandWithGripSequence]'s uniform per-leg template,
     * each grip gets its own rep count (see [NO_HANGS_DEFAULT_GRIPS]) instead of the same
     * template repeated for every grip. Each grip is still trained one hand at a time
     * (left, then right) with one 10s PREP per leg, same shape as [expandWithGripSequence].
     */
    fun buildNoHangs(legs: List<Pair<HoldType, GripVariant>>): ProgramDefinition {
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
                val reps = noHangsRepsFor(variant.name)
                repeat(reps) { rep ->
                    add(
                        ProgramStep(
                            kind = StepKind.WORK,
                            durationSeconds = 10,
                            percentOfMax = 0.40f,
                            toleranceBandPoints = DEFAULT_TOLERANCE,
                            holdTypeName = holdType.name,
                            gripLabel = variant.name,
                            hand = hand,
                            label = "${rep + 1}/$reps",
                        ),
                    )
                    val isLastRepOfLastLeg = legIndex == handLegs.lastIndex && rep == reps - 1
                    if (!isLastRepOfLastLeg) add(ProgramStep(StepKind.REST, 20))
                }
            }
        }
        return ProgramDefinition(id = ProgramId.NO_HANGS, title = "No Hangs", hasNoTarget = false, steps = steps)
    }
}

private const val GRIP_SEQUENCE_PREP_SECONDS = 10

/**
 * Expands [base]'s own step template into a run through [legs] in the given order,
 * each grip trained one hand at a time (left then right) -- the same shape
 * [Programs.buildNoHangs] uses, just with [base]'s template repeated identically for
 * every leg instead of a per-grip rep count. [legs] must be non-empty; callers check
 * for an unconfigured (empty) grip sequence before resolving (see
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
