package com.smsmurilo.blehealth.domain

sealed interface VitalsAlert {
    data class Tachycardia(val bpm: Int) : VitalsAlert
    data class Bradycardia(val bpm: Int) : VitalsAlert
    data object SensorContactLost : VitalsAlert
    data object FallDetected : VitalsAlert
}

/**
 * Raises heart-rate alerts only when the abnormal value is sustained (default 10 s),
 * filtering out single spikes caused by movement artefacts.
 */
class VitalsAlertPolicy(
    private val maxBpm: Int = 120,
    private val minBpm: Int = 45,
    private val sustainMs: Long = 10_000,
) {
    private var abnormalSince: Long? = null
    private var alerted = false

    fun onHeartRate(bpm: Int, contact: Boolean?, now: Long): VitalsAlert? {
        if (contact == false) return VitalsAlert.SensorContactLost
        val abnormal = bpm > maxBpm || bpm < minBpm
        if (!abnormal) {
            abnormalSince = null
            alerted = false
            return null
        }
        val since = abnormalSince ?: now.also { abnormalSince = it }
        if (!alerted && now - since >= sustainMs) {
            alerted = true
            return if (bpm > maxBpm) VitalsAlert.Tachycardia(bpm) else VitalsAlert.Bradycardia(bpm)
        }
        return null
    }
}
