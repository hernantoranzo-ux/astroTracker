package com.pebete.astrotracker.data.settings

import android.content.Context

/**
 * Guarda preferencias simples en el teléfono con SharedPreferences (un archivo XML clave/valor,
 * el equivalente directo de Java: mismo API).
 *
 * Opción B de "pasosPorGrado": el firmware NO nos deja leer el valor, así que la app guarda el último
 * valor calibrado y lo reenvía (CAL:<v>) en cada conexión. Si el equipo de firmware agrega un comando
 * para leerlo (ej. EEPROM), alcanza con cambiar esta clase y el arranque de AstroRepository.
 *
 * Usamos applicationContext: así nunca retenemos una Activity (anti memory-leak).
 */
class SettingsStore(context: Context) {

    private companion object {
        const val FILE = "astrotracker_prefs"
        const val KEY_LAST_MAC = "last_device_mac"
        const val KEY_AUTO_RECONNECT = "auto_reconnect"
        const val KEY_RED_MODE = "red_night_mode"
        const val KEY_STEPS_PER_DEGREE = "steps_per_degree"
    }

    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** Última MAC conectada con éxito (para preseleccionarla y para reconectar). */
    var lastDeviceMac: String?
        get() = prefs.getString(KEY_LAST_MAC, null)
        set(value) { prefs.edit().putString(KEY_LAST_MAC, value).apply() }

    /** Reintentar la conexión (limitado) si se corta sola. */
    var autoReconnect: Boolean
        get() = prefs.getBoolean(KEY_AUTO_RECONNECT, true)
        set(value) { prefs.edit().putBoolean(KEY_AUTO_RECONNECT, value).apply() }

    /** Filtro rojo para preservar la visión nocturna. */
    var redNightMode: Boolean
        get() = prefs.getBoolean(KEY_RED_MODE, false)
        set(value) { prefs.edit().putBoolean(KEY_RED_MODE, value).apply() }

    /** Último pasos/grado calibrado; null = nunca se calibró desde la app. */
    var stepsPerDegree: Float?
        get() = if (prefs.contains(KEY_STEPS_PER_DEGREE)) prefs.getFloat(KEY_STEPS_PER_DEGREE, 10f) else null
        set(value) {
            // apply() guarda en segundo plano (no bloquea el hilo principal); commit() sí bloquearía
            if (value == null) prefs.edit().remove(KEY_STEPS_PER_DEGREE).apply()
            else prefs.edit().putFloat(KEY_STEPS_PER_DEGREE, value).apply()
        }
}
