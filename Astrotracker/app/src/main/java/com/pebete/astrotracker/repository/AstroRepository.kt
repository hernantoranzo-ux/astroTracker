package com.pebete.astrotracker.repository

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import com.pebete.astrotracker.data.bluetooth.BluetoothManager
import com.pebete.astrotracker.data.bluetooth.ConnectionState
import com.pebete.astrotracker.data.model.AppMode
import com.pebete.astrotracker.data.model.BluetoothDeviceInfo
import com.pebete.astrotracker.data.model.MotorPosition
import com.pebete.astrotracker.data.model.PhonePosition
import com.pebete.astrotracker.data.model.TelemetryData
import com.pebete.astrotracker.data.model.TelemetryState
import com.pebete.astrotracker.data.sensor.PhoneSensorManager
import com.pebete.astrotracker.protocol.CommandProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * AstroRepository = el "mediador" entre la capa de hardware (BluetoothManager + PhoneSensorManager)
 * y el ViewModel. El ViewModel NO sabe nada de sockets ni sensores: solo le pide cosas a esta clase.
 *
 * Qué hace:
 *  1. Escucha el estado de la conexión y, según éste, arranca/detiene la lectura de telemetría,
 *     el "watchdog" (perro guardián) y los sensores.
 *  2. Traduce acciones de alto nivel ("apuntá a este objeto") a mensajes del protocolo (CommandProtocol).
 *  3. Valida TODO antes de enviar (conexión, modo, rangos), porque un comando mal enviado
 *     puede mover físicamente el telescopio.
 *
 * HILOS: todas las corrutinas de esta clase corren en [repoScope] (en la app será el viewModelScope,
 * hilo principal). Como todo el estado mutable se toca desde ese mismo hilo, no necesitamos locks.
 * Lo bloqueante (socket) ya vive en BluetoothManager con Dispatchers.IO.
 */
