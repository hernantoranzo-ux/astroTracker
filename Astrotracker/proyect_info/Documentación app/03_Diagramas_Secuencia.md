# 03 — Diagramas de Secuencia UML (Astrotracker)

> Cubren los **5 flujos críticos** del sistema ordenados de mayor a menor frecuencia de ocurrencia.

---

## Flujo 1: Conexión Bluetooth (SPP)

Ocurre una vez por sesión al iniciar la app.

```mermaid
sequenceDiagram
    actor Usuario
    participant UI as MainActivity / ConnectFragment
    participant VM as AstroViewModel
    participant Repo as AstroRepository
    participant BT as BluetoothManager
    participant OS as Android OS (BluetoothAdapter)
    participant HC05 as HC-05 (Arduino)

    Usuario->>UI: Selecciona dispositivo en Spinner
    Usuario->>UI: Presiona botón "Conectar"

    UI->>VM: onConnectClicked(macAddress)
    VM->>VM: emite UiState(isLoading=true)
    VM->>Repo: connect(macAddress)

    Repo->>BT: connect(macAddress)
    BT->>OS: BluetoothAdapter.getRemoteDevice(mac)
    OS-->>BT: BluetoothDevice
    BT->>OS: device.createRfcommSocketToServiceRecord(UUID_SPP)
    OS-->>BT: BluetoothSocket

    Note over BT, HC05: Coroutine en Dispatchers.IO
    BT->>HC05: socket.connect() [operación bloqueante]

    alt Conexión exitosa
        HC05-->>BT: ACK (TCP/SPP handshake)
        BT-->>Repo: emit ConnectionState.CONNECTED
        Repo->>BT: startListening() [lanza Flow de lectura]
        Repo-->>VM: emit ConnectionState.CONNECTED
        VM->>VM: emite UiState(isConnected=true, mode=MANUAL)
        VM-->>UI: StateFlow actualizado
        UI->>UI: Habilita controles, muestra "Conectado"
        UI->>UI: Registra sensores (SENSOR_DELAY_UI)
    else IOException (timeout, emparejamiento, apagado)
        HC05--xBT: Fallo de conexión
        BT-->>Repo: emit ConnectionState.ERROR(msg)
        Repo-->>VM: emit ConnectionState.ERROR(msg)
        VM->>VM: emite UiState(error="No se pudo conectar")
        VM-->>UI: StateFlow actualizado
        UI->>UI: Muestra Snackbar con error
    end
```

---

## Flujo 2: Modo Manual — D-Pad + Visualización de Sensores

Ocurre repetidamente mientras el usuario opera manualmente el telescopio.

```mermaid
sequenceDiagram
    actor Usuario
    participant UI as ManualFragment
    participant VM as AstroViewModel
    participant Repo as AstroRepository
    participant SensorMgr as PhoneSensorManager
    participant BT as BluetoothManager
    participant Arduino as Arduino (HC-05)

    Note over SensorMgr, UI: Bucle de sensores (SENSOR_DELAY_UI ~60ms)
    loop Cada ~60ms (solo en foreground)
        SensorMgr->>SensorMgr: SensorEventListener.onSensorChanged()
        SensorMgr->>Repo: emit PhonePosition(azimuth, altitude)
        Repo->>VM: sensorFlow actualizado
        VM->>VM: actualiza UiState.sensorReading
        VM->>UI: StateFlow emite nuevo valor
        UI->>UI: Actualiza TextView Azimut y Altitud
        Note right of UI: Solo visual. NO se envía al Arduino en modo manual.
    end

    Note over UI, Arduino: Cuando usuario toca el D-Pad
    Usuario->>UI: Toca botón "▲" (Adelante / DEC+)
    UI->>VM: onManualCommand('F')
    VM->>Repo: sendCommand('F'.toString())
    Repo->>BT: write("F".toByteArray())

    Note over BT, Arduino: Dispatchers.IO — NO bloquea el hilo principal
    BT->>Arduino: 0x46 ('F')
    Arduino->>Arduino: motorY.moveTo(pos + 200)

    Note over UI, Arduino: Recepción asíncrona de telemetría (500ms del Arduino)
    loop Cada 500ms
        Arduino->>BT: "DATA:120.5,45.2,0.0,0\n"
        BT->>BT: lineBuffer acumula hasta '\n'
        BT->>Repo: emit "DATA:120.5,45.2,0.0,0"
        Repo->>Repo: parseTelemetry() → TelemetryData
        Repo->>VM: telemetryFlow.emit(TelemetryData)
        VM->>VM: actualiza UiState.telemetry
        VM->>UI: StateFlow emite nuevo valor
        UI->>UI: Actualiza panel de telemetría
    end
```

