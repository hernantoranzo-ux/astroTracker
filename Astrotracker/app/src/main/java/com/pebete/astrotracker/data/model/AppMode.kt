package com.pebete.astrotracker.data.model

/**
 * Modo de operación de la app. Debe reflejar el modo del Arduino:
 * MANUAL    -> el Arduino lee 1 caracter = 1 comando (F, B, R, L...)
 * AUTOMATIC -> el Arduino lee líneas "CLAVE:VALOR\n"
 */
enum class AppMode {
    MANUAL,
    AUTOMATIC
}