class AstroRepository(
    private val btManager: BluetoothManager,
    private val sensorManager: PhoneSensorManager,
    private val bluetoothAdapter: BluetoothAdapter?,
    private val repoScope: CoroutineScope,
    // Devuelve el último pasos/grado calibrado guardado en el teléfono (null = nunca calibrado).
    // Se reenvía al Arduino en cada conexión (opción B: el firmware no permite leerlo).
    private val savedStepsPerDegree: () -> Float?
) {

    private companion object {
        const val TAG = "AstroRepository"

        // ICD sección 2: si no llega un DATA: en 2000 ms => "Sin respuesta del telescopio"
        const val TELEMETRY_TIMEOUT_MS = 2000L
        // Cada cuánto el watchdog revisa. 500 ms = el período de telemetría del Arduino. Despierta 2 veces/s: costo ínfimo.
        const val WATCHDOG_INTERVAL_MS = 500L

        // Mínimo tiempo entre envíos de AZ_TEL/ALT_TEL. El HC-05 va a 9600 baud (~960 bytes/s)
        // y el Arduino procesa 1 línea por vuelta de loop(): no lo ahogamos.
        const val MIN_PHONE_SEND_INTERVAL_MS = 200L
        // Una lectura del sensor más vieja que esto se considera inválida y NO se envía
        const val MAX_POSITION_AGE_MS = 1000L

        // Mínimo tiempo entre dos comandos manuales. Protege al HC-05 (9600 baud) y al Arduino
        // cuando el usuario mantiene presionado el D-pad o toca muy rápido.
        const val MIN_MANUAL_INTERVAL_MS = 50L

        // Tiempo que esperamos para que salgan los mensajes de seguridad antes de cerrar el socket
        const val GOODBYE_FLUSH_MS = 250L

        // Comandos permitidos en modo manual (el cambio de modo ('a') tiene su propia función)
        const val MANUAL_ALLOWED = "FBRLZMP+-"

        // Límites de seguridad para los valores numéricos
        const val MAX_SIDEREAL_SPEED = 1000f      // = velocidad máxima de los motores en el firmware
        const val MIN_STEPS_PER_DEGREE = 0.1f
        const val MAX_STEPS_PER_DEGREE = 10000f
        const val CALIBRATION_THEORETICAL_DEG = 90f // CAL:START mueve 90° teóricos (ICD 3.2)
    }

    // ------------------------------------------------------------------
    // FLOWS PÚBLICOS (lo que el ViewModel observa)
    // ------------------------------------------------------------------

    /** Estado de la conexión Bluetooth, tal cual lo informa BluetoothManager. */
    val connectionFlow: SharedFlow<ConnectionState> = btManager.connectionState

    // Telemetría. DROP_OLDEST + buffer: tryEmit() nunca falla; si la UI es lenta se pierde el dato viejo, no el nuevo.
    private val _telemetryFlow = MutableSharedFlow<TelemetryData>(
        replay = 1, extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val telemetryFlow: SharedFlow<TelemetryData> = _telemetryFlow

    // Respuesta al comando 'P' (posición de los motores en pasos)
    private val _motorPositionFlow = MutableSharedFlow<MotorPosition>(
        replay = 1, extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val motorPositionFlow: SharedFlow<MotorPosition> = _motorPositionFlow

    /** Posición del teléfono (relay del sensor). Se muestra en pantalla en ambos modos. */
    val phonePositionFlow: SharedFlow<PhonePosition> = sensorManager.positionFlow

    // Avisos de UNA SOLA VEZ para mostrar en Snackbar (sin replay: si nadie escucha, se pierden, y está bien)
    private val _messages = MutableSharedFlow<String>(
        extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val messages: SharedFlow<String> = _messages

    private val _mode = MutableStateFlow(AppMode.MANUAL)
    /** Modo actual (lo que la app CREE que es el modo del Arduino; se sincroniza al conectar). */
    val modeFlow: StateFlow<AppMode> = _mode

    private val _isCalibrating = MutableStateFlow(false)
    val isCalibratingFlow: StateFlow<Boolean> = _isCalibrating

    val isSensorAvailable: Boolean get() = sensorManager.isSensorAvailable

    // ------------------------------------------------------------------
    // ESTADO INTERNO
    // ------------------------------------------------------------------

    private var readingJob: Job? = null     // corrutina que lee líneas del Arduino
    private var watchdogJob: Job? = null    // corrutina que vigila el timeout de telemetría
    private var autoSendJob: Job? = null    // corrutina que envía AZ_TEL/ALT_TEL en modo automático

    private var lastDataMs = 0L             // instante (elapsedRealtime) del último DATA: válido
    private var staleReported = false       // para avisar STALE una sola vez por corte
    private var lastTelemetry: TelemetryData? = null

    private var uiVisible = false           // ¿la app está en primer plano? (ahorro de batería)
    private var activeSensorDelay: Int? = null // delay con el que están registrados los sensores (null = apagados)
    private var hasTarget = false           // ¿ya se envió un objetivo en este tramo de modo automático?
    private var lastPhoneSendMs = 0L
    private var lastManualSendMs = 0L      // instante del último comando manual enviado (rate limit)

    private val isConnected: Boolean
        get() = btManager.connectionState.replayCache.lastOrNull() == ConnectionState.CONNECTED

    init {
        // Observamos la conexión durante toda la vida del repositorio.
        // collect{} se suspende y reacciona a cada cambio de estado (patrón Observer).
        repoScope.launch {
            btManager.connectionState.collect { state -> onConnectionStateChanged(state) }
        }
    }

    // ------------------------------------------------------------------
    // CICLO DE VIDA / CONEXIÓN
    // ------------------------------------------------------------------

    private fun onConnectionStateChanged(state: ConnectionState) {
        // La posición de motores de una sesión anterior ya no es válida
        _motorPositionFlow.resetReplayCache()
        if (state == ConnectionState.CONNECTED) {
            lastDataMs = SystemClock.elapsedRealtime()
            staleReported = false
            lastTelemetry = null
            hasTarget = false
            _isCalibrating.value = false
            _mode.value = AppMode.MANUAL

            // SINCRONIZACIÓN DE MODO: el Arduino puede haber quedado en AUTOMÁTICO de una sesión anterior
            // (sigue encendido aunque la app se haya cerrado). Mandar "MANUAL\n" a un Arduino que ya está en
            // manual sería PELIGROSO: lo leería como M (guardar límite), A, N, U, A, L (mover motor).
            // Truco seguro: "a\n" lo pasa a automático si estaba en manual (el \n sobrante se ignora) y
            // es ignorado si ya estaba en automático. Luego "MANUAL\n" lo deja SIEMPRE en manual.
            btManager.write(CommandProtocol.MANUAL_TO_AUTO.toString() + "\n")
            // Opción B de pasosPorGrado: reenviamos el último valor calibrado desde la app (si existe),
            // porque el Arduino vuelve a 10.0 cada vez que se reinicia. En este punto el Arduino ya está
            // en modo automático (por eso entiende CAL:<v>).
            savedStepsPerDegree()?.let { btManager.write(CommandProtocol.buildCalValue(it)) }
            btManager.write(CommandProtocol.TRACK_OFF)
            btManager.write(CommandProtocol.AUTO_TO_MANUAL)

            startReadingLoop()
            startTelemetryWatchdog()
        } else {
            // Cualquier otro estado (DISCONNECTED, ERROR, CONNECTING): cortamos todo lo que dependa de la conexión.
            readingJob?.cancel(); readingJob = null
            watchdogJob?.cancel(); watchdogJob = null
            _isCalibrating.value = false
            hasTarget = false
            _mode.value = AppMode.MANUAL
        }
        updateSensors()
    }

    /** Lista de dispositivos YA emparejados (el HC-05 se empareja una vez desde Ajustes de Android, PIN 1234/0000). */
    @SuppressLint("MissingPermission") // La UI pide el permiso BLUETOOTH_CONNECT antes de llamar a esto
    fun getPairedDevices(): List<BluetoothDeviceInfo> {
        val adapter = bluetoothAdapter ?: return emptyList()
        return try {
            adapter.bondedDevices.orEmpty()
                .map { BluetoothDeviceInfo(it.name ?: "Dispositivo sin nombre", it.address) }
                .sortedBy { it.name }
        } catch (e: SecurityException) {
            // El usuario no concedió (o revocó) el permiso: devolvemos lista vacía en vez de crashear
            emptyList()
        }
    }

    fun isBluetoothSupported(): Boolean = bluetoothAdapter != null
    fun isBluetoothEnabled(): Boolean = bluetoothAdapter?.isEnabled == true

    fun connect(macAddress: String) {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            _messages.tryEmit("Este dispositivo no tiene Bluetooth")
            return
        }
        if (!adapter.isEnabled) {
            _messages.tryEmit("Activá el Bluetooth para conectar")
            return
        }
        val device = try {
            adapter.getRemoteDevice(macAddress) // lanza IllegalArgumentException si la MAC es inválida
        } catch (e: IllegalArgumentException) {
            _messages.tryEmit("Dirección de dispositivo inválida")
            return
        }
        btManager.connect(device)
    }

    /**
     * Desconexión "educada": si estábamos en automático, primero apagamos el seguimiento y
     * devolvemos el Arduino a manual (si no, el motor sideral seguiría girando SOLO sin nadie controlándolo).
     */
    fun disconnect() {
        if (isConnected && _mode.value == AppMode.AUTOMATIC) {
            btManager.write(CommandProtocol.TRACK_OFF + CommandProtocol.AUTO_TO_MANUAL)
            // Damos tiempo a la cola de escritura para que envíe los mensajes antes de cerrar el socket
            repoScope.launch {
                delay(GOODBYE_FLUSH_MS)
                btManager.disconnect()
            }
        } else {
            btManager.disconnect()
        }
    }

    /** Liberar TODO (llamado desde ViewModel.onCleared). Evita leaks de socket y sensores encendidos. */
    fun release() {
        autoSendJob?.cancel()
        readingJob?.cancel()
        watchdogJob?.cancel()
        sensorManager.stopListening()
        btManager.disconnect()
    }

    /** La UI avisa si la app está visible. En segundo plano apagamos sensores (batería). */
    fun setUiVisible(visible: Boolean) {
        uiVisible = visible
        updateSensors()
    }

    // ------------------------------------------------------------------
    // LECTURA DE TELEMETRÍA
    // ------------------------------------------------------------------

    private fun startReadingLoop() {
        readingJob?.cancel()
        readingJob = repoScope.launch {
            // listenForLines() corre en IO (flowOn); acá recibimos cada línea completa ya en el scope del repo
            btManager.listenForLines().collect { line -> handleLine(line) }
        }
    }

    private fun handleLine(line: String) {
        // parseTelemetry usa runCatching: nunca lanza excepción, devuelve null si algo está mal
        val data = CommandProtocol.parseTelemetry(line)
        if (data != null) {
            lastDataMs = SystemClock.elapsedRealtime()
            staleReported = false
            lastTelemetry = data
            _telemetryFlow.tryEmit(data)
        } else {
            // Puede ser la respuesta al comando 'P' ("posX,posY") o basura (ICD sección 7)
            val motorPos = CommandProtocol.parseMotorPosition(line)
            if (motorPos != null) {
                _motorPositionFlow.tryEmit(motorPos)
            } else {
                Log.d(TAG, "Línea descartada: $line")
            }
        }
    }

    private fun startTelemetryWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = repoScope.launch {
            while (isActive) {
                delay(WATCHDOG_INTERVAL_MS)
                val silentMs = SystemClock.elapsedRealtime() - lastDataMs
                if (silentMs > TELEMETRY_TIMEOUT_MS && !staleReported) {
                    staleReported = true
                    // Reemitimos el último dato conocido marcado como STALE (o uno vacío si nunca llegó nada)
                    val base = lastTelemetry ?: TelemetryData(0f, 0f, 0f, false)
                    _telemetryFlow.tryEmit(base.copy(state = TelemetryState.STALE))
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // SENSORES
    // ------------------------------------------------------------------

    /**
     * Decide si los sensores deben estar encendidos y con qué frecuencia. Se llama cada vez que cambia
     * algo relevante (conexión, modo, app visible). Regla de ahorro de batería:
     * sensores ENCENDIDOS solo si hay conexión Y la app está en pantalla.
     */
    private fun updateSensors() {
        val shouldRun = isConnected && uiVisible && sensorManager.isSensorAvailable
        val mode = _mode.value

        if (shouldRun) {
            // ICD sección 5: automático = NORMAL (~200 ms), manual = UI (~60 ms, solo visualización)
            val delayWanted = if (mode == AppMode.AUTOMATIC) SensorManager.SENSOR_DELAY_NORMAL
                              else SensorManager.SENSOR_DELAY_UI
            if (activeSensorDelay != delayWanted) {
                sensorManager.startListening(delayWanted)
                activeSensorDelay = delayWanted
            }
        } else if (activeSensorDelay != null) {
            sensorManager.stopListening()
            activeSensorDelay = null
        }

        // Envío de la posición del teléfono al Arduino: solo automático + sensores activos + objetivo ya enviado
        autoSendJob?.cancel()
        autoSendJob = null
        if (shouldRun && mode == AppMode.AUTOMATIC && hasTarget) {
            autoSendJob = repoScope.launch {
                sensorManager.positionFlow.collect { pos -> sendPhonePosition(pos) }
            }
        }
    }

    /** Envía AZ_TEL y ALT_TEL juntos (en un solo write, para que lleguen consecutivos). */
    fun sendPhonePosition(pos: PhonePosition) {
        if (!isConnected || _mode.value != AppMode.AUTOMATIC) return
        if (!isValidPosition(pos)) return

        val now = SystemClock.elapsedRealtime()
        if (now - lastPhoneSendMs < MIN_PHONE_SEND_INTERVAL_MS) return // limitamos la tasa de envío
        lastPhoneSendMs = now

        btManager.write(CommandProtocol.buildAzTel(pos.azimuth) + CommandProtocol.buildAltTel(pos.altitude))
    }

    /** Una posición sirve solo si es reciente y sus números son finitos (no NaN/Infinito). */
    private fun isValidPosition(pos: PhonePosition): Boolean =
        pos.azimuth.isFinite() && pos.altitude.isFinite() &&
            System.currentTimeMillis() - pos.timestamp <= MAX_POSITION_AGE_MS

    private fun latestFreshPosition(): PhonePosition? =
        sensorManager.positionFlow.replayCache.lastOrNull()?.takeIf { isValidPosition(it) }

    // ------------------------------------------------------------------
    // COMANDOS
    // ------------------------------------------------------------------

    /** Devuelve true si hay conexión; si no, avisa (ICD sección 7: "ignorar y mostrar Snackbar"). */
    private fun requireConnection(): Boolean {
        if (!isConnected) {
            _messages.tryEmit("Sin conexión con el telescopio")
            return false
        }
        return true
    }

    private fun requireMode(required: AppMode): Boolean {
        if (_mode.value != required) {
            _messages.tryEmit(
                if (required == AppMode.MANUAL) "Esta acción solo está disponible en modo manual"
                else "Esta acción solo está disponible en modo automático"
            )
            return false
        }
        return true
    }

    /** Envío "crudo" (ej. un String ya armado por CommandProtocol). Solo si hay conexión. */
    fun sendRawCommand(cmd: String) {
        if (!requireConnection()) return
        btManager.write(cmd)
    }

    /** Comando de modo manual (F B R L Z M P + -). Cualquier otro caracter se rechaza. */
    fun sendManualCommand(char: Char) {
        if (char !in MANUAL_ALLOWED) {
            Log.w(TAG, "Comando manual no permitido: $char")
            return
        }
        if (!requireConnection() || !requireMode(AppMode.MANUAL)) return

        // Rate limit: ignoramos comandos que lleguen demasiado seguidos (mantener presionado, spam de toques)
        val now = SystemClock.elapsedRealtime()
        if (now - lastManualSendMs < MIN_MANUAL_INTERVAL_MS) return
        lastManualSendMs = now

        btManager.write(char.toString())
    }

    /**
     * Fija el objeto a apuntar (modo automático). Primero enviamos la posición ACTUAL del teléfono y recién
     * después el objetivo: así el Arduino nunca calcula el error con una posición vieja (movimiento brusco).
     */
    fun sendAutoTarget(az: Float, alt: Float) {
        if (!requireConnection() || !requireMode(AppMode.AUTOMATIC)) return
        if (!az.isFinite() || !alt.isFinite() || alt < -90f || alt > 90f) {
            _messages.tryEmit("Coordenadas fuera de rango (altitud -90..90)")
            return
        }
        val pos = latestFreshPosition()
        if (pos == null) {
            _messages.tryEmit("Esperando datos del sensor del teléfono...")
            return
        }
        // Normalizamos el azimut a [0, 360): ej. 370 -> 10, -10 -> 350
        val azNorm = ((az % 360f) + 360f) % 360f

        btManager.write(
            CommandProtocol.buildAzTel(pos.azimuth) + CommandProtocol.buildAltTel(pos.altitude) +
            CommandProtocol.buildAzObj(azNorm) + CommandProtocol.buildAltObj(alt)
        )
        if (!hasTarget) {
            hasTarget = true
            updateSensors() // ahora sí empezamos a transmitir la posición del teléfono continuamente
        }
    }

    fun setTracking(enabled: Boolean) {
        if (!requireConnection() || !requireMode(AppMode.AUTOMATIC)) return
        if (enabled && _isCalibrating.value) {
            _messages.tryEmit("Terminá la calibración antes de activar el seguimiento")
            return
        }
        btManager.write(if (enabled) CommandProtocol.TRACK_ON else CommandProtocol.TRACK_OFF)

        // Firmware v1.6: en automático el Arduino apaga el seguimiento si dejan de llegar AZ_TEL por 5 s.
        // Por eso, al activar el seguimiento también arrancamos el envío continuo de la posición del teléfono
        // (aunque no se haya fijado un objetivo).
        if (enabled && !hasTarget) {
            hasTarget = true
            updateSensors()
        }
    }

    fun setSiderealSpeed(stepsPerSec: Float) {
        if (!requireConnection() || !requireMode(AppMode.AUTOMATIC)) return
        if (!stepsPerSec.isFinite() || stepsPerSec <= 0f || stepsPerSec > MAX_SIDEREAL_SPEED) {
            _messages.tryEmit("Velocidad inválida (0 a ${MAX_SIDEREAL_SPEED.toInt()} pasos/s)")
            return
        }
        btManager.write(CommandProtocol.buildVelocity(stepsPerSec))
    }

    fun startCalibration() {
        if (!requireConnection() || !requireMode(AppMode.AUTOMATIC)) return
        _isCalibrating.value = true
        btManager.write(CommandProtocol.CAL_START) // el firmware apaga el tracking y mueve 90° teóricos
    }

    /**
     * Cancela la calibración. Firmware v1.6: el lazo automático queda bloqueado hasta recibir CAL:<valor>,
     * así que reenviamos el valor actual para destrabarlo sin cambiarlo.
     */
    fun cancelCalibration(currentStepsPerDegree: Float) {
        if (isConnected && _mode.value == AppMode.AUTOMATIC) {
            btManager.write(CommandProtocol.buildCalValue(currentStepsPerDegree))
        }
        _isCalibrating.value = false
    }

    /**
     * Cierra la calibración. Si el motor se movió 90° teóricos usando [currentStepsPerDegree] pasos/grado pero
     * el movimiento REAL medido fue [measuredDeg], el valor correcto es: nuevo = 90 * actual / medido.
     * Devuelve el nuevo valor (para que el ViewModel lo guarde) o null si el dato no es válido.
     */
    fun applyCalibration(measuredDeg: Float, currentStepsPerDegree: Float): Float? {
        if (!requireConnection() || !requireMode(AppMode.AUTOMATIC)) return null
        if (!measuredDeg.isFinite() || measuredDeg <= 0f || measuredDeg > 360f) {
            _messages.tryEmit("El ángulo medido debe estar entre 0 y 360 grados")
            return null
        }
        val newValue = CALIBRATION_THEORETICAL_DEG * currentStepsPerDegree / measuredDeg
        if (!newValue.isFinite() || newValue < MIN_STEPS_PER_DEGREE || newValue > MAX_STEPS_PER_DEGREE) {
            _messages.tryEmit("Valor de calibración fuera de rango")
            return null
        }
        btManager.write(CommandProtocol.buildCalValue(newValue))
        _isCalibrating.value = false
        return newValue
    }

    // ------------------------------------------------------------------
    // CAMBIO DE MODO
    // ------------------------------------------------------------------

    fun switchToAuto() {
        if (!requireConnection()) return
        if (_mode.value == AppMode.AUTOMATIC) return
        if (!sensorManager.isSensorAvailable) {
            _messages.tryEmit("Tu teléfono no tiene el sensor necesario para el modo automático")
            return
        }
        val pos = latestFreshPosition()
        if (pos == null) {
            _messages.tryEmit("Esperando datos del sensor del teléfono...")
            return
        }
        // El Arduino conserva las variables viejas de una sesión anterior. Para que NO se mueva al entrar,
        // le mandamos la posición actual como "actual" Y como "objetivo" (error = 0) apenas pasa a automático.
        btManager.write(
            CommandProtocol.MANUAL_TO_AUTO.toString() +
            CommandProtocol.buildAzTel(pos.azimuth) + CommandProtocol.buildAltTel(pos.altitude) +
            CommandProtocol.buildAzObj(pos.azimuth) + CommandProtocol.buildAltObj(pos.altitude)
        )
        hasTarget = false
        _mode.value = AppMode.AUTOMATIC
        updateSensors()
    }

    fun switchToManual() {
        if (!requireConnection()) return
        if (_mode.value == AppMode.MANUAL) return
        // El orden importa: apagamos el seguimiento y luego volvemos a manual
        btManager.write(CommandProtocol.TRACK_OFF + CommandProtocol.AUTO_TO_MANUAL)
        hasTarget = false
        _isCalibrating.value = false
        _mode.value = AppMode.MANUAL
        updateSensors()
    }
}
