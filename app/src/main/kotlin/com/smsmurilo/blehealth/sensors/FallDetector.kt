package com.smsmurilo.blehealth.sensors

import kotlin.math.sqrt

/**
 * Classic three-phase fall heuristic on accelerometer data:
 * free fall (|a| < 0.5 g) → impact (|a| > 2.5 g) within 1 s → lying still for 2 s.
 * Pure Kotlin: fed with samples, testable on the JVM with recorded traces.
 */
class FallDetector(
    private val freeFallG: Float = 0.5f,
    private val impactG: Float = 2.5f,
    private val impactWindowMs: Long = 1_000,
    private val stillnessMs: Long = 2_000,
    private val stillnessToleranceG: Float = 0.25f,
) {
    private enum class Phase { NORMAL, FREE_FALL, IMPACT }

    private var phase = Phase.NORMAL
    private var phaseStartedAt = 0L

    /** @return true exactly once, when a fall is confirmed. */
    fun onSample(xMs2: Float, yMs2: Float, zMs2: Float, timestampMs: Long): Boolean {
        val g = sqrt(xMs2 * xMs2 + yMs2 * yMs2 + zMs2 * zMs2) / GRAVITY
        when (phase) {
            Phase.NORMAL -> if (g < freeFallG) enter(Phase.FREE_FALL, timestampMs)
            Phase.FREE_FALL -> when {
                g > impactG -> enter(Phase.IMPACT, timestampMs)
                timestampMs - phaseStartedAt > impactWindowMs -> enter(Phase.NORMAL, timestampMs)
            }
            Phase.IMPACT -> when {
                kotlin.math.abs(g - 1f) > stillnessToleranceG && timestampMs - phaseStartedAt > 300 -> enter(Phase.NORMAL, timestampMs)
                timestampMs - phaseStartedAt >= stillnessMs -> {
                    enter(Phase.NORMAL, timestampMs)
                    return true
                }
            }
        }
        return false
    }

    private fun enter(next: Phase, at: Long) {
        phase = next
        phaseStartedAt = at
    }

    companion object {
        const val GRAVITY = 9.80665f
    }
}
