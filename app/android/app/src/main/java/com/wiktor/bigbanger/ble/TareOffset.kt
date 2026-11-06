package com.wiktor.bigbanger.ble

/**
 * A client-side tare: remembers the most recent raw reading passed to [apply], and once
 * [captureFromLastRaw] is called, subtracts that value from every reading from then on --
 * mathematically the same thing the scale's own tare does, just computed on the phone.
 *
 * This exists because the firmware's own tare can be silently dropped while a measurement
 * is already streaming (a measurement-loop bug found on the Rust firmware; see
 * firmware/rust/src/measurement/task.rs). Free Measure's Tare button is always pressed in
 * exactly that state (that screen auto-starts on connect and has no stop button), so
 * taring there must not depend on the firmware doing anything at all with the opcode.
 *
 * Deliberately has no Android dependencies, so the offset math itself -- the entire fix --
 * can be unit tested directly (see TareOffsetTest) without a device, emulator, or any
 * Bluetooth mocking.
 */
class TareOffset {
    @Volatile private var offsetKg: Float = 0f

    @Volatile var lastRawKg: Float = 0f
        private set

    /** Feeds in the next raw sample from the scale, returning it with the current offset
     * applied. Must be called for every sample, including before the first tare, so
     * [lastRawKg] is always the true latest raw reading when [captureFromLastRaw] runs. */
    fun apply(rawKg: Float): Float {
        lastRawKg = rawKg
        return rawKg - offsetKg
    }

    /** Zeroes the next reading: the next [apply] call with the same raw value as the last
     * one will return 0, and every later reading is shifted by that same amount. */
    fun captureFromLastRaw() {
        offsetKg = lastRawKg
    }

    /** Back to passing raw readings straight through -- call at the start of a fresh
     * measurement session, since a leftover offset from a previous session (or from
     * before the firmware's own tare already zeroed things, e.g. at a program's Ready
     * gate) would otherwise double-apply. */
    fun reset() {
        offsetKg = 0f
    }
}
