package com.smsmurilo.blehealth.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.smsmurilo.blehealth.ServiceLocator
import com.smsmurilo.blehealth.ble.BleState
import com.smsmurilo.blehealth.ble.FoundDevice
import com.smsmurilo.blehealth.service.MonitorService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

data class MonitorUiState(
    val ble: BleState = BleState.Idle,
    val devices: List<FoundDevice> = emptyList(),
    val scanning: Boolean = false,
    val bpm: Int? = null,
    val history: List<Int> = emptyList(),
    val hrvMs: Int? = null,
)

class MonitorViewModel(app: Application) : AndroidViewModel(app) {

    private val client = ServiceLocator.ble(app)
    private val devices = MutableStateFlow<List<FoundDevice>>(emptyList())
    private val scanning = MutableStateFlow(false)
    private var scanJob: Job? = null

    private val readings = client.measurements.runningFold(emptyList<com.smsmurilo.blehealth.ble.HeartRateMeasurement>()) { acc, m ->
        (acc + m).takeLast(60)
    }

    val state: StateFlow<MonitorUiState> = combine(client.state, devices, scanning, readings) { ble, found, isScanning, last ->
        val rr = last.flatMap { it.rrIntervalsMs }.takeLast(30)
        MonitorUiState(
            ble = ble,
            devices = found,
            scanning = isScanning,
            bpm = last.lastOrNull()?.bpm,
            history = last.map { it.bpm },
            hrvMs = rmssd(rr),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonitorUiState())

    fun scan() {
        scanJob?.cancel()
        devices.value = emptyList()
        scanJob = viewModelScope.launch {
            scanning.value = true
            withTimeoutOrNull(10_000) {
                client.scan()
                    .catch { scanning.value = false }
                    .onEach { d -> devices.value = (devices.value.filterNot { it.address == d.address } + d).sortedByDescending { it.rssi } }
                    .collect {}
            }
            scanning.value = false
        }
    }

    fun connect(device: FoundDevice) {
        scanJob?.cancel()
        scanning.value = false
        client.connect(device.address)
        MonitorService.start(getApplication())
    }

    fun disconnect() {
        client.disconnect()
        MonitorService.stop(getApplication())
    }

    companion object {
        /** RMSSD: standard short-term HRV metric from successive RR differences. */
        fun rmssd(rr: List<Int>): Int? {
            if (rr.size < 3) return null
            val diffs = rr.zipWithNext { a, b -> (b - a).toDouble() }
            return Math.round(kotlin.math.sqrt(diffs.sumOf { it * it } / diffs.size)).toInt()
        }
    }
}
