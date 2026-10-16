package com.pebete.astrotracker.data.model

/**
 * Posición del teléfono calculada a partir del sensor ROTATION_VECTOR.
 * @param azimuth  Rumbo en grados [0, 360)
 * @param altitude Inclinación respecto al horizonte en grados [-90, 90]
 * @param timestamp Momento de la lectura (ms). Sirve para descartar lecturas viejas.
 */
data class PhonePosition(
    val azimuth: Float,
    val altitude: Float,
    val timestamp: Long = System.currentTimeMillis()
)