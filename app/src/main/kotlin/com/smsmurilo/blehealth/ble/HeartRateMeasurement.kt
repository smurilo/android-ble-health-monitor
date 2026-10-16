package com.smsmurilo.blehealth.ble

import java.util.UUID

/**
 * Parser for the standard Bluetooth SIG Heart Rate Measurement characteristic (0x2A37).
 * Works with any compliant chest strap or smartwatch, regardless of brand.
 */
data class HeartRateMeasurement(
    val bpm: Int,
    val sensorContact: Boolean?,
    val energyExpendedKj: Int?,
    /** RR intervals in milliseconds — used for heart-rate variability (HRV) */
    val rrIntervalsMs: List<Int>,
) {
    companion object {
        val SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        val MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        val BATTERY_SERVICE: UUID = UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb")
        val BATTERY_LEVEL: UUID = UUID.fromString("00002a19-0000-1000-8000-00805f9b34fb")
        /** Client Characteristic Configuration Descriptor: writing 0x0001 enables notifications. */
        val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        fun parse(value: ByteArray): HeartRateMeasurement {
            require(value.size >= 2) { "Payload muito curto: ${value.size} bytes" }
            val flags = value[0].toInt() and 0xFF
            var offset = 1

            val is16Bit = flags and 0x01 != 0
            val bpm = if (is16Bit) uint16(value, offset).also { offset += 2 } else (value[offset].toInt() and 0xFF).also { offset += 1 }

            val contactSupported = flags and 0x04 != 0
            val contact = if (contactSupported) flags and 0x02 != 0 else null

            val energy = if (flags and 0x08 != 0) uint16(value, offset).also { offset += 2 } else null

            val rr = mutableListOf<Int>()
            if (flags and 0x10 != 0) {
                while (offset + 1 < value.size) {
                    // RR is transmitted in 1/1024 s units.
                    rr += Math.round(uint16(value, offset) * 1000f / 1024f)
                    offset += 2
                }
            }
            return HeartRateMeasurement(bpm, contact, energy, rr)
        }

        private fun uint16(b: ByteArray, i: Int) = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
    }
}
