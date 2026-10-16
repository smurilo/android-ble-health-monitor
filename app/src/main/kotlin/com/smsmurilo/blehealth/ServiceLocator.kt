package com.smsmurilo.blehealth

import android.content.Context
import com.smsmurilo.blehealth.ble.BleHeartRateClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Minimal manual DI: one BLE client shared by the UI and the foreground service. */
object ServiceLocator {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    @Volatile private var client: BleHeartRateClient? = null

    fun ble(context: Context): BleHeartRateClient =
        client ?: synchronized(this) {
            client ?: BleHeartRateClient(context.applicationContext, appScope).also { client = it }
        }
}
