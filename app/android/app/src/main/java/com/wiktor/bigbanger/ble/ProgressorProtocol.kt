package com.wiktor.bigbanger.ble

import java.util.UUID

/**
 * Wire format for the Tindeq Progressor BLE API, which BigBanger's firmware
 * (both the Rust and MicroPython implementations) emulates.
 *
 * Devices are identified by advertised name, not by advertised service UUID:
 * the Rust firmware's advertising packet only carries a generic 16-bit
 * Battery Service UUID filler plus the local name, and the real 128-bit
 * Progressor service below is only visible after connecting and discovering
 * services. This matches the project README, which requires the advertised
 * name to start with "Progressor".
 */
object ProgressorProtocol {
    val SERVICE_UUID: UUID = UUID.fromString("7e4e1701-1ea6-40c9-9dcc-13d34ffead57")
    val DATA_CHARACTERISTIC_UUID: UUID = UUID.fromString("7e4e1702-1ea6-40c9-9dcc-13d34ffead57")
    val CONTROL_CHARACTERISTIC_UUID: UUID = UUID.fromString("7e4e1703-1ea6-40c9-9dcc-13d34ffead57")
    val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    const val DEVICE_NAME_PREFIX = "Progressor"

    private const val WEIGHT_RESPONSE_OPCODE = 0x01

    object ControlOpcode {
        const val TARE: Byte = 0x64
        const val START_MEASUREMENT: Byte = 0x65
        const val STOP_MEASUREMENT: Byte = 0x66
    }

    /** Decodes a data_point notification, or null if it isn't a weight sample. */
    fun decodeWeight(data: ByteArray): WeightSample? {
        // Layout: [opcode][length][weight f32 LE][timestamp u32 LE]
        if (data.size < 10) return null
        val opcode = data[0].toInt() and 0xFF
        if (opcode != WEIGHT_RESPONSE_OPCODE) return null
        val kilograms = Float.fromBits(leUInt32(data, 2).toInt())
        val timestampMicros = leUInt32(data, 6)
        return WeightSample(timestampSeconds = timestampMicros / 1_000_000f, kilograms = kilograms)
    }

    private fun leUInt32(bytes: ByteArray, offset: Int): Long {
        var result = 0L
        for (i in 0 until 4) {
            result = result or ((bytes[offset + i].toLong() and 0xFF) shl (8 * i))
        }
        return result
    }
}
