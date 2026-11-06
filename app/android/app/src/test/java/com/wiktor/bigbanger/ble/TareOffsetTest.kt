package com.wiktor.bigbanger.ble

import org.junit.Assert.assertEquals
import org.junit.Test

private const val EPSILON = 0.0001f

class TareOffsetTest {
    @Test
    fun apply_beforeAnyTare_passesRawValueThrough() {
        val offset = TareOffset()
        assertEquals(5.2f, offset.apply(5.2f), EPSILON)
        assertEquals(-1.3f, offset.apply(-1.3f), EPSILON)
    }

    @Test
    fun captureFromLastRaw_zeroesTheNextIdenticalReading() {
        val offset = TareOffset()
        offset.apply(3.4f)
        offset.captureFromLastRaw()
        assertEquals(0f, offset.apply(3.4f), EPSILON)
    }

    @Test
    fun captureFromLastRaw_shiftsLaterReadingsByTheSameAmount() {
        val offset = TareOffset()
        offset.apply(3.4f) // residual load/drift on the scale at tare time
        offset.captureFromLastRaw()
        // 1.0kg of real load added after taring should still read as 1.0kg, not 4.4kg.
        assertEquals(1.0f, offset.apply(4.4f), EPSILON)
    }

    @Test
    fun repeatedTare_rezeroesFromWhicheverRawReadingWasLatest() {
        val offset = TareOffset()
        offset.apply(5f)
        offset.captureFromLastRaw()
        assertEquals(0f, offset.apply(5f), EPSILON)

        offset.apply(8f) // drifted/loaded since the first tare
        offset.captureFromLastRaw()
        assertEquals(0f, offset.apply(8f), EPSILON)
        assertEquals(1f, offset.apply(9f), EPSILON)
    }

    @Test
    fun reset_clearsTheOffsetBackToPassthrough() {
        val offset = TareOffset()
        offset.apply(2f)
        offset.captureFromLastRaw()
        assertEquals(0f, offset.apply(2f), EPSILON)

        offset.reset()
        assertEquals(2f, offset.apply(2f), EPSILON)
    }
}
