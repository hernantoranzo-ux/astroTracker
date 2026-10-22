package com.pebete.astrotracker.data.catalog

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Efemérides de BAJA precisión (suficiente para apuntar "a ojo" con un telescopio casero):
 * - Luna: fórmulas simplificadas del Astronomical Almanac (~0,3° de error).
 * - Planetas: elementos keplerianos aproximados de JPL (válidos 1800-2050), error del orden de minutos de arco.
 * No incluye aberración, nutación ni refracción.
 */
object Ephemeris {

    enum class Planet { VENUS, EARTH, MARS, JUPITER, SATURN }

    /** Elementos orbitales a J2000 y su variación por siglo juliano (a, e, I, L, ϖ, Ω). */
    private class Elements(
        val a: Double, val aDot: Double,
        val e: Double, val eDot: Double,
        val i: Double, val iDot: Double,
        val l: Double, val lDot: Double,
        val w: Double, val wDot: Double,
        val node: Double, val nodeDot: Double
    )

    private val elements = mapOf(
        Planet.VENUS to Elements(
            0.72333566, 0.00000390, 0.00677672, -0.00004107, 3.39467605, -0.00078890,
            181.97909950, 58517.81538729, 131.60246718, 0.00268329, 76.67984255, -0.27769418
        ),
        Planet.EARTH to Elements(
            1.00000261, 0.00000562, 0.01671123, -0.00004392, -0.00001531, -0.01294668,
            100.46457166, 35999.37244981, 102.93768193, 0.32327364, 0.0, 0.0
        ),
        Planet.MARS to Elements(
            1.52371034, 0.00001847, 0.09339410, 0.00007882, 1.84969142, -0.00813131,
            -4.55343205, 19140.30268499, -23.94362959, 0.44441088, 49.55953891, -0.29257343
        ),
        Planet.JUPITER to Elements(
            5.20288700, -0.00011607, 0.04838624, -0.00013253, 1.30439695, -0.00183714,
            34.39644051, 3034.74612775, 14.72847983, 0.21252668, 100.47390909, 0.20469106
        ),
        Planet.SATURN to Elements(
            9.53667594, -0.00125060, 0.05386179, -0.00050991, 2.48599187, 0.00193609,
            49.95424423, 1222.49362201, 92.59887831, -0.41897216, 113.66242448, -0.28867794
        )
    )

    private const val OBLIQUITY_J2000_DEG = 23.43928

    private data class Vec(val x: Double, val y: Double, val z: Double)

    /** Posición heliocéntrica (UA) en la eclíptica J2000. */
    private fun heliocentric(planet: Planet, jd: Double): Vec {
        val el = elements.getValue(planet)
        val t = (jd - SkyCalculator.J2000) / 36525.0

        val a = el.a + el.aDot * t
        val e = el.e + el.eDot * t
        val inc = Math.toRadians(el.i + el.iDot * t)
        val l = el.l + el.lDot * t
        val w = el.w + el.wDot * t
        val node = Math.toRadians(el.node + el.nodeDot * t)
        val argPeri = Math.toRadians(w) - node

        // Anomalía media en (-180°, 180°] y ecuación de Kepler resuelta por iteración
        var m = SkyCalculator.normalize360(l - w)
        if (m > 180.0) m -= 360.0
        val mRad = Math.toRadians(m)
        var ecc = mRad + e * sin(mRad)
        repeat(10) { ecc = mRad + e * sin(ecc) }

        val xp = a * (cos(ecc) - e)
        val yp = a * sqrt(1 - e * e) * sin(ecc)

        val cw = cos(argPeri); val sw = sin(argPeri)
        val cn = cos(node); val sn = sin(node)
        val ci = cos(inc); val si = sin(inc)

        return Vec(
            (cw * cn - sw * sn * ci) * xp + (-sw * cn - cw * sn * ci) * yp,
            (cw * sn + sw * cn * ci) * xp + (-sw * sn + cw * cn * ci) * yp,
            (sw * si) * xp + (cw * si) * yp
        )
    }

    private fun eclipticToEquatorial(v: Vec): Equatorial {
        val eps = Math.toRadians(OBLIQUITY_J2000_DEG)
        val x = v.x
        val y = v.y * cos(eps) - v.z * sin(eps)
        val z = v.y * sin(eps) + v.z * cos(eps)
        val r = sqrt(x * x + y * y + z * z)
        return Equatorial(
            SkyCalculator.normalize360(Math.toDegrees(atan2(y, x))),
            Math.toDegrees(asin(z / r))
        )
    }

    /** Posición geocéntrica del planeta (equinoccio J2000). */
    fun planet(planet: Planet, jd: Double): Equatorial {
        require(planet != Planet.EARTH) { "La Tierra no es un objetivo" }
        val p = heliocentric(planet, jd)
        val earth = heliocentric(Planet.EARTH, jd)
        return eclipticToEquatorial(Vec(p.x - earth.x, p.y - earth.y, p.z - earth.z))
    }

    /** Posición geocéntrica del Sol (equinoccio J2000). Se usa para tests y comprobaciones. */
    fun sun(jd: Double): Equatorial {
        val earth = heliocentric(Planet.EARTH, jd)
        return eclipticToEquatorial(Vec(-earth.x, -earth.y, -earth.z))
    }

    /** Posición geocéntrica de la Luna (equinoccio de la FECHA, no J2000). */
    fun moon(jd: Double): Equatorial {
        val t = (jd - SkyCalculator.J2000) / 36525.0
        fun s(d: Double) = sin(Math.toRadians(d))

        val lon = 218.32 + 481267.881 * t +
            6.29 * s(135.0 + 477198.87 * t) - 1.27 * s(259.3 - 413335.36 * t) +
            0.66 * s(235.7 + 890534.22 * t) + 0.21 * s(269.9 + 954397.74 * t) -
            0.19 * s(357.5 + 35999.05 * t) - 0.11 * s(186.5 + 966404.03 * t)
        val lat = 5.13 * s(93.3 + 483202.02 * t) + 0.28 * s(228.2 + 960400.89 * t) -
            0.28 * s(318.3 + 6003.15 * t) - 0.17 * s(217.6 - 407332.21 * t)

        val lonR = Math.toRadians(lon)
        val latR = Math.toRadians(lat)
        val l = cos(latR) * cos(lonR)
        val m = 0.9175 * cos(latR) * sin(lonR) - 0.3978 * sin(latR)
        val n = 0.3978 * cos(latR) * sin(lonR) + 0.9175 * sin(latR)

        return Equatorial(
            SkyCalculator.normalize360(Math.toDegrees(atan2(m, l))),
            Math.toDegrees(asin(n.coerceIn(-1.0, 1.0)))
        )
    }

    /** Paralaje horizontal de la Luna (grados, ~0,95°). Importa: desplaza la altitud casi 1°. */
    fun moonParallaxDeg(jd: Double): Double {
        val t = (jd - SkyCalculator.J2000) / 36525.0
        fun c(d: Double) = cos(Math.toRadians(d))
        return abs(
            0.9508 + 0.0518 * c(135.0 + 477198.87 * t) + 0.0095 * c(259.3 - 413335.36 * t) +
                0.0078 * c(235.7 + 890534.22 * t) + 0.0028 * c(269.9 + 954397.74 * t)
        )
    }
}
