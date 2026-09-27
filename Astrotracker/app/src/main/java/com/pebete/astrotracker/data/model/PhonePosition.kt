package com.pebete.astrotracker.data.model
data class PhonePosition(
    val azumith: Float,
    val altitude: Float,
    val timestamp: Long = System.currentTimeMillis()
)