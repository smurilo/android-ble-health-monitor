package com.smsmurilo.blehealth.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.smsmurilo.blehealth.ble.BleState

class MainActivity : ComponentActivity() {

    private val viewModel: MonitorViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { MonitorScreen(viewModel) } }
    }
}

private val blePermissions = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        add(Manifest.permission.ACCESS_FINE_LOCATION) // required for BLE scan before Android 12
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
}.toTypedArray()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MonitorScreen(viewModel: MonitorViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) viewModel.scan()
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Monitor Cardíaco BLE") }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                when (val s = state.ble) {
                    BleState.Idle -> "Nenhum sensor conectado"
                    is BleState.Connecting -> "Conectando a ${s.name}…"
                    is BleState.Connected -> "Conectado: ${s.name}" + (s.battery?.let { " • bateria $it%" } ?: "")
                    is BleState.Reconnecting -> "Sinal perdido, reconectando (tentativa ${s.attempt})…"
                    is BleState.Failed -> s.reason
                },
            )
            if (state.ble is BleState.Connected || state.ble is BleState.Reconnecting) {
                VitalsCard(state)
                OutlinedButton(onClick = viewModel::disconnect, modifier = Modifier.fillMaxWidth()) { Text("Desconectar") }
            } else {
                Button(onClick = { permissions.launch(blePermissions) }, enabled = !state.scanning, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.scanning) "Procurando sensores…" else "Procurar sensores")
                }
                if (state.scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
                LazyColumn {
                    items(state.devices, key = { it.address }) { d ->
                        ListItem(
                            headlineContent = { Text(d.name) },
                            supportingContent = { Text("${d.address} • sinal ${d.rssi} dBm") },
                            modifier = Modifier.clickable { viewModel.connect(d) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VitalsCard(state: MonitorUiState) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Favorite, null, tint = Color(0xFFD32F2F))
                Text(
                    text = state.bpm?.toString() ?: "--",
                    fontSize = 56.sp,
                    modifier = Modifier.padding(start = 12.dp).semantics { contentDescription = "${state.bpm ?: 0} batimentos por minuto" },
                )
                Text(" bpm", style = MaterialTheme.typography.titleMedium)
            }
            state.hrvMs?.let { Text("Variabilidade (RMSSD): $it ms", style = MaterialTheme.typography.bodySmall) }
            Sparkline(state.history, Modifier.fillMaxWidth().height(80.dp).padding(top = 8.dp))
        }
    }
}

@Composable
private fun Sparkline(values: List<Int>, modifier: Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val min = values.min() - 5f
        val max = values.max() + 5f
        val stepX = size.width / (values.size - 1)
        values.zipWithNext().forEachIndexed { i, (a, b) ->
            drawLine(
                color = color,
                start = Offset(i * stepX, size.height * (1 - (a - min) / (max - min))),
                end = Offset((i + 1) * stepX, size.height * (1 - (b - min) / (max - min))),
                strokeWidth = 4f,
            )
        }
    }
}
