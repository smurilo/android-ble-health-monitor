# Monitor Cardíaco BLE — Android (Kotlin)

App de **monitoramento contínuo de pacientes**: conecta-se via **Bluetooth Low Energy** a qualquer cinta peitoral ou relógio que siga o padrão *Heart Rate Service* do Bluetooth SIG, calcula a **variabilidade cardíaca (HRV)**, detecta **quedas pelo acelerômetro** e emite alertas por um **foreground service**, mesmo com a tela desligada.

> Segmento: **Saúde / Hospitalar**

## Stack
Kotlin 2.0 • **Bluetooth Low Energy (GATT)**: scan com filtro por serviço, notificações via CCCD, leitura de bateria, reconexão com backoff • **SensorManager** (acelerômetro a 50 Hz) • **Foreground Service** (`connectedDevice`) • Coroutines (`callbackFlow`, `SharedFlow`, `StateFlow`) • Jetpack Compose (Canvas para o gráfico em tempo real) • DI manual (Service Locator) • JUnit 4

## Componentes

```
 BLE (cinta/relógio) ──GATT notify──► BleHeartRateClient ──► HeartRateMeasurement.parse (bpm, contato, energia, RR)
                                            │                                 │
                                       StateFlow<BleState>              SharedFlow<medições>
                                            │                                 │
 Acelerômetro ──callbackFlow──► FallDetector          MonitorViewModel (UI)  MonitorService (foreground)
                                     │                 bpm, HRV (RMSSD),       VitalsAlertPolicy ──► notificações
                                     └─────────────────────── gráfico ────────────── de alerta
```

### Destaques técnicos
- **Parser do padrão 0x2A37** completo: bpm de 8 ou 16 bits, contato com a pele, energia gasta e intervalos RR (unidade 1/1024 s).
- **API de callbacks do GATT encapsulada em coroutines**; respeita a regra de **uma operação GATT por vez** (a leitura da bateria só ocorre após a escrita do CCCD).
- **Compatível do Android 8 ao 15**: permissões `BLUETOOTH_SCAN/CONNECT` com `neverForLocation` no Android 12+, localização no Android 11 ou anterior, overloads novos e antigos de `onCharacteristicChanged`.
- **Reconexão automática** com backoff exponencial (2 s → 30 s) quando o paciente sai do alcance.
- **Detecção de queda em 3 fases** (queda livre → impacto → imobilidade), testada com sinais simulados de queda, salto, celular jogado na cama e caminhada.
- **Alertas sem falso positivo**: taquicardia ou bradicardia só disparam se o valor anormal **se mantiver por 10 s**, e uma vez por episódio.

## Como executar
```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```
Sem um sensor físico, use o app **nRF Connect** em outro celular para simular um periférico *Heart Rate*.
