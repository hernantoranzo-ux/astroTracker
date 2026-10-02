# 01 — ICD: Contrato de Interfaz App ↔ Arduino (Astrotracker)

> **Interface Control Document (ICD) — Versión 1.0**
> Basado en: `Prototipo_arduino.ino` v1.5 (29/03/2026)

---

## 1. Canal de Comunicación

| Parámetro | Valor |
|-----------|-------|
| Hardware | Módulo Bluetooth HC-05 |
| Perfil Bluetooth | SPP (Serial Port Profile) — Bluetooth Clásico |
| UUID SPP | `00001101-0000-1000-8000-00805F9B34FB` |
| Velocidad serial (baud rate) | **9600 bps** |
| Conexión en Arduino | SoftwareSerial: RX=Pin10, TX=Pin11 |
| Nivel lógico Arduino | 3.3 V / TTL, con divisor resistivo hacia HC-05 |
| Flujo de control | Sin flow control (ni RTS/CTS) |

---

## 2. Convenciones Generales

- **Encoding:** ASCII puro (no UTF-8 extendido).
- **Delimitador de mensaje:** Ver por modo (Tabla 2 y 3).
- **Float:** Punto decimal anglosajón (`.`), sin separador de miles.  
  *Ejemplo correcto:* `120.50` / *Incorrecto:* `120,50`.
- **Booleano en telemetría:** `1` = verdadero, `0` = falso.
- **Timeout de lectura recomendado (App):** Si no se recibe ningún paquete `DATA:` en **2000 ms**, la app debe emitir una alerta visual de "Sin respuesta del telescopio".

---

## 3. Dirección App → Arduino

### 3.1 MODO MANUAL

> El Arduino lee carácter a carácter (`bluetoothSerial.read()`).  
> **No usa salto de línea como delimitador.** Cada byte es un comando completo.

| Comando (byte) | Acción en Arduino | Motor afectado | Pasos |
|:--------------:|-------------------|:--------------:|:-----:|
| `F` | Avanzar (DEC +) | Motor Y (DEC) | +200 |
| `B` | Retroceder (DEC −) | Motor Y (DEC) | −200 |
| `R` | Derecha (AR +) | Motor X (AR) | +200 |
| `L` | Izquierda (AR −) | Motor X (AR) | −200 |
| `Z` | Guardar posición actual como cero de referencia | X e Y | — |
| `M` | Guardar posición actual como límite máximo | X e Y | — |
| `P` | Solicitar posición actual | X e Y | — |
| `+` | Incrementar velocidad en 100 pasos/s | X e Y | — |
| `-` | Decrementar velocidad en 100 pasos/s (mín: 100) | X e Y | — |
| `a` | **Transición a MODO AUTOMÁTICO** | — | — |

> **Nota de mapeo físico:** Según el firmware, el Motor X (pines STEP=2, DIR=5 de la CNC Shield) corresponde al eje de **AR (Ascensión Recta)**, y el Motor Y (STEP=3, DIR=6) al eje de **DEC (Declinación)**.

#### Respuesta del Arduino en Modo Manual

- Al recibir `P`: emite `"<posX>,<posY>\n"` (ej. `"1200,-400\n"`)
- Los demás comandos son silenciosos (sin ACK por defecto).

---

### 3.2 MODO AUTOMÁTICO

> El Arduino lee mensajes completos terminados en `\n` (`readStringUntil('\n')`).  
> **Todos los mensajes deben terminar en el carácter `\n` (LF, 0x0A).**

| Mensaje | Descripción | Tipo de valor | Ejemplo |
|---------|-------------|:-------------:|---------|
| `AZ_OBJ:<float>\n` | Azimut del objeto a apuntar | `Float` [0, 360) | `AZ_OBJ:152.75\n` |
| `ALT_OBJ:<float>\n` | Altitud del objeto a apuntar | `Float` [−90, 90] | `ALT_OBJ:42.30\n` |
| `AZ_TEL:<float>\n` | Azimut actual del teléfono (brújula) | `Float` [0, 360) | `AZ_TEL:148.20\n` |
| `ALT_TEL:<float>\n` | Altitud actual del teléfono (acelerómetro) | `Float` [−90, 90] | `ALT_TEL:40.10\n` |
| `TRACK:ON\n` | Activar seguimiento sideral continuo | — | `TRACK:ON\n` |
| `TRACK:OFF\n` | Desactivar seguimiento sideral | — | `TRACK:OFF\n` |
| `VEL:<float>\n` | Ajustar velocidad sideral (pasos/s) | `Float` > 0 | `VEL:75.0\n` |
| `CAL:START\n` | Iniciar calibración (mueve 90° teóricos) | — | `CAL:START\n` |
| `CAL:<float>\n` | Enviar nuevo valor de pasosPorGrado | `Float` > 0 | `CAL:22.75\n` |
| `MANUAL\n` | **Transición a MODO MANUAL** | — | `MANUAL\n` |