---

## Flujo 3: Modo Automático — Envío de Coordenadas y Telemetría

Flujo central del sistema astronómico.

```mermaid
sequenceDiagram
    actor Usuario
    participant UI as AutoFragment
    participant VM as AstroViewModel
    participant Repo as AstroRepository
    participant SensorMgr as PhoneSensorManager
    participant BT as BluetoothManager
    participant Arduino as Arduino (HC-05)

    Note over UI, Arduino: Transición desde Modo Manual
    Usuario->>UI: Presiona pestaña "Automático"
    UI->>VM: onModeSwitch(AUTO)
    VM->>Repo: sendCommand("a")
    Repo->>BT: write("a".toByteArray())
    BT->>Arduino: 0x61 ('a')
    Arduino->>Arduino: modoAutomatico = true
    VM->>SensorMgr: setSensorRate(SENSOR_DELAY_NORMAL)
    VM->>VM: emite UiState(mode=AUTO)

    Note over SensorMgr, Arduino: Bucle de sensores y envío (~200ms)
    loop Cada ~200ms
        SensorMgr->>SensorMgr: onSensorChanged()
        SensorMgr->>Repo: emit PhonePosition(az=148.2, alt=40.1)
        Repo->>BT: write("AZ_TEL:148.20\n")
        BT->>Arduino: "AZ_TEL:148.20\n"
        Arduino->>Arduino: azActual = 148.20
        Repo->>BT: write("ALT_TEL:40.10\n")
        BT->>Arduino: "ALT_TEL:40.10\n"
        Arduino->>Arduino: altActual = 40.10
    end

    Note over Usuario, Arduino: Usuario ingresa coordenadas objetivo y apunta
    Usuario->>UI: Escribe AzObjetivo=152.75, AltObjetivo=42.30
    Usuario->>UI: Presiona botón "Apuntar"
    UI->>VM: onSetTarget(az=152.75, alt=42.30)
    VM->>Repo: sendCommand("AZ_OBJ:152.75\n")
    VM->>Repo: sendCommand("ALT_OBJ:42.30\n")
    Repo->>BT: write secuencial
    BT->>Arduino: "AZ_OBJ:152.75\n"
    BT->>Arduino: "ALT_OBJ:42.30\n"
    Arduino->>Arduino: Calcula error y mueve motores

    Note over Arduino, UI: Telemetría de corrección (cada 500ms)
    loop Cada 500ms
        Arduino->>BT: "DATA:148.2,40.1,4.55,0\n"
        BT->>Repo: emit rawLine
        Repo->>Repo: parseTelemetry() → TelemetryData(error=4.55)
        Repo->>VM: emit TelemetryData
        VM->>UI: StateFlow actualizado
        UI->>UI: Panel muestra: Error=4.55° / Tracking=OFF
    end

    Note over Usuario, Arduino: Usuario activa el seguimiento sideral
    Usuario->>UI: Activa Toggle "Tracking Sideral"
    UI->>VM: onTrackingToggle(true)
    VM->>Repo: sendCommand("TRACK:ON\n")
    Repo->>BT: write("TRACK:ON\n")
    BT->>Arduino: "TRACK:ON\n"
    Arduino->>Arduino: trackingActivo = true
    Arduino->>Arduino: motorX.runSpeed() continuo

    loop Cada 500ms (tracking activo)
        Arduino->>BT: "DATA:148.2,40.1,4.55,1\n"
        BT->>Repo: emit rawLine
        Repo->>VM: emit TelemetryData(isTracking=true)
        VM->>UI: StateFlow actualizado
        UI->>UI: Panel muestra: Tracking=ACTIVO ✔
    end
```

