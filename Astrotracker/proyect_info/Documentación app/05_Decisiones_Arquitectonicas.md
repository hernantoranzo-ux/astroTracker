# 05 — Decisiones Arquitectónicas (Astrotracker)

> Justificación técnica de las elecciones de diseño, patrones aplicados y riesgos mitigados.

---

## 5.1 Resumen de Patrones Aplicados

| Patrón | Dónde se aplica | Beneficio principal |
|--------|----------------|---------------------|
| **MVVM** | ViewModel ↔ Fragments | Sobrevive a rotaciones; separa UI de lógica |
| **Repository** | AstroRepository | Desacopla orígenes de datos de la presentación |
| **Observer / Flow** | StateFlow + SharedFlow | UI reactiva sin polling, sin callbacks enredados |
| **Facade** | CommandProtocol | Centraliza el protocolo; único punto de cambio |
| **Adapter** | PhoneSensorManager | Adapta API Android a Flow de Kotlin |
| **Single Source of Truth** | AstroUiState | Estado consistente, sin inconsistencias entre vistas |
| **Strategy** *(futuro)* | BluetoothManager | Reemplazable por WifiManager o BleManager |

---

## 5.2 Decisión 1: ¿Por qué MVVM y no MVC simple?

**Problema de MVC en Android:**
En Android, la `Activity` es simultáneamente Controlador y Vista. Al rotar el teléfono, el sistema **destruye y recrea la Activity**. Si la conexión Bluetooth y el `BluetoothSocket` viven dentro de la Activity, se cierran y se pierden en cada rotación.

**Solución con MVVM:**
El `ViewModel` es gestionado por el `ViewModelStore`, que sobrevive a los cambios de configuración (rotaciones, cambio de idioma, modo oscuro). El `BluetoothSocket` vivirá en `AstroRepository`, que es creado y retenido por el `ViewModel`.

```
Rotación de pantalla:
  Activity destruida ──────────────────────────────────────┐
  Activity recreada ────────────────────────────────────────┘
                              ↓
  ViewModel: sigue existiendo, mantiene la conexión BT ✔
```

---

## 5.3 Decisión 2: StateFlow vs. LiveData

Ambos sirven para observar datos del ViewModel, pero `StateFlow` fue elegido por:

| Criterio | LiveData | StateFlow |
|----------|----------|-----------|
| Requiere Activity/Fragment (lifecycle) | Sí | No (Flow es agnóstico) |
| Funciona con Coroutines nativamente | No (requiere adaptadores) | Sí |
| Valor inicial requerido | No | Sí (garantiza que la UI siempre tiene un estado) |
| Testeable sin Android framework | Difícil | Fácil (puro Kotlin) |
| Soporta `collect` en coroutines | Limitado | Nativo |

---

## 5.4 Decisión 3: Flow en BluetoothManager (no callbacks)

El enfoque clásico de leer Bluetooth en Android usa callbacks o handlers. El problema es el **callback hell**: cada evento genera un callback anidado en otro.

Con `Flow`, el lector de Bluetooth se convierte en un productor de eventos:

```kotlin
// BluetoothManager
fun listenForLines(): Flow<String> = flow {
    val reader = socket.inputStream.bufferedReader()
    while (true) {
        val line = reader.readLine() ?: break  // bloqueante, en IO thread
        emit(line)
    }
}.flowOn(Dispatchers.IO)
```

El `AstroRepository` simplemente colecciona este flow y lo procesa. Si el socket se cierra, el `flow` termina naturalmente y la excepción se propaga de forma limpia.

---

## 5.5 Decisión 4: CommandProtocol como Singleton

Toda la serialización y parseo de comandos vive en un único objeto `CommandProtocol`. Sin esto, los strings del protocolo (`"AZ_OBJ:"`, `"TRACK:ON\n"`, etc.) estarían dispersos como **magic strings** en el ViewModel, el Repository y los Fragments.

**Ventaja de mantenimiento:** Si el Arduino cambia la sintaxis (ej. `TRACK:ON` → `TRACKING:ENABLE`), se modifica **un único archivo**.

**Ventaja de testing:** `CommandProtocol.parseTelemetry()` puede ser testeado unitariamente de forma aislada, sin necesidad de un dispositivo Bluetooth real.

