package com.pebete.astrotracker.data.catalog

import androidx.annotation.StringRes
import com.pebete.astrotracker.R
import kotlin.math.cos

enum class ObjectType { SOLAR_SYSTEM, DEEP_SPACE, STAR }

/** De dónde sale la posición del objeto. */
sealed interface SkySource {
    /** Coordenadas casi fijas (estrellas, cielo profundo) en J2000. */
    data class Fixed(val raHours: Double, val decDeg: Double) : SkySource
    data object Moon : SkySource
    data class PlanetSource(val planet: Ephemeris.Planet) : SkySource
}

/**
 * Objeto del catálogo. No guarda Az/Alt: esos valores cambian con la hora y el lugar, así que se calculan
 * al momento con [CelestialCatalog.horizontalOf]. Los textos viven en strings.xml.
 */
data class CelestialObject(
    val id: String,
    @param:StringRes val nameRes: Int,
    val type: ObjectType,
    @param:StringRes val constellationRes: Int?,
    @param:StringRes val descriptionRes: Int,
    val source: SkySource,
    val magnitude: Float,
    /** true para Luna y planetas, cuyo brillo varía con la fase/distancia. */
    val magnitudeIsTypical: Boolean = false
)

object CelestialCatalog {

    val objects: List<CelestialObject> = listOf(
        // ---- Sistema solar ----
        CelestialObject(
            "moon", R.string.obj_moon, ObjectType.SOLAR_SYSTEM, null, R.string.desc_moon,
            SkySource.Moon, -12.7f, true
        ),
        CelestialObject(
            "venus", R.string.obj_venus, ObjectType.SOLAR_SYSTEM, null, R.string.desc_venus,
            SkySource.PlanetSource(Ephemeris.Planet.VENUS), -4.1f, true
        ),
        CelestialObject(
            "mars", R.string.obj_mars, ObjectType.SOLAR_SYSTEM, null, R.string.desc_mars,
            SkySource.PlanetSource(Ephemeris.Planet.MARS), 0.7f, true
        ),
        CelestialObject(
            "jupiter", R.string.obj_jupiter, ObjectType.SOLAR_SYSTEM, null, R.string.desc_jupiter,
            SkySource.PlanetSource(Ephemeris.Planet.JUPITER), -2.2f, true
        ),
        CelestialObject(
            "saturn", R.string.obj_saturn, ObjectType.SOLAR_SYSTEM, null, R.string.desc_saturn,
            SkySource.PlanetSource(Ephemeris.Planet.SATURN), 0.7f, true
        ),

        // ---- Espacio profundo ----
        CelestialObject(
            "m42", R.string.obj_m42, ObjectType.DEEP_SPACE, R.string.con_orion, R.string.desc_m42,
            SkySource.Fixed(5.5881, -5.3911), 4.0f
        ),
        CelestialObject(
            "m45", R.string.obj_m45, ObjectType.DEEP_SPACE, R.string.con_taurus, R.string.desc_m45,
            SkySource.Fixed(3.7900, 24.1167), 1.6f
        ),
        CelestialObject(
            "m31", R.string.obj_m31, ObjectType.DEEP_SPACE, R.string.con_andromeda, R.string.desc_m31,
            SkySource.Fixed(0.7123, 41.2692), 3.4f
        ),
        CelestialObject(
            "omega_cen", R.string.obj_omega_cen, ObjectType.DEEP_SPACE, R.string.con_centaurus, R.string.desc_omega_cen,
            SkySource.Fixed(13.4464, -47.4794), 3.9f
        ),
        CelestialObject(
            "lmc", R.string.obj_lmc, ObjectType.DEEP_SPACE, R.string.con_dorado, R.string.desc_lmc,
            SkySource.Fixed(5.3933, -69.7500), 0.9f
        ),
        CelestialObject(
            "smc", R.string.obj_smc, ObjectType.DEEP_SPACE, R.string.con_tucana, R.string.desc_smc,
            SkySource.Fixed(0.8783, -72.8000), 2.7f
        ),

        // ---- Estrellas ----
        CelestialObject(
            "sirius", R.string.obj_sirius, ObjectType.STAR, R.string.con_canis_major, R.string.desc_sirius,
            SkySource.Fixed(6.7525, -16.7161), -1.46f
        ),
        CelestialObject(
            "canopus", R.string.obj_canopus, ObjectType.STAR, R.string.con_carina, R.string.desc_canopus,
            SkySource.Fixed(6.3992, -52.6956), -0.74f
        ),
        CelestialObject(
            "alpha_cen", R.string.obj_alpha_cen, ObjectType.STAR, R.string.con_centaurus, R.string.desc_alpha_cen,
            SkySource.Fixed(14.6601, -60.8339), -0.27f
        ),
        CelestialObject(
            "arcturus", R.string.obj_arcturus, ObjectType.STAR, R.string.con_bootes, R.string.desc_arcturus,
            SkySource.Fixed(14.2610, 19.1825), -0.05f
        ),
        CelestialObject(
            "vega", R.string.obj_vega, ObjectType.STAR, R.string.con_lyra, R.string.desc_vega,
            SkySource.Fixed(18.6157, 38.7836), 0.03f
        ),
        CelestialObject(
            "rigel", R.string.obj_rigel, ObjectType.STAR, R.string.con_orion, R.string.desc_rigel,
            SkySource.Fixed(5.2423, -8.2017), 0.13f
        ),
        CelestialObject(
            "betelgeuse", R.string.obj_betelgeuse, ObjectType.STAR, R.string.con_orion, R.string.desc_betelgeuse,
            SkySource.Fixed(5.9195, 7.4070), 0.42f
        ),
        CelestialObject(
            "achernar", R.string.obj_achernar, ObjectType.STAR, R.string.con_eridanus, R.string.desc_achernar,
            SkySource.Fixed(1.6286, -57.2367), 0.46f
        ),
        CelestialObject(
            "antares", R.string.obj_antares, ObjectType.STAR, R.string.con_scorpius, R.string.desc_antares,
            SkySource.Fixed(16.4901, -26.4320), 1.06f
        ),
        CelestialObject(
            "polaris", R.string.obj_polaris, ObjectType.STAR, R.string.con_ursa_minor, R.string.desc_polaris,
            SkySource.Fixed(2.5303, 89.2642), 1.98f
        )
    )

    /** Az/Alt del objeto en el instante [jd] para un observador en ([latDeg], [lonDeg]). */
    fun horizontalOf(obj: CelestialObject, jd: Double, latDeg: Double, lonDeg: Double): Horizontal =
        when (val src = obj.source) {
            is SkySource.Fixed -> {
                val eq = SkyCalculator.precessFromJ2000(Equatorial(src.raHours * 15.0, src.decDeg), jd)
                SkyCalculator.equatorialToHorizontal(eq, jd, latDeg, lonDeg)
            }

            is SkySource.PlanetSource -> {
                val eq = SkyCalculator.precessFromJ2000(Ephemeris.planet(src.planet, jd), jd)
                SkyCalculator.equatorialToHorizontal(eq, jd, latDeg, lonDeg)
            }

            SkySource.Moon -> {
                // La fórmula de la Luna ya es "de la fecha": no se precesa. Corregimos la paralaje (~1°).
                val h = SkyCalculator.equatorialToHorizontal(Ephemeris.moon(jd), jd, latDeg, lonDeg)
                val alt = h.altitudeDeg - Ephemeris.moonParallaxDeg(jd) * cos(Math.toRadians(h.altitudeDeg))
                Horizontal(h.azimuthDeg, alt)
            }
        }
}
