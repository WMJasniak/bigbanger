package com.wiktor.bigbanger.training

/**
 * The 4 built-in programs. Max Hangs (85% of max, 10s x 6, 2 min rest) and
 * Max Testing (7s holds, progressive load, 2 min rest, up to 8 attempts)
 * follow Lattice Training's published testing/training instructions.
 * Warm Up's ramp has no single authoritative source. No Hangs' structure
 * (10 sets over ~10 min) follows Lattice's published Abrahangs protocol;
 * its ~70%-of-max intensity specifically is still an approximation.
 */
object Programs {
    private const val DEFAULT_TOLERANCE = 0.05f

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
        steps = buildList {
            repeat(10) { i ->
                add(
                    ProgramStep(
                        kind = StepKind.WORK,
                        durationSeconds = 10,
                        percentOfMax = 0.70f,
                        toleranceBandPoints = DEFAULT_TOLERANCE,
                        label = "Set ${i + 1}/10",
                    ),
                )
                if (i < 9) add(ProgramStep(StepKind.REST, 55))
            }
        },
    )

    val all = listOf(WARM_UP, NO_HANGS, MAX_HANGS, MAX_TESTING)
}
