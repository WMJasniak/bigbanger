package com.wiktor.bigbanger.ui

import com.wiktor.bigbanger.training.GripType
import com.wiktor.bigbanger.training.ProgramDefinition

/** Simple sealed navigation state (house style, extended with payloads this app's flow needs). */
sealed interface Screen {
    data object Home : Screen

    /** null program = Free Measure (the original ad-hoc scan/connect flow). */
    data class Connect(val pendingProgram: ProgramDefinition?) : Screen
    data class GripTypeSelect(val program: ProgramDefinition) : Screen

    /** [gripType] is null for a program with a fixed grip sequence (e.g. No Hangs). */
    data class Running(val program: ProgramDefinition, val gripType: GripType?) : Screen
    data object Stats : Screen
    data object Settings : Screen
}
