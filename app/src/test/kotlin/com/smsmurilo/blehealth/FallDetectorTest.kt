package com.smsmurilo.blehealth

import com.smsmurilo.blehealth.sensors.FallDetector
import org.junit.Assert.assertEquals
import org.junit.Test

class FallDetectorTest {

    private val g = FallDetector.GRAVITY

    /** Replays a trace of (g-force, duration ms) segments at 50 Hz; returns how many falls were detected. */
    private fun replay(vararg segments: Pair<Float, Long>): Int {
        val detector = FallDetector()
        var t = 0L
        var falls = 0
        for ((force, duration) in segments) {
            val end = t + duration
            while (t < end) {
                if (detector.onSample(0f, 0f, force * g, t)) falls++
                t += 20
            }
        }
        return falls
    }

    @Test
    fun `detects free fall, impact and stillness`() {
        assertEquals(1, replay(1f to 1000, 0.2f to 300, 3.5f to 60, 1f to 2500))
    }

    @Test
    fun `ignores jumping (impact followed by movement)`() {
        assertEquals(0, replay(1f to 1000, 0.2f to 300, 3.5f to 60, 1.8f to 500, 0.6f to 400, 1f to 1000))
    }

    @Test
    fun `ignores phone dropped on a bed (free fall without strong impact)`() {
        assertEquals(0, replay(1f to 500, 0.1f to 400, 1.6f to 100, 1f to 3000))
    }

    @Test
    fun `walking never triggers`() {
        val steps = (1..50).flatMap { listOf(1.3f to 200L, 0.8f to 200L) }.toTypedArray()
        assertEquals(0, replay(*steps))
    }
}