---

## 5.6 Decisión 5: PhoneSensorManager como Adapter

La API de Android para sensores (`SensorManager`) usa callbacks (`SensorEventListener`). Esta API está ligada al framework de Android y es difícil de testear.

El `PhoneSensorManager` actúa como un **Adapter** que transforma esos callbacks en un `Flow<PhonePosition>`, permitiendo:
- Controlar la frecuencia del sensor desde un único punto.
- Testear la lógica de procesamiento de `SensorEvent` de forma unitaria.
- Facilitar el reemplazo futuro por otro sensor (ej. GPS externo o sensor de aceleración diferente).

---

## 5.7 Bugs Clásicos de Android Prevenidos

| Bug | Causa común | Prevención implementada |
|-----|-------------|------------------------|
| **ANR (App Not Responding)** | Operación bloqueante en hilo principal | `BluetoothManager` usa `Dispatchers.IO` exclusivamente |
| **Crash al rotar la pantalla** | Estado almacenado en la Activity | `AstroViewModel` + `StateFlow` sobreviven a la rotación |
| **Crash por NullPointerException en telemetría** | Parseo directo sin validación | `CommandProtocol.parseTelemetry()` usa `runCatching` y retorna `null` en fallo |
| **Memory leak del socket Bluetooth** | Socket no cerrado en `onDestroy()` | `AstroRepository` expone `disconnect()` llamado desde `ViewModel.onCleared()` |
| **Batería drenada por sensores** | Sensores registrados pero no desregistrados | `PhoneSensorManager.stopListening()` llamado al ir a background y al desconectar |
| **UI congelada por emisión rápida** | Flow emite más rápido de lo que la UI dibuja | `StateFlow` tiene semántica de último valor: la UI nunca procesa una cola de 1000 eventos, solo el más reciente |
| **Crash por permisos Bluetooth en Android 12+** | Falta de declaración de `BLUETOOTH_CONNECT` | `MainActivity` solicita permisos en runtime según versión de API |
| **Comando enviado a socket cerrado** | Race condition al desconectar | `write()` en `BluetoothManager` verifica que el socket no sea null y captura `IOException` |

---

## 5.8 Escalabilidad Futura

La arquitectura permite extender el sistema sin refactorizaciones mayores:

```
Escenario 1: Cambiar Bluetooth HC-05 por Wi-Fi (ESP32)
  └── Crear WifiManager que implemente la misma interfaz que BluetoothManager
  └── AstroRepository recibe la inyección del nuevo manager
  └── El resto de la app no cambia.

Escenario 2: Agregar pantalla de historial de sesiones
  └── Agregar una LocalDatabase (Room) en la capa de datos
  └── AstroRepository guarda TelemetryData en la BD
  └── Un nuevo HistoryViewModel lee de la BD
  └── Cero cambios en BluetoothManager, PhoneSensorManager o los fragments existentes.

Escenario 3: Agregar un segundo telescopio
  └── BluetoothManager puede gestionar múltiples sockets
  └── AstroRepository recibe una lista de managers
  └── AstroUiState agrega un campo selectedTelescope

Escenario 4: Agregar control por voz
  └── Un nuevo SpeechCommandManager convierte voz → comandos
  └── Llama a los mismos métodos de AstroViewModel (onManualCommand, onSetTarget, etc.)
  └── Cero cambios en la cadena de datos.
```

---

## 5.9 Permisos de Android Requeridos

A declarar en `AndroidManifest.xml`:

```xml
<!-- Bluetooth Clásico -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />

<!-- Android 12+ (API 31+) -->
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />

<!-- Necesario para scanning de dispositivos en Android < 12 -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

<!-- Sensores (no requieren permiso explícito, pero sí declaración de feature) -->
<uses-feature android:name="android.hardware.sensor.compass" android:required="true" />
<uses-feature android:name="android.hardware.sensor.accelerometer" android:required="true" />
```

> ⚠️ **Nota importante:** En Android 12+ (API 31), `BLUETOOTH_CONNECT` es un **permiso peligroso** que debe solicitarse en runtime. La `MainActivity` debe incluir lógica de `ActivityResultContracts.RequestMultiplePermissions`.
