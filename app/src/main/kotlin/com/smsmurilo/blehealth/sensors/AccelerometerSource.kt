package com.smsmurilo.blehealth.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

data class AccelSample(val x: Float, val y: Float, val z: Float, val timestampMs: Long)

fun Context.accelerometer(): Flow<AccelSample> = callbackFlow {
    val manager = getSystemService(SensorManager::class.java)
    val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: run { close(); return@callbackFlow }
    val listener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            trySend(AccelSample(e.values[0], e.values[1], e.values[2], e.timestamp / 1_000_000))
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }
    // ~50 Hz is enough for fall detection and far cheaper than SENSOR_DELAY_FASTEST.
    manager.registerListener(listener, sensor, 20_000)
    awaitClose { manager.unregisterListener(listener) }
}
