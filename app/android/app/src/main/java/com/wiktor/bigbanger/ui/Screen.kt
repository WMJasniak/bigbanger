package com.wiktor.bigbanger.ui

import com.wiktor.bigbanger.training.GripVariant
import com.wiktor.bigbanger.training.Hand
import com.wiktor.bigbanger.training.HoldType
import com.wiktor.bigbanger.training.ProgramDefinition

/** Simple sealed navigation state (house style, extended with payloads this app's flow needs). */
sealed interface Screen {
    data object Home : Screen

    /** null program = Free Measure (the original ad-hoc scan/connect flow). */
    data class Connect(val pendingProgram: ProgramDefinition?) : Screen
    data class HoldTypeSelect(val program: ProgramDefinition) : Screen
    data class GripVariantSelect(val program: ProgramDefinition, val holdType: HoldType) : Screen

    /** [gripVariant]/[hand] are null for a program with a fixed grip sequence (e.g. No Hangs). */
    data class Running(val program: ProgramDefinition, val gripVariant: GripVariant?, val hand: Hand?) : Screen
    data object Stats : Screen
    data object Settings : Screen
}
