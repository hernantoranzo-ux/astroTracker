package com.pebete.astrotracker.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SkyCalculatorTest {

    private fun jd(iso: String) = SkyCalculator.julianDay(Instant.parse(iso).toEpochMilli())

    @Test
    fun julianDay_j2000() {
        assertEquals(2451545.0, jd("2000-01-01T12:00:00Z"), 1e-6)
    }

    @Test
    fun objectOnMeridianAtZenith() {
        val lat = -34.6; val lon = -58.4
        val t = jd("2026-03-10T03:00:00Z")
        val lst = SkyCalculator.localSiderealDeg(t, lon)
        val h = SkyCalculator.equatorialToHorizontal(Equatorial(lst, lat), t, lat, lon)
        assertEquals(90.0, h.altitudeDeg, 1e-6)
    }

    @Test
    fun meridianTransit_south_and_north() {
        val lat = -34.6; val lon = -58.4
        val t = jd("2026-03-10T03:00:00Z")
        val lst = SkyCalculator.localSiderealDeg(t, lon)

        // Dec más al sur que el observador: culmina al Sur
        val south = SkyCalculator.equatorialToHorizontal(Equatorial(lst, -60.0), t, lat, lon)
        assertEquals(180.0, south.azimuthDeg, 1e-6)
        assertEquals(64.6, south.altitudeDeg, 1e-6)

        // Dec 0: culmina al Norte
        val north = SkyCalculator.equatorialToHorizontal(Equatorial(lst, 0.0), t, lat, lon)
        assertEquals(0.0, north.azimuthDeg, 1e-6)
        assertEquals(55.4, north.altitudeDeg, 1e-6)
    }

    @Test
    fun eastOfMeridian_isEasternAzimuth() {
        val lat = -34.6; val lon = -58.4
        val t = jd("2026-03-10T03:00:00Z")
        val lst = SkyCalculator.localSiderealDeg(t, lon)
        // AR mayor que el LST => ángulo horario negativo => objeto al Este (azimut entre 0 y 180)
        val h = SkyCalculator.equatorialToHorizontal(Equatorial(lst + 40.0, 0.0), t, lat, lon)
        assertTrue("azimut=${h.azimuthDeg}", h.azimuthDeg in 0.0..180.0)
    }

    private fun eclipticLonDeg(eq: Equatorial): Double {
        val eps = Math.toRadians(23.43928)
        val ra = Math.toRadians(eq.raDeg)
        val dec = Math.toRadians(eq.decDeg)
        return SkyCalculator.normalize360(
            Math.toDegrees(Math.atan2(Math.sin(ra) * Math.cos(eps) + Math.tan(dec) * Math.sin(eps), Math.cos(ra)))
        )
    }

    @Test
    fun moonFullMoon_isOppositeTheSun() {
        // Luna llena = longitudes eclípticas opuestas (la latitud lunar, de hasta ~5°, no cuenta)
        val t = jd("2025-01-13T22:27:00Z")
        val diff = SkyCalculator.normalize360(eclipticLonDeg(Ephemeris.moon(t)) - eclipticLonDeg(Ephemeris.sun(t)))
        assertTrue("diferencia de longitud=$diff", Math.abs(diff - 180.0) < 1.5)
    }


    @Test
    fun planetsAtOpposition_areOppositeTheSun() {
        fun sep(planet: Ephemeris.Planet, iso: String): Double {
            val t = jd(iso)
            return SkyCalculator.angularSeparationDeg(Ephemeris.planet(planet, t), Ephemeris.sun(t))
        }
        val jupiter = sep(Ephemeris.Planet.JUPITER, "2024-12-07T00:00:00Z")
        val mars = sep(Ephemeris.Planet.MARS, "2025-01-16T00:00:00Z")
        val saturn = sep(Ephemeris.Planet.SATURN, "2025-09-21T00:00:00Z")
        assertTrue("Júpiter=$jupiter", jupiter > 175.0)
        assertTrue("Marte=$mars", mars > 174.0)
        assertTrue("Saturno=$saturn", saturn > 175.0)
    }

    @Test
    fun planetDistancesAreSane() {
        // Venus nunca se aleja del Sol más de ~47°
        val t = jd("2026-10-21T00:00:00Z")
        val venusElong = SkyCalculator.angularSeparationDeg(Ephemeris.planet(Ephemeris.Planet.VENUS, t), Ephemeris.sun(t))
        assertTrue("Venus=$venusElong", venusElong < 48.0)
    }
}