---

## 4. Dirección Arduino → App (Telemetría)

El Arduino emite **incondicionalmente cada 500 ms** un paquete de telemetría, independientemente del modo activo.

### Formato del Paquete

```
DATA:<azActual>,<altActual>,<errorAz>,<tracking>\n
```

| Campo | Tipo | Descripción | Ejemplo |
|-------|------|-------------|---------|
| `azActual` | `Float` | Azimut leído del teléfono (reflejado desde `AZ_TEL`) | `120.50` |
| `altActual` | `Float` | Altitud leída del teléfono (reflejada desde `ALT_TEL`) | `45.20` |
| `errorAz` | `Float` | Diferencia `azObjetivo − azActual` | `1.15` |
| `tracking` | `Int` (0/1) | Estado del seguimiento sideral | `1` |

**Ejemplo de trama completa:**
```
DATA:120.50,45.20,1.15,1\n
```

> 📌 **Nota de diseño:** El Arduino actualmente refleja en la telemetría los últimos valores que la app le envió como `AZ_TEL` / `ALT_TEL`. No posee sus propios sensores. La "posición angular real del telescopio" no se transmite directamente; el error angular es calculado en el firmware.

---

## 5. Lectura de Sensores del Teléfono

La app Android es la responsable de leer los sensores de posición del dispositivo móvil.

| Parámetro | Sensor Android usado | API Android |
|-----------|---------------------|-------------|
| Azimut (rumbo magnético) | `Sensor.TYPE_ROTATION_VECTOR` | `SensorManager.getOrientation()` |
| Altitud (ángulo de inclinación) | `Sensor.TYPE_ROTATION_VECTOR` | `SensorManager.getOrientation()` |

> 📌 Se prefiere `TYPE_ROTATION_VECTOR` sobre `TYPE_MAGNETIC_FIELD` + `TYPE_ACCELEROMETER` por separado, ya que incorpora fusión de sensores del SO y es más estable ante vibraciones.

### Frecuencia de Actualización por Modo

| Modo | `SensorManager.SENSOR_DELAY_*` | Intervalo aprox. | Enviado al Arduino |
|------|-------------------------------|-------------------|--------------------|
| **Automático** | `SENSOR_DELAY_NORMAL` | ~200 ms | Sí, en cada ciclo de envío |
| **Manual** | `SENSOR_DELAY_UI` | ~60 ms (solo visualización) | No se envía al Arduino |

> En Modo Manual, los datos de sensor **se muestran en pantalla** para que el operador tenga orientación visual pero **no se transmiten por Bluetooth**, ya que el Arduino en ese modo procesa comandos char a char y no tiene lógica de posición.

---

## 6. Máquina de Estados del Protocolo (Resumen)

```
[Desconectado]
    │  (usuario selecciona dispositivo y presiona "Conectar")
    ▼
[Conectando...]
    │  (socket SPP establecido)
    ▼
[Conectado — MODO MANUAL]   ◄─────────────────────────────┐
    │   Comandos: F B R L Z M P + -                        │
    │   Sensores: visualización en pantalla (baja freq)    │
    │                                                      │
    │   (usuario envía 'a')                               │
    ▼                                                      │
[Conectado — MODO AUTOMÁTICO]                              │
    │   Comandos: AZ_OBJ ALT_OBJ AZ_TEL ALT_TEL           │
    │             TRACK:ON/OFF  VEL  CAL:START  CAL:val    │
    │   Sensores: enviados al Arduino (freq normal)        │
    │                                                      │
    ├──► [Seguimiento ACTIVO]  (TRACK:ON enviado)          │
    │       Motor AR girando a velocidad sideral           │
    │                                                      │
    ├──► [Calibración en curso]  (CAL:START enviado)       │
    │       Motor AR se mueve 90° teóricos                 │
    │       Usuario mide y envía CAL:<valor>               │
    │                                                      │
    └──── (usuario envía MANUAL\n) ────────────────────────┘
```

---

## 7. Manejo de Errores y Robustez

| Escenario | Comportamiento Esperado en la App |
|-----------|----------------------------------|
| Paquete `DATA:` malformado (faltan campos) | Descartar el paquete, registrar en log, mantener último valor válido |
| `Float` no parseable | Descartar campo, mantener último valor válido |
| Timeout de telemetría (> 2000 ms sin `DATA:`) | Mostrar alerta "Sin respuesta. Verificar conexión" |
| Desconexión abrupta del Bluetooth | Cerrar socket, emitir `ConnectionState.DISCONNECTED`, mostrar aviso |
| Envío de comando cuando desconectado | La app debe ignorar el evento y mostrar un Snackbar informativo |
| Buffer overflow en lectura | El lector debe acumular por línea (`\n`) y descartar líneas > 128 bytes |
