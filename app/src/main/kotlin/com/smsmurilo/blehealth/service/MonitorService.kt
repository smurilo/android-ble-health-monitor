package com.smsmurilo.blehealth.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.smsmurilo.blehealth.ServiceLocator
import com.smsmurilo.blehealth.domain.VitalsAlert
import com.smsmurilo.blehealth.domain.VitalsAlertPolicy
import com.smsmurilo.blehealth.sensors.FallDetector
import com.smsmurilo.blehealth.sensors.accelerometer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * Foreground service: keeps monitoring with the screen off and survives the app going to
 * background, as required for continuous patient monitoring.
 */
class MonitorService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val policy = VitalsAlertPolicy()
    private val fallDetector = FallDetector()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(ONGOING, "Monitoramento", NotificationManager.IMPORTANCE_LOW))
        nm.createNotificationChannel(NotificationChannel(ALERTS, "Alertas de saúde", NotificationManager.IMPORTANCE_HIGH))

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        ServiceCompat.startForeground(this, 1, ongoing("Aguardando leituras…"), type)

        val ble = ServiceLocator.ble(this)
        ble.measurements.onEach { m ->
            update(ongoing("${m.bpm} bpm"))
            policy.onHeartRate(m.bpm, m.sensorContact, System.currentTimeMillis())?.let(::alert)
        }.launchIn(scope)

        accelerometer().onEach { s ->
            if (fallDetector.onSample(s.x, s.y, s.z, s.timestampMs)) alert(VitalsAlert.FallDetected)
        }.launchIn(scope)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun ongoing(text: String): Notification = NotificationCompat.Builder(this, ONGOING)
        .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
        .setContentTitle("Monitor cardíaco ativo")
        .setContentText(text)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()

    private fun update(n: Notification) = getSystemService(NotificationManager::class.java).notify(1, n)

    private fun alert(alert: VitalsAlert) {
        val text = when (alert) {
            is VitalsAlert.Tachycardia -> "Frequência elevada: ${alert.bpm} bpm"
            is VitalsAlert.Bradycardia -> "Frequência baixa: ${alert.bpm} bpm"
            VitalsAlert.SensorContactLost -> "Sensor sem contato com a pele"
            VitalsAlert.FallDetected -> "Possível queda detectada!"
        }
        getSystemService(NotificationManager::class.java).notify(
            alert.hashCode(),
            NotificationCompat.Builder(this, ALERTS)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle("Alerta de saúde")
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .build(),
        )
    }

    companion object {
        private const val ONGOING = "ongoing"
        private const val ALERTS = "alerts"

        fun start(context: Context) = context.startForegroundService(Intent(context, MonitorService::class.java))
        fun stop(context: Context) = context.stopService(Intent(context, MonitorService::class.java))
    }
}
