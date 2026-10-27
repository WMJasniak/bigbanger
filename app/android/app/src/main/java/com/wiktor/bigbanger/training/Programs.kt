package com.wiktor.bigbanger.training

/**
 * The 4 built-in programs. Max Hangs and Max Testing follow Lattice
 * Training's published testing/training instructions. Warm Up and No Hangs
 * have no single authoritative source (see each program's [ProgramDefinition.description])
 * -- they're reasonable, easy-to-tweak defaults, not verified protocols.
 */
object Programs {
    private const val DEFAULT_TOLERANCE = 0.05f

    val MAX_HANGS = ProgramDefinition(
        id = ProgramId.MAX_HANGS,
        title = "Max Hangs",
        description = "Lattice Training: 6 x 10s holds at 85% of max, 2 min rest between.",
        isApproximate = false,
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
        description = "Find your current max: up to 8 attempts, 7s holds, progressively heavier, 2 min rest.",
        isApproximate = false,
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
        description = "Approximate ramp: 50/60/70/80/90% of max, 8s holds, 60s rest. Adjust by feel.",
        isApproximate = true,
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
        description = "Lattice's published Abrahangs protocol: 10 sets over ~10 min, feet never leave the " +
            "ground, low intensity (~70% of max -- that figure specifically is still approximate).",
        isApproximate = true,
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
