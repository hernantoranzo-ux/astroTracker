# 02 — Diagrama de Máquina de Estados (UML State Machine)

> Modela el comportamiento del sistema completo: firmware Arduino + App Android.

---

## 2.1 Máquina de Estados — Firmware Arduino

```mermaid
stateDiagram-v2
    [*] --> Inicializando

    Inicializando --> ModoManual : setup() completo

    state ModoManual {
        [*] --> EsperandoComando
        EsperandoComando --> EjecutandoMovimiento : recibe F/B/R/L
        EjecutandoMovimiento --> EsperandoComando  : motorX/Y.run() completa el movimiento
        EsperandoComando --> EsperandoComando      : recibe Z, M, P, +, -
    }

    ModoManual --> ModoAutomatico : recibe char 'a'

    state ModoAutomatico {
        [*] --> Apuntando

        state Apuntando {
            [*] --> CalculandoError
            CalculandoError --> CorrigiendoPosicion : errorAz o errorAlt != 0
            CorrigiendoPosicion --> CalculandoError : motores en movimiento
        }

        Apuntando --> SeguimientoActivo   : recibe TRACK:ON
        SeguimientoActivo --> Apuntando   : recibe TRACK:OFF

        state SeguimientoActivo {
            [*] --> GirandoSideral
            GirandoSideral --> GirandoSideral : motorX.runSpeed() (bucle continuo)
        }

        Apuntando --> Calibrando          : recibe CAL:START
        SeguimientoActivo --> Calibrando  : recibe CAL:START

        state Calibrando {
            [*] --> Moviendo90Grados
            Moviendo90Grados --> EsperandoValorReal : movimiento completo
            EsperandoValorReal --> AplicandoCalibrado : recibe CAL:<float>
            AplicandoCalibrado --> [*]
        }

        Calibrando --> Apuntando : calibración aplicada
    }

    ModoAutomatico --> ModoManual : recibe "MANUAL\n"

    note right of ModoManual
        Telemetría emitida cada 500 ms:
        DATA:azActual,altActual,errorAz,tracking
        (independiente del modo activo)
    end note

    note right of ModoAutomatico
        AZ_TEL y ALT_TEL actualizan
        las variables azActual y altActual
        usadas en el cálculo de error.
    end note
```

**Notas de comportamiento:**
- En `Apuntando`: el Arduino recibe `AZ_TEL`/`ALT_TEL` para calcular el error angular y mueve los motores.
- En `Tracking`: el motor AR gira a velocidad sideral constante (`motorX.runSpeed()`).
- En `Calibrando`: el motor AR se mueve 90° teóricos; al recibir `CAL:<valor>` actualiza `pasosPorGrado`.

---

## 2.2 Máquina de Estados — App Android (Conexión)

> El primer nivel del ciclo de vida de la app: gestión de la conexión Bluetooth.

```mermaid
stateDiagram-v2
    direction LR

    [*] --> Desconectada

    Desconectada --> Conectando         : Botón Conectar\n+ dispositivo seleccionado

    state Conectando {
        [*] --> Abriendo_Socket
        Abriendo_Socket --> Enlazando   : socket creado
        Enlazando --> [*]               : socket.connect() OK
    }

    Conectando --> Desconectada         : IOException\n→ Snackbar de error
    Conectando --> ModoManual           : Conexión exitosa\n+ lector iniciado

    ModoManual --> ModoAutomatico       : Botón "Automático"\n→ envía 'a'
    ModoAutomatico --> ModoManual       : Botón "Manual"\n→ envía MANUAL

    ModoManual --> Desconectada         : Botón Desconectar\n/ IOException
    ModoAutomatico --> Desconectada     : Botón Desconectar\n/ IOException

    Desconectada --> [*]
```

---

## 2.3 Máquina de Estados — App Android (Operación por Modo)

> Detalle interno de los dos modos operativos una vez establecida la conexión.

