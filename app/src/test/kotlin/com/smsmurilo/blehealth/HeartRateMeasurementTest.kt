package com.smsmurilo.blehealth

import com.smsmurilo.blehealth.ble.HeartRateMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HeartRateMeasurementTest {

    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }

    @Test
    fun `parses uint8 bpm without optional fields`() {
        val m = HeartRateMeasurement.parse(bytes(0x00, 72))
        assertEquals(72, m.bpm)
        assertNull(m.sensorContact)
        assertEquals(emptyList<Int>(), m.rrIntervalsMs)
    }

    @Test
    fun `parses uint16 bpm, sensor contact, energy and rr intervals`() {
        // flags: 16-bit bpm | contact supported+detected | energy | RR
        val flags = 0x01 or 0x02 or 0x04 or 0x08 or 0x10
        val m = HeartRateMeasurement.parse(bytes(flags, 0x2C, 0x01, 0x10, 0x00, 0x00, 0x04, 0x00, 0x02))
        assertEquals(300, m.bpm)
        assertEquals(true, m.sensorContact)
        assertEquals(16, m.energyExpendedKj)
        assertEquals(listOf(1000, 500), m.rrIntervalsMs)
    }

    @Test
    fun `reports lost skin contact`() {
        assertEquals(false, HeartRateMeasurement.parse(bytes(0x04, 60)).sensorContact)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects truncated payload`() {
        HeartRateMeasurement.parse(bytes(0x00))
    }
}
