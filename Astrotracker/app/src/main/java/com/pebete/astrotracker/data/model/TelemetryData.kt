package com.pebete.astrotracker.data.model

/**
 * Indica si la telemetría que estamos mostrando es "fresca" o "vieja".
 * FRESH -> llegó un paquete DATA: hace menos de 2 segundos.
 * STALE -> pasaron más de 2 segundos sin recibir nada (ICD sección 2 y 7): hay que avisar al usuario.
 */
enum class TelemetryState {
    FRESH,
    STALE
}

/**
 * Un "data class" es el equivalente a un POJO de Java, pero Kotlin te genera solo
 * equals(), hashCode(), toString() y copy(). Es inmutable (todo "val"): para "cambiar" un valor
 * se crea una copia con data.copy(state = STALE).
 */
data class TelemetryData(
    val azActual: Float,
    val altActual: Float,
    val errorAz: Float,
    val isTracking: Boolean,
    // Valor por defecto: así CommandProtocol.parseTelemetry() no necesita pasarlo (siempre nace FRESH)
    val state: TelemetryState = TelemetryState.FRESH
)