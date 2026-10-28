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
 * strain," so 40% is the closest documented figure.
 */
object Programs {
    private const val DEFAULT_TOLERANCE = 0.05f

    /** The 6 fixed grip positions No Hangs cycles through, named for a 20mm edge. */
    private object NoHangsGrip {
        const val FOUR_FINGER_HALF_CRIMP = "20mm 4-Finger Half Crimp"
        const val FRONT_3_OPEN = "20mm Front-3 Open"
        const val FRONT_2_OPEN = "20mm Front-2 Open"
        const val MIDDLE_2_OPEN = "20mm Middle-2 Open"
        const val FRONT_2_HALF_CRIMP = "20mm Front-2 Half Crimp"
        const val MIDDLE_2_HALF_CRIMP = "20mm Middle-2 Half Crimp"
    }

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
            val legs = listOf(
                NoHangsGrip.FOUR_FINGER_HALF_CRIMP to 6,
                NoHangsGrip.FRONT_3_OPEN to 6,
                NoHangsGrip.FRONT_2_OPEN to 2,
                NoHangsGrip.MIDDLE_2_OPEN to 2,
                NoHangsGrip.FRONT_2_HALF_CRIMP to 2,
                NoHangsGrip.MIDDLE_2_HALF_CRIMP to 2,
            )
            legs.forEachIndexed { legIndex, (gripName, reps) ->
                repeat(reps) { rep ->
                    add(
                        ProgramStep(
                            kind = StepKind.WORK,
                            durationSeconds = 10,
                            percentOfMax = 0.40f,
                            toleranceBandPoints = DEFAULT_TOLERANCE,
                            gripTypeName = gripName,
                            label = "${gripName.removePrefix("20mm ")} · ${rep + 1}/$reps",
                        ),
                    )
                    val isLastRep = legIndex == legs.lastIndex && rep == reps - 1
                    if (!isLastRep) add(ProgramStep(StepKind.REST, 20))
                }
            }
        },
    )

    val all = listOf(WARM_UP, NO_HANGS, MAX_HANGS, MAX_TESTING)
}
