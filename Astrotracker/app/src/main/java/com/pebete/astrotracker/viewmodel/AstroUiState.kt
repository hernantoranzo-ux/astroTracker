package com.pebete.astrotracker.viewmodel

import com.pebete.astrotracker.data.bluetooth.ConnectionState
import com.pebete.astrotracker.data.model.AppMode
import com.pebete.astrotracker.data.model.BluetoothDeviceInfo
import com.pebete.astrotracker.data.model.MotorPosition
import com.pebete.astrotracker.data.model.PhonePosition
import com.pebete.astrotracker.data.model.TelemetryData

/**
 * "Fuente única de verdad" de la pantalla: UN solo objeto inmutable que describe TODO lo que la UI
 * necesita dibujar en un momento dado. La UI nunca modifica esto: solo lo observa.
 * Cuando algo cambia, el ViewModel crea una copia con state.copy(campo = nuevoValor).
 *
 * Los valores por defecto representan el estado "recién abierta la app".
 */
data class AstroUiState(
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val mode: AppMode = AppMode.MANUAL,
    // null = todavía no llegó ningún paquete DATA: desde que conectamos
    val telemetry: TelemetryData? = null,
    // null = el sensor todavía no entregó ninguna lectura
    val phonePosition: PhonePosition? = null,
    // Última respuesta del Arduino al comando 'P' (posición de motores en pasos); null = sin respuesta aún
    val motorPosition: MotorPosition? = null,
    val pairedDevices: List<BluetoothDeviceInfo> = emptyList(),
    // MAC del dispositivo elegido en la lista (null = ninguno elegido todavía)
    val selectedDeviceMac: String? = null,
    val isCalibrating: Boolean = false,
    // false si el teléfono no tiene el sensor ROTATION_VECTOR (la app no puede funcionar en automático)
    val isSensorAvailable: Boolean = true,
    // false si el celular no tiene Bluetooth o está apagado
    val isBluetoothEnabled: Boolean = true,
    // Mensaje de error PERSISTENTE (se muestra mientras exista). Los avisos de una sola vez
    // (ej. "Comando ignorado") NO van acá: viajan por un evento aparte (ver AstroViewModel.messages).
    val errorMessage: String? = null,
    // Pasos del motor por grado. 10.0 es el valor por defecto del firmware (pasosPorGrado = 10.0)
    val stepsPerDegree: Float = 10f,
    // true = el valor viene de una calibración hecha desde la app (se reenvía al conectar)
    val isStepsCalibrated: Boolean = false,
    // Preferencias de usuario
    val autoReconnect: Boolean = true,
    val redNightMode: Boolean = false,
    // 0 = no hay reconexión en curso; 1..N = número de intento
    val reconnectAttempt: Int = 0
)
