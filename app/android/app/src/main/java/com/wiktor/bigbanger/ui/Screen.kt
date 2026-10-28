package com.wiktor.bigbanger.ui

import com.wiktor.bigbanger.training.ProgramDefinition

/** Simple sealed navigation state (house style, extended with payloads this app's flow needs). */
sealed interface Screen {
    data object Home : Screen

    /** null program = Free Measure (the original ad-hoc scan/connect flow). */
    data class Connect(val pendingProgram: ProgramDefinition?) : Screen

    /** The program's own settings: which grips it always uses, and in what order. */
    data class ProgramGripConfig(val program: ProgramDefinition) : Screen

    /** [program]'s steps are already fully resolved against its configured grip sequence. */
    data class Running(val program: ProgramDefinition) : Screen
    data object Stats : Screen
    data object Settings : Screen
    data object HoldsSettings : Screen
}
