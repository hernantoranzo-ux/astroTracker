package com.pebete.astrotracker.data.model
data class TelemetryData(
        val azActual:   Float,
        val altActual:  Float,
        val errorAz:    Float,
        val isTracking: Boolean
        )