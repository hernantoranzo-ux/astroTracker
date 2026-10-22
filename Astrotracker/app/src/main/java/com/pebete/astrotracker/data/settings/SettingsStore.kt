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
        const val KEY_USER_NAME = "user_name"
        const val DEFAULT_USER_NAME = "Astrónomo Amateur"
        const val KEY_SENSORS_OFF_BG = "sensors_off_in_background"
        const val KEY_LAT = "observer_lat"
        const val KEY_LON = "observer_lon"
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

    /** Nombre que se muestra en el menú lateral (editable en Configuración). Nunca queda vacío. */
    var userName: String
        get() = prefs.getString(KEY_USER_NAME, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_USER_NAME
        set(value) { prefs.edit().putString(KEY_USER_NAME, value.trim().ifEmpty { DEFAULT_USER_NAME }).apply() }

    /** Último pasos/grado calibrado; null = nunca se calibró desde la app. */
    var stepsPerDegree: Float?
        get() = if (prefs.contains(KEY_STEPS_PER_DEGREE)) prefs.getFloat(KEY_STEPS_PER_DEGREE, 10f) else null
        set(value) {
            // apply() guarda en segundo plano (no bloquea el hilo principal); commit() sí bloquearía
            if (value == null) prefs.edit().remove(KEY_STEPS_PER_DEGREE).apply()
            else prefs.edit().putFloat(KEY_STEPS_PER_DEGREE, value).apply()
        }

    /** Apagar los sensores del teléfono cuando la app pasa a segundo plano (ahorra batería). */
    var sensorsOffInBackground: Boolean
        get() = prefs.getBoolean(KEY_SENSORS_OFF_BG, true)
        set(value) { prefs.edit().putBoolean(KEY_SENSORS_OFF_BG, value).apply() }

    /** Ubicación del observador (grados). null = sin configurar. Se guardan como texto para no perder precisión. */
    var observerLat: Double?
        get() = prefs.getString(KEY_LAT, null)?.toDoubleOrNull()
        set(value) { prefs.edit().putString(KEY_LAT, value?.toString()).apply() }

    var observerLon: Double?
        get() = prefs.getString(KEY_LON, null)?.toDoubleOrNull()
        set(value) { prefs.edit().putString(KEY_LON, value?.toString()).apply() }
}