---

## Flujo 4: Calibración de Pasos por Grado

Procedimiento de configuración de precisión del sistema.

```mermaid
sequenceDiagram
    actor Usuario
    participant UI as AutoFragment
    participant VM as AstroViewModel
    participant Repo as AstroRepository
    participant BT as BluetoothManager
    participant Arduino as Arduino

    Note over Usuario, Arduino: Inicio del proceso de calibración
    Usuario->>UI: Presiona "Iniciar Calibración"
    UI->>VM: onCalibrationStart()
    VM->>VM: emite UiState(calibrating=true)
    VM->>Repo: sendCommand("CAL:START\n")
    Repo->>BT: write("CAL:START\n")
    BT->>Arduino: "CAL:START\n"

    Arduino->>Arduino: trackingActivo = false
    Arduino->>Arduino: motorX.move(90 * pasosPorGrado)
    Note right of Arduino: El motor AR se mueve lo que el sistema cree que son 90°

    loop Motores en movimiento (500ms)
        Arduino->>BT: "DATA:0.0,0.0,0.0,0\n"
        BT->>Repo: emit TelemetryData
        Repo->>VM: emit TelemetryData
        VM->>UI: StateFlow actualizado
        UI->>UI: Muestra "Calibrando... espere"
    end

    Note over Usuario, UI: El usuario mide físicamente el ángulo real recorrido
    Usuario->>UI: Ingresa valor medido (ej: "87.3")
    Note over Usuario, UI: La app calcula: nuevosPasosPorGrado = (pasosPorGrado * 90) / 87.3

    UI->>VM: onCalibrationValue(measuredDegrees=87.3)
    VM->>VM: newStepsPerDegree = (currentSteps * 90.0) / 87.3
    VM->>Repo: sendCommand("CAL:103.09\n")
    Repo->>BT: write("CAL:103.09\n")
    BT->>Arduino: "CAL:103.09\n"
    Arduino->>Arduino: pasosPorGrado = 103.09

    VM->>VM: emite UiState(calibrating=false)
    VM->>UI: StateFlow actualizado
    UI->>UI: Muestra "Calibración completada ✔"
```

---

## Flujo 5: Desconexión y Manejo de Errores

```mermaid
sequenceDiagram
    participant UI as Actividad / Fragments
    participant VM as AstroViewModel
    participant Repo as AstroRepository
    participant BT as BluetoothManager
    participant Arduino as Arduino (HC-05)
    participant SensorMgr as PhoneSensorManager

    alt Desconexión intencional por el usuario
        UI->>VM: onDisconnectClicked()
        VM->>Repo: disconnect()
        Repo->>SensorMgr: unregisterListener()
        Repo->>BT: close()
        BT->>BT: socket.close()
        BT->>Repo: emit ConnectionState.DISCONNECTED
        Repo->>VM: emit ConnectionState.DISCONNECTED
        VM->>VM: emite UiState(isConnected=false)
        VM->>UI: StateFlow actualizado
        UI->>UI: Resetea controles, muestra "Desconectado"

    else Desconexión abrupta (apagado del Arduino / pérdida de señal)
        Arduino--xBT: Señal perdida
        BT->>BT: socket.inputStream.read() lanza IOException
        Note over BT: Coroutine captura la excepción
        BT->>Repo: emit ConnectionState.ERROR("Conexión perdida")
        Repo->>SensorMgr: unregisterListener()
        Repo->>VM: emit ConnectionState.ERROR
        VM->>VM: emite UiState(isConnected=false, error="Conexión perdida")
        VM->>UI: StateFlow actualizado
        UI->>UI: Muestra Snackbar "Conexión perdida"\nResetea controles

    else Telemetría no recibida (timeout 2000ms)
        Note over Repo: Timer watchdog en coroutine del repositorio
        Repo->>Repo: Timeout sin DATA: recibido
        Repo->>VM: emit TelemetryState.STALE
        VM->>UI: StateFlow actualizado
        UI->>UI: Alerta visual "Sin respuesta del telescopio". Mantiene modo actual
    end
```
