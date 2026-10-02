package com.pebete.astrotracker.protocol

import com.pebete.astrotracker.data.model.TelemetryData
import java.util.Locale
import java.util.UUID


object CommandProtocol {
    /* Definimos una constante con identificador único para la conexión
       entre el teléfono y el módulo Bluetooth HC-05 */
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    /* UUID.fromString toma el texto y lo convierte en un objeto de clase UUID para ser
       interpretado por las librerías de Gluetooth de Android */

    //Comandos del modo manual
    const val MANUAL_FORWARD = 'F'  //Avanzar DEC (+)
    const val MANUAL_BACK = 'B'     //Retroceder DEC (-)
    const val MANUAL_RIGHT = 'R'    //Derecha AR (+)
    const val MANUAL_LEFT = 'L'     //Izquierda AR (-)
    const val MANUAL_SET_ZERO = 'Z' //Guardar posición actual como cero
    const val MANUAL_SET_MAX = 'M'  //Guardar posición actual como límite máximo
    const val MANUAL_REQUEST_POS = 'P'  //Pedir posición actual al Arduino
    const val MANUAL_SPEED_UP = '+'     //Incrementar velocidad de movimiento manual
    const val MANUAL_SPEED_DOWN = '-'   //Decrementar velocidad de movimiento manual
    const val MANUAL_TO_AUTO = 'a'      //Cambiar a modo automático

    //Comandos del modo automático
    const val AUTO_TO_MANUAL = "MANUAL\n"   //Cambio a modo manual
    const val TRACK_ON = "TRACK:ON\n"       //Activar el seguimiento sideral
    const val TRACK_OFF = "TRACK:OFF\n"     //Desactivar el seguimeinto sideral
    const val CAL_START = "CAL:START\n"     //Iniciar calibración

    //Constructores de los mensajes automáticos
    //Usados para dejar definidas las conversiones de los valores Float a Strings
    //Locale.US para evitar comas
    fun buildAzObj(az: Float): String = String.format(Locale.US, "AZ_OBJ:%.2f\n", az)
    fun buildAltObj(alt: Float): String = String.format(Locale.US, "ALT_OBJ:%.2f\n", alt)
    fun buildAzTel(az: Float): String = String.format(Locale.US, "AZ_TEL:%.2f\n", az)
    fun buildAltTel(alt: Float): String = String.format(Locale.US, "ALT_TEL:%.2f\n", alt)
    fun buildVelocity(v: Float): String = String.format(Locale.US, "VEL:%.2f\n", v)
    fun buildCalValue(steps: Float): String = String.format(Locale.US, "CAL:%.2f\n", steps)

    // Parseo del paquete recibido del Arduino: DATA:<azActual>,<altActual>,<errorAz>,<tracking>\n
    fun parseTelemetry(line: String): TelemetryData? {
        return runCatching {
            val cleanLine = line.trim()
            if (!cleanLine.startsWith("DATA:")) return null

            val parts = cleanLine.removePrefix("DATA:").split(",")
            if (parts.size != 4) return null

            val az = parts[0].trim().toFloatOrNull() ?: return null
            val alt = parts[1].trim().toFloatOrNull() ?: return null
            val err = parts[2].trim().toFloatOrNull() ?: return null
            val tracking = parts[3].trim() == "1"

            TelemetryData(
                azActual = az,
                altActual = alt,
                errorAz = err,
                isTracking = tracking
            )
        }.getOrNull()

    }
}