```mermaid
stateDiagram-v2
    direction TB

    state ModoManual {
        [*] --> Escuchando_M
        Escuchando_M --> Enviando_Cmd   : Toca D-Pad / botón de config
        Enviando_Cmd --> Escuchando_M   : Byte enviado
        Escuchando_M --> Actualizando_M : DATA recibido (cada 500ms)
        Actualizando_M --> Escuchando_M : UI actualizada
    }

    state ModoAutomatico {
        [*] --> Operando
        Operando --> Apuntando_A        : Botón "Apuntar"\n→ envía AZ_OBJ / ALT_OBJ
        Apuntando_A --> Operando        : Comandos enviados

        Operando --> TrackingON         : Toggle Tracking ON\n→ envía TRACK:ON
        TrackingON --> Operando         : Toggle Tracking OFF\n→ envía TRACK:OFF

        Operando --> Calibrando_A       : Botón "Calibrar"
        state Calibrando_A {
            [*] --> Enviando_CAL_START
            Enviando_CAL_START --> Midiendo : CAL:START enviado\n(motor se mueve 90°)
            Midiendo --> Aplicando      : Usuario ingresa medición
            Aplicando --> [*]           : CAL:valor enviado
        }
        Calibrando_A --> Operando       : Calibración completa

        Operando --> Actualizando_A     : DATA recibido (cada 500ms)
        Actualizando_A --> Operando     : Panel de telemetría actualizado
    }
```

**Nota — Sensores en cada modo:**
| Modo | Frecuencia | Enviado al Arduino |
|------|:----------:|--------------------|
| Manual | `SENSOR_DELAY_UI` (~60 ms) | ❌ Solo visualización en pantalla |
| Automático | `SENSOR_DELAY_NORMAL` (~200 ms) | ✅ `AZ_TEL:<val>` y `ALT_TEL:<val>` |

---

## 2.4 Ciclo de Vida de los Sensores del Teléfono

```mermaid
stateDiagram-v2
    [*] --> Inactivo

    Inactivo --> RegistradoBajaFrec  : app entra en Modo Manual\n(Activity/Fragment en foreground)
    RegistradoBajaFrec --> Inactivo  : app va a background / se desconecta

    RegistradoBajaFrec --> RegistradoAltaFrec : app entra en Modo Automático
    RegistradoAltaFrec --> RegistradoBajaFrec : app vuelve a Modo Manual
    RegistradoAltaFrec --> Inactivo           : app va a background / se desconecta

    state RegistradoBajaFrec {
        [*] --> Midiendo_UI
        Midiendo_UI --> Emitiendo_Flow_UI : nuevo evento de sensor
        Emitiendo_Flow_UI --> Midiendo_UI
        note right of Midiendo_UI
            SENSOR_DELAY_UI
            Solo actualiza pantalla.
            No envía al Arduino.
        end note
    }

    state RegistradoAltaFrec {
        [*] --> Midiendo_Auto
        Midiendo_Auto --> Emitiendo_Flow_Auto : nuevo evento de sensor
        Emitiendo_Flow_Auto --> Midiendo_Auto
        note right of Midiendo_Auto
            SENSOR_DELAY_NORMAL
            Actualiza pantalla Y
            envía al Arduino.
        end note
    }
```

---

## 2.5 Tabla de Transiciones Consolidada

| Estado Origen (App) | Evento / Condición | Estado Destino | Acción |
|---------------------|--------------------|----------------|--------|
| Desconectada | Botón Conectar + dispositivo válido | Conectando | Abre coroutine de conexión en IO |
| Conectando | `socket.connect()` exitoso | ModoManual | Inicia lector + sensores baja freq |
| Conectando | `IOException` | Desconectada | Snackbar error, libera socket |
| ModoManual | Botón "Automático" | ModoAutomatico | Envía `'a'`, sube freq sensores |
| ModoAutomatico | Botón "Manual" | ModoManual | Envía `"MANUAL"`, baja freq sensores |
| Cualquier modo conectado | `IOException` en read() | Desconectada | Cierra socket, emite estado |
| Cualquier modo conectado | Botón Desconectar | Desconectada | Cierra socket limpiamente |
| ModoManual | Timeout telemetría > 2 s | ModoManual | Alerta visual (sin cambio de estado) |
| ModoAutomatico | Toggle Tracking → ON | TrackingON | Envía `"TRACK:ON"` |
| TrackingON | Toggle Tracking → OFF | Operando | Envía `"TRACK:OFF"` |
| Operando | Botón "Calibrar" | Calibrando_A | Envía `"CAL:START"` |
| Calibrando_A | Usuario ingresa medición | Aplicando | Calcula y envía `"CAL:<val>"` |
