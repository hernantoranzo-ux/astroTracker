package com.pebete.astrotracker.data.catalog

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Coordenadas ecuatoriales (AR y Dec) en grados. */
data class Equatorial(val raDeg: Double, val decDeg: Double)

/** Coordenadas horizontales: azimut medido desde el Norte hacia el Este [0, 360) y altitud [-90, 90]. */
data class Horizontal(val azimuthDeg: Double, val altitudeDeg: Double)

/**
 * Cálculos astronómicos básicos, sin dependencias de Android (se pueden probar con tests unitarios).
 * Precisión: décimas de grado, de sobra para apuntar un telescopio casero guiado por el teléfono.
 */
object SkyCalculator {

    const val J2000 = 2451545.0

    /** Día juliano a partir de milisegundos Unix (UTC). */
    fun julianDay(epochMs: Long): Double = epochMs / 86_400_000.0 + 2_440_587.5

    fun normalize360(deg: Double): Double {
        val r = deg % 360.0
        return if (r < 0) r + 360.0 else r
    }

    /** Tiempo sidéreo local en grados. Longitud positiva al Este. */
    fun localSiderealDeg(jd: Double, lonDeg: Double): Double =
        normalize360(280.46061837 + 360.98564736629 * (jd - J2000) + lonDeg)

    /**
     * Precesión aproximada (primer orden) de coordenadas J2000 a la fecha [jd].
     * Sin esto, en 2026 habría ~0,35° de error en las coordenadas de estrellas y objetos de cielo profundo.
     */
    fun precessFromJ2000(eq: Equatorial, jd: Double): Equatorial {
        val years = (jd - J2000) / 365.25
        val ra = Math.toRadians(eq.raDeg)
        val dec = Math.toRadians(eq.decDeg)
        val m = 0.012812   // grados/año
        val n = 0.005567   // grados/año
        val dRa = m + n * sin(ra) * tan(dec)
        val dDec = n * cos(ra)
        return Equatorial(normalize360(eq.raDeg + dRa * years), (eq.decDeg + dDec * years).coerceIn(-90.0, 90.0))
    }

    /** Convierte AR/Dec (de la fecha) a azimut/altitud para un observador en ([latDeg], [lonDeg]). */
    fun equatorialToHorizontal(eq: Equatorial, jd: Double, latDeg: Double, lonDeg: Double): Horizontal {
        val h = Math.toRadians(localSiderealDeg(jd, lonDeg) - eq.raDeg)
        val dec = Math.toRadians(eq.decDeg)
        val lat = Math.toRadians(latDeg)

        val sinAlt = sin(dec) * sin(lat) + cos(dec) * cos(lat) * cos(h)
        val alt = asin(sinAlt.coerceIn(-1.0, 1.0))
        // atan2 da el azimut medido desde el Sur hacia el Oeste; sumamos 180° para pasar a Norte->Este
        val az = atan2(sin(h), cos(h) * sin(lat) - tan(dec) * cos(lat)) + Math.PI

        return Horizontal(normalize360(Math.toDegrees(az)), Math.toDegrees(alt))
    }

    /** Separación angular (grados) entre dos posiciones ecuatoriales. */
    fun angularSeparationDeg(a: Equatorial, b: Equatorial): Double {
        val ra1 = Math.toRadians(a.raDeg); val d1 = Math.toRadians(a.decDeg)
        val ra2 = Math.toRadians(b.raDeg); val d2 = Math.toRadians(b.decDeg)
        val c = sin(d1) * sin(d2) + cos(d1) * cos(d2) * cos(ra1 - ra2)
        return Math.toDegrees(kotlin.math.acos(c.coerceIn(-1.0, 1.0)))
    }
}
