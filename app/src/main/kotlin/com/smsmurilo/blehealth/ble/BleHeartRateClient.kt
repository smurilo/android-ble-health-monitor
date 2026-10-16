package com.smsmurilo.blehealth.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

sealed interface BleState {
    data object Idle : BleState
    data class Connecting(val name: String) : BleState
    data class Connected(val name: String, val battery: Int?) : BleState
    data class Reconnecting(val name: String, val attempt: Int) : BleState
    data class Failed(val reason: String) : BleState
}

data class FoundDevice(val address: String, val name: String, val rssi: Int)

/**
 * Coroutine wrapper over the callback-based GATT API. Permissions are checked by the UI
 * before any call, hence the MissingPermission suppression.
 */
@SuppressLint("MissingPermission")
class BleHeartRateClient(private val context: Context, private val scope: CoroutineScope) {

    private val adapter = context.getSystemService(BluetoothManager::class.java).adapter
    private var gatt: BluetoothGatt? = null
    private var device: BluetoothDevice? = null
    private var reconnectJob: Job? = null
    private var userDisconnected = false

    private val _state = MutableStateFlow<BleState>(BleState.Idle)
    val state: StateFlow<BleState> = _state

    private val _measurements = MutableSharedFlow<HeartRateMeasurement>(extraBufferCapacity = 64)
    val measurements: SharedFlow<HeartRateMeasurement> = _measurements

    /** Scans only for devices advertising the Heart Rate service, saving battery and avoiding noise. */
    fun scan(): Flow<FoundDevice> = callbackFlow {
        val scanner = adapter?.bluetoothLeScanner ?: run { close(IllegalStateException("Bluetooth desligado")); return@callbackFlow }
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(FoundDevice(result.device.address, result.device.name ?: "Sensor cardíaco", result.rssi))
            }
            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("Falha no scan BLE: $errorCode"))
            }
        }
        scanner.startScan(
            listOf(ScanFilter.Builder().setServiceUuid(ParcelUuid(HeartRateMeasurement.SERVICE)).build()),
            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(),
            callback,
        )
        awaitClose { scanner.stopScan(callback) }
    }

    fun connect(address: String) {
        userDisconnected = false
        device = adapter.getRemoteDevice(address)
        _state.value = BleState.Connecting(device?.name ?: address)
        openGatt()
    }

    fun disconnect() {
        userDisconnected = true
        reconnectJob?.cancel()
        gatt?.disconnect()
        gatt?.close()
        gatt = null
        _state.value = BleState.Idle
    }

    private fun openGatt() {
        gatt?.close()
        gatt = device?.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    /** Sensors drop out (out of range, strap removed): reconnect with backoff 2s, 4s, 8s… up to 30s. */
    private fun scheduleReconnect() {
        if (userDisconnected) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            var attempt = 1
            while (_state.value !is BleState.Connected && !userDisconnected) {
                _state.value = BleState.Reconnecting(device?.name ?: "", attempt)
                openGatt()
                delay(minOf(30_000L, 2_000L shl (attempt - 1)))
                attempt++
            }
        }
    }

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> g.discoverServices()
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (userDisconnected) return
                    _state.value = BleState.Reconnecting(g.device.name ?: "", 0)
                    scheduleReconnect()
                }
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val hr = g.getService(HeartRateMeasurement.SERVICE)?.getCharacteristic(HeartRateMeasurement.MEASUREMENT)
            if (status != BluetoothGatt.GATT_SUCCESS || hr == null) {
                _state.value = BleState.Failed("Dispositivo não expõe o serviço de frequência cardíaca")
                return
            }
            g.setCharacteristicNotification(hr, true)
            val cccd = hr.getDescriptor(HeartRateMeasurement.CCCD)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
        }

        override fun onDescriptorWrite(g: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            // GATT allows one pending operation at a time, so the battery read waits for the CCCD write.
            _state.value = BleState.Connected(g.device.name ?: "Sensor", null)
            g.getService(HeartRateMeasurement.BATTERY_SERVICE)?.getCharacteristic(HeartRateMeasurement.BATTERY_LEVEL)
                ?.let { g.readCharacteristic(it) }
        }

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
            if (c.uuid == HeartRateMeasurement.BATTERY_LEVEL && value.isNotEmpty()) {
                _state.value = BleState.Connected(g.device.name ?: "Sensor", value[0].toInt() and 0xFF)
            }
        }

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            if (c.uuid == HeartRateMeasurement.MEASUREMENT) {
                runCatching { HeartRateMeasurement.parse(value) }.onSuccess { _measurements.tryEmit(it) }
            }
        }

        // Pre-Android 13 devices still deliver notifications through the deprecated overload.
        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) onCharacteristicChanged(g, c, c.value ?: return)
        }
    }
}
