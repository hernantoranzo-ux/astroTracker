package com.pebete.astrotracker.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pebete.astrotracker.data.bluetooth.BluetoothManager
import com.pebete.astrotracker.data.bluetooth.ConnectionState
import com.pebete.astrotracker.data.model.AppMode
import com.pebete.astrotracker.data.sensor.PhoneSensorManager
import com.pebete.astrotracker.data.settings.SettingsStore
import com.pebete.astrotracker.repository.AstroRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * AstroViewModel = el "cerebro" de la pantalla.
 *
 * - SOBREVIVE a cambios de configuración (ej. si Android recrea la Activity): por eso la conexión Bluetooth
 *   vive acá (vía el repositorio) y no en la Activity. (La app además está bloqueada en vertical.)
 * - Expone UN solo [uiState] (StateFlow) que la UI observa, y métodos "onXxx" que la UI llama ante
 *   eventos del usuario. La UI no contiene lógica de negocio.
 *
 * AndroidViewModel (en vez de ViewModel) nos da acceso al "Application": un Context que vive tanto como
 * la app, así NO hay riesgo de filtrar una Activity (memory leak). Se lo pasamos a PhoneSensorManager.
 *
 * viewModelScope: scope de corrutinas atado al ViewModel. Cuando el ViewModel muere (onCleared) todas las
 * corrutinas lanzadas ahí se cancelan solas. Es el equivalente a "no olvidarse de hacer thread.interrupt()".
 */
class AstroViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        // Número de intentos de reconexión automática (debe coincidir con el tamaño de RECONNECT_DELAYS_MS)
        const val MAX_RECONNECT_ATTEMPTS = 3

        // Esperas (ms) antes de cada intento de reconexión automática. 3 intentos: no insistimos para siempre
        // (cada intento de connect() consume batería y bloquea el HC-05).
        private val RECONNECT_DELAYS_MS = longArrayOf(2_000L, 4_000L, 8_000L)
        // Máximo que esperamos el resultado de UN intento de conexión
        private const val CONNECT_ATTEMPT_TIMEOUT_MS = 20_000L
    }

    // Preferencias persistentes (SharedPreferences): última MAC, auto-reconectar, modo rojo, pasos/grado
    private val settings = SettingsStore(application)

    // Adaptador Bluetooth del teléfono (null si el equipo no tiene Bluetooth).
    // OJO: android.bluetooth.BluetoothManager (del sistema) NO es nuestra clase BluetoothManager; por eso va completo.
    private val bluetoothAdapter =
        (application.getSystemService(Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager)?.adapter

    // Armamos la cadena de dependencias a mano (sin librerías de inyección, para mantenerlo simple).
    private val repository = AstroRepository(
        btManager = BluetoothManager(viewModelScope),
        sensorManager = PhoneSensorManager(application),
        bluetoothAdapter = bluetoothAdapter,
        repoScope = viewModelScope,
        savedStepsPerDegree = { settings.stepsPerDegree }
    )

    // MutableStateFlow = el estado "editable" (privado). StateFlow = vista de solo lectura (pública).
    // StateFlow siempre tiene un valor y solo entrega el MÁS RECIENTE: la UI nunca se atrasa procesando una cola.
    private val _uiState = MutableStateFlow(
        AstroUiState(
            stepsPerDegree = settings.stepsPerDegree ?: 10f,
            isStepsCalibrated = settings.stepsPerDegree != null,
            autoReconnect = settings.autoReconnect,
            redNightMode = settings.redNightMode,
            isSensorAvailable = repository.isSensorAvailable
        )
    )
    val uiState: StateFlow<AstroUiState> = _uiState

    // Avisos de una sola vez (Snackbar). No van en uiState porque, al ser "estado", se volverían a mostrar
    // cada vez que la UI se re-suscribe (ej. al volver a abrir la app).
    private val _messages = MutableSharedFlow<String>(
        extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages: SharedFlow<String> = _messages

    // Para distinguir "me desconecté yo" de "se cortó solo"
    private var userRequestedDisconnect = false
    private var previousConnectionState = ConnectionState.DISCONNECTED
    private var reconnectJob: Job? = null

    init {
        observeRepository()
        refreshBluetoothInfo()
    }

    // ------------------------------------------------------------------
    // OBSERVAR AL REPOSITORIO: cada Flow se copia al uiState
    // ------------------------------------------------------------------

    private fun observeRepository() {
        // Cada launch{} crea una corrutina independiente que "escucha" un flujo para siempre
        // (hasta que el ViewModel muera). Se ejecutan en el hilo principal: son operaciones livianas.
        viewModelScope.launch {
            repository.connectionFlow.collect { handleConnectionState(it) }
        }
        viewModelScope.launch {
            repository.telemetryFlow.collect { data ->
                // update{} cambia el estado de forma atómica (seguro aunque lo llamen varios hilos)
                _uiState.update { it.copy(telemetry = data) }
            }
        }
        viewModelScope.launch {
            repository.phonePositionFlow.collect { pos ->
                _uiState.update { it.copy(phonePosition = pos) }
            }
        }
        viewModelScope.launch {
            repository.motorPositionFlow.collect { pos ->
                _uiState.update { it.copy(motorPosition = pos) }
            }
        }
        viewModelScope.launch {
            repository.modeFlow.collect { mode -> _uiState.update { it.copy(mode = mode) } }
        }
        viewModelScope.launch {
            repository.isCalibratingFlow.collect { c -> _uiState.update { it.copy(isCalibrating = c) } }
        }
        viewModelScope.launch {
            // Reenviamos los avisos del repositorio a la UI
            repository.messages.collect { _messages.tryEmit(it) }
        }
    }

    private fun handleConnectionState(state: ConnectionState) {
        val connected = state == ConnectionState.CONNECTED
        _uiState.update { current ->
            current.copy(
                connectionState = state,
                // Sin conexión no hay telemetría, posición ni motores vigentes: evitamos mostrar datos viejos como actuales
                telemetry = if (connected) current.telemetry else null,
                phonePosition = if (connected) current.phonePosition else null,
                motorPosition = if (connected) current.motorPosition else null,
                errorMessage = when (state) {
                    ConnectionState.ERROR ->
                        "No se pudo conectar. Verificá que el telescopio esté encendido y emparejado."
                    else -> null
                },
                // Si se conectó, termina cualquier reconexión en curso
                reconnectAttempt = if (connected) 0 else current.reconnectAttempt
            )
        }

        if (connected) {
            // Conexión exitosa: cancelamos reintentos y recordamos este dispositivo para la próxima vez
            reconnectJob?.cancel()
            _uiState.value.selectedDeviceMac?.let { settings.lastDeviceMac = it }
        }

        // Si estábamos conectados y pasamos a DISCONNECTED/ERROR sin que el usuario lo pidiera => se cortó solo
        val lostConnection = previousConnectionState == ConnectionState.CONNECTED &&
            (state == ConnectionState.DISCONNECTED || state == ConnectionState.ERROR) &&
            !userRequestedDisconnect
        if (lostConnection) {
            _messages.tryEmit("Se perdió la conexión con el telescopio")
            val mac = _uiState.value.selectedDeviceMac
            if (_uiState.value.autoReconnect && mac != null) startReconnect(mac)
        }
        if (state == ConnectionState.DISCONNECTED || state == ConnectionState.ERROR) {
            userRequestedDisconnect = false
        }
        previousConnectionState = state
    }

    // ------------------------------------------------------------------
    // RECONEXIÓN AUTOMÁTICA (limitada)
    // ------------------------------------------------------------------

    private fun startReconnect(mac: String) {
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            for ((index, waitMs) in RECONNECT_DELAYS_MS.withIndex()) {
                _uiState.update { it.copy(reconnectAttempt = index + 1) }
                delay(waitMs)

                // Si el Bluetooth está apagado no tiene sentido intentar: esperamos al siguiente turno
                if (!repository.isBluetoothEnabled()) continue

                repository.connect(mac)
                // connectionFlow tiene replay=1: first{} arranca viendo el estado MÁS RECIENTE (CONNECTING)
                // y espera hasta que el intento termine bien (CONNECTED) o mal (ERROR).
                val result = withTimeoutOrNull(CONNECT_ATTEMPT_TIMEOUT_MS) {
                    repository.connectionFlow.first {
                        it == ConnectionState.CONNECTED || it == ConnectionState.ERROR
                    }
                }
                if (result == ConnectionState.CONNECTED) return@launch // handleConnectionState limpia el resto
            }
            _uiState.update { it.copy(reconnectAttempt = 0) }
            _messages.tryEmit("No se pudo reconectar. Conectá manualmente desde la pantalla Conexión.")
        }
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        _uiState.update { it.copy(reconnectAttempt = 0) }
    }

    // ------------------------------------------------------------------
    // EVENTOS DE LA UI
    // ------------------------------------------------------------------

    /**
     * Releer dispositivos emparejados y estado del Bluetooth. Llamar al abrir la pantalla, al volver
     * a primer plano, al cambiar el estado del Bluetooth y tras conceder permisos.
     */
    fun refreshBluetoothInfo() {
        val devices = repository.getPairedDevices()
        _uiState.update { current ->
            // Mantener la selección si sigue existiendo; si no, preseleccionar la última usada;
            // si hay un solo dispositivo emparejado, elegirlo directamente (caso típico: solo el HC-05).
            val selected = current.selectedDeviceMac?.takeIf { mac -> devices.any { it.macAddress == mac } }
                ?: settings.lastDeviceMac?.takeIf { mac -> devices.any { it.macAddress == mac } }
                ?: devices.singleOrNull()?.macAddress
            current.copy(
                isBluetoothEnabled = repository.isBluetoothEnabled(),
                pairedDevices = devices,
                selectedDeviceMac = selected
            )
        }
    }

    fun onDeviceSelected(mac: String) {
        _uiState.update { it.copy(selectedDeviceMac = mac) }
    }

    fun onConnectClicked() {
        val mac = _uiState.value.selectedDeviceMac
        if (mac == null) {
            _messages.tryEmit("Elegí un dispositivo de la lista")
            return
        }
        cancelReconnect() // una conexión manual reemplaza cualquier reintento automático
        userRequestedDisconnect = false
        repository.connect(mac)
    }

    fun onDisconnectClicked() {
        cancelReconnect()
        userRequestedDisconnect = true
        repository.disconnect()
    }

    fun onManualCommand(char: Char) = repository.sendManualCommand(char)

    fun onSwitchMode(mode: AppMode) {
        when (mode) {
            AppMode.AUTOMATIC -> repository.switchToAuto()
            AppMode.MANUAL -> repository.switchToManual()
        }
    }

    fun onSetTarget(az: Float, alt: Float) = repository.sendAutoTarget(az, alt)

    fun onTrackingToggle(enabled: Boolean) = repository.setTracking(enabled)

    fun onSpeedChanged(stepsPerSec: Float) = repository.setSiderealSpeed(stepsPerSec)

    fun onCalibrationStart() = repository.startCalibration()

    fun onCalibrationCancel() = repository.cancelCalibration(_uiState.value.stepsPerDegree)

    /** El usuario midió cuántos grados se movió REALMENTE el telescopio. Calculamos y enviamos el nuevo pasos/grado. */
    fun onCalibrationValue(measuredDeg: Float) {
        val newValue = repository.applyCalibration(measuredDeg, _uiState.value.stepsPerDegree)
        if (newValue != null) {
            // Lo guardamos en el teléfono: se reenvía al Arduino en cada conexión (opción B)
            settings.stepsPerDegree = newValue
            _uiState.update { it.copy(stepsPerDegree = newValue, isStepsCalibrated = true) }
            _messages.tryEmit("Calibración aplicada: ${String.format(Locale.US, "%.2f", newValue)} pasos/grado")
        }
    }

    fun onAutoReconnectChanged(enabled: Boolean) {
        settings.autoReconnect = enabled
        _uiState.update { it.copy(autoReconnect = enabled) }
        if (!enabled) cancelReconnect()
    }

    fun onRedNightModeChanged(enabled: Boolean) {
        settings.redNightMode = enabled
        _uiState.update { it.copy(redNightMode = enabled) }
    }

    /** La UI avisa si está visible (onStart/onStop) para apagar los sensores en segundo plano y ahorrar batería. */
    fun onAppVisibilityChanged(visible: Boolean) = repository.setUiVisible(visible)

    /** Confirma que el usuario ya vio el mensaje de error persistente. */
    fun onErrorShown() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /** Se llama automáticamente cuando el ViewModel se destruye (la app se cierra de verdad). */
    override fun onCleared() {
        repository.release() // cierra socket y desregistra sensores: evita leaks y batería drenada
        super.onCleared()
    }
}
