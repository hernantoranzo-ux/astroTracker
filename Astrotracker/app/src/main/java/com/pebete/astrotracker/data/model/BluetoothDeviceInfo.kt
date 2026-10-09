package com.pebete.astrotracker.data.model

/**
 * Datos mínimos de un dispositivo Bluetooth emparejado, para mostrar en la lista de selección.
 * Usamos esto en lugar de BluetoothDevice para que la UI no dependa de clases del framework.
 */
data class BluetoothDeviceInfo(
    val name: String,
    val macAddress: String
)
