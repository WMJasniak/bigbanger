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
    private const val NO_HANGS_PREP_SECONDS = 10

    /** The 6 fixed grip positions No Hangs cycles through, one 20mm-edge rep count each. */
    private val NO_HANGS_GRIPS = listOf(
        "4-Finger Half Crimp" to 6,
        "Front-3 Open" to 6,
        "Front-2 Open" to 2,
        "Middle-2 Open" to 2,
        "Front-2 Half Crimp" to 2,
        "Middle-2 Half Crimp" to 2,
    )

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

    val NO_HANGS = ProgramDefinition(
        id = ProgramId.NO_HANGS,
        title = "No Hangs",
        hasNoTarget = false,
        usesFixedGripSequence = true,
        steps = buildList {
            // Each grip is a leg per hand (left, then right) before moving to the next grip.
            val legs = NO_HANGS_GRIPS.flatMap { (gripLabel, reps) ->
                listOf(Hand.LEFT, Hand.RIGHT).map { hand -> Triple(gripLabel, hand, reps) }
            }
            legs.forEachIndexed { legIndex, (gripLabel, hand, reps) ->
                // One 10s PREP per leg (not per rep) -- the grip/hand doesn't change rep to
                // rep within a leg, so only a leg switch needs a "get into position" beat.
                add(
                    ProgramStep(
                        kind = StepKind.PREP,
                        durationSeconds = NO_HANGS_PREP_SECONDS,
                        gripLabel = gripLabel,
                        hand = hand,
                    ),
                )
                repeat(reps) { rep ->
                    add(
                        ProgramStep(
                            kind = StepKind.WORK,
                            durationSeconds = 10,
                            percentOfMax = 0.40f,
                            toleranceBandPoints = DEFAULT_TOLERANCE,
                            gripTypeName = "20mm $gripLabel (${hand.displayLabel})",
                            gripLabel = gripLabel,
                            hand = hand,
                            label = "${rep + 1}/$reps",
                        ),
                    )
                    val isLastRepOfLastLeg = legIndex == legs.lastIndex && rep == reps - 1
                    if (!isLastRepOfLastLeg) add(ProgramStep(StepKind.REST, 20))
                }
            }
        },
    )

    val all = listOf(WARM_UP, NO_HANGS, MAX_HANGS, MAX_TESTING)
}
