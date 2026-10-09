package com.pebete.astrotracker.data.model

/**
 * Posición de los motores en PASOS, como la informa el Arduino al recibir el comando 'P'.
 * Formato del firmware: "<posX>,<posY>\n" (ej. "1200,-400").
 * stepsX = motor X (eje AR), stepsY = motor Y (eje DEC).
 */
data class MotorPosition(
    val stepsX: Int,
    val stepsY: Int
)
