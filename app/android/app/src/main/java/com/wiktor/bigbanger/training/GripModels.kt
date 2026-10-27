package com.wiktor.bigbanger.training

data class GripType(val id: String, val name: String)

data class MaxTestResult(
    val id: String,
    val gripTypeId: String,
    val peakKg: Float,
    val timestampMs: Long,
)

data class SessionLogEntry(
    val id: String,
    val programId: ProgramId,
    val timestampMs: Long,
)
