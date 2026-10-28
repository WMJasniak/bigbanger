package com.wiktor.bigbanger.training

/** A hold you'd physically buy/own, e.g. "20mm Edge" -- the thing Settings lets you add. */
data class HoldType(val id: String, val name: String)

/** A finger position on a [HoldType], e.g. "4-Finger Half Crimp". Every variant carries
 * two maxes by default (one per [Hand]), not two separate variants -- Settings only
 * ever shows one row per variant, never a left/right pair of rows. */
data class GripVariant(val id: String, val holdTypeId: String, val name: String)

data class MaxTestResult(
    val id: String,
    val gripVariantId: String,
    val hand: Hand,
    val peakKg: Float,
    val timestampMs: Long,
)

data class SessionLogEntry(
    val id: String,
    val programId: ProgramId,
    val timestampMs: Long,
)
