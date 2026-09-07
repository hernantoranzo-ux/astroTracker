package com.example.myapplication // ⚠️ CAMBIALO POR TU PAQUETE REAL SI DA ERROR

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    // ============================================================
    // VARIABLES DE CONTROL BLUETOOTH (HC-05 / HC-06)
    // ============================================================
    private var bluetoothAdapter: android.bluetooth.BluetoothAdapter? = null
    private var bluetoothSocket: android.bluetooth.BluetoothSocket? = null
    private val BT_UUID = java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    // Cambiá esto por la dirección MAC real de tu módulo Bluetooth
    private val DIRECCION_MAC_HC05 = "00:21:13:01:47:E0"

    // ============================================================
    // ESTADOS DINÁMICOS PARA LA INTERFAZ (Resuelven tu error en rojo)
    // ============================================================
    var mostrarModoManual = androidx.compose.runtime.mutableStateOf(false)
    var azimutTelescopio = androidx.compose.runtime.mutableStateOf("0.0°")
    var alturaTelescopio = androidx.compose.runtime.mutableStateOf("0.0°")
    var errorSeguimiento = androidx.compose.runtime.mutableStateOf("0.0°")
    var estadoTracking = androidx.compose.runtime.mutableStateOf("Alineando")

    private var hiloEscucha: Thread? = null
    private var escuchando = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Aseguramos pasarle 'this' (esta misma actividad) a la pantalla
            PantallaPrincipalSeguidor(mainActivity = this)
        }
    }

    // ============================================================
    // FUNCIONES DE CONEXIÓN Y FLUJO DE DATOS
    // ============================================================
    fun conectarBluetooth() {
        bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        val dispositivo = bluetoothAdapter?.getRemoteDevice(DIRECCION_MAC_HC05)

        try {
            // Verificación rápida de permisos para entornos de desarrollo
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) == android.content.pm.PackageManager.PERMISSION_GRANTED || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) {
                bluetoothSocket = dispositivo?.createRfcommSocketToServiceRecord(BT_UUID)
                bluetoothSocket?.connect()
                android.widget.Toast.makeText(this, "¡Conectado al Astrotracker!", android.widget.Toast.LENGTH_SHORT).show()
                comenzarAEscucharDatos()
            }
        } catch (e: Exception) {
            android.widget.Toast.makeText(this, "Error: Revisa que el Arduino esté encendido", android.widget.Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    fun enviarComandoBluetooth(caracter: Char) {
        if (bluetoothSocket != null && bluetoothSocket!!.isConnected) {
            try {
                bluetoothSocket!!.outputStream.write(caracter.code)
            } catch (e: Exception) {
                // Falla silenciosa o log de desarrollo
            }
        }
    }

    fun comenzarAEscucharDatos() {
        escuchando = true
        hiloEscucha = Thread {
            val lector = java.io.BufferedReader(java.io.InputStreamReader(bluetoothSocket?.inputStream))
            while (escuchando && bluetoothSocket != null && bluetoothSocket!!.isConnected) {
                try {
                    val linea = lector.readLine()
                    if (linea != null && linea.startsWith("DATA:")) {
                        val datosLimpios = linea.replace("DATA:", "").trim()
                        val partes = datosLimpios.split(",")

                        if (partes.size >= 4) {
                            runOnUiThread {
                                azimutTelescopio.value = "${partes[0]}°"
                                alturaTelescopio.value = "${partes[1]}°"
                                errorSeguimiento.value = "${partes[2]}°"
                                estadoTracking.value = if (partes[3] == "1" || partes[3].lowercase() == "true") "Rastreando" else "Alineando"
                            }
                        }
                    }
                } catch (e: Exception) {
                    escuchando = false
                    break
                }
            }
        }
        hiloEscucha?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            escuchando = false
            bluetoothSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
} // 👈 Esta llave cierra la clase MainActivity. Todo lo demás va AFUERA.


@Composable
fun PantallaPrincipalSeguidor(mainActivity: MainActivity? = null) {
    val fondoEspacialOscuro = Color(0xFF0A0B10)
    val tarjetaFondoTranslucido = Color(0xCC141622)
    val modoAutomaticoVioleta = Color(0xFF8A2BE2)
    val modoManualAzul = Color(0xFF1A73E8)
    val textoPrincipalBlanco = Color(0xFFF5F6FA)
    val textoSecundarioGris = Color(0xFF8F92A1)

    // Estados dinámicos de la interfaz enlazados al Arduino y al flujo de pantallas
    val enModoManual = mainActivity?.mostrarModoManual?.value ?: false
    val azimutReal = mainActivity?.azimutTelescopio?.value ?: "0.0°"
    val alturaReal = mainActivity?.alturaTelescopio?.value ?: "0.0°"
    val errorReal = mainActivity?.errorSeguimiento?.value ?: "0.0°"
    val trackingReal = mainActivity?.estadoTracking?.value ?: "Alineando"

    if (!enModoManual) {
        // ================================================================
        // PANTALLA PRINCIPAL: SELECCIÓN DE MODO Y TELEMETRÍA REAL
        // ================================================================
        Box(modifier = Modifier.fillMaxSize().background(fondoEspacialOscuro).padding(24.dp)) {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {

                // PANEL IZQUIERDO: Telemetría en tiempo real desde el Arduino
                Column(modifier = Modifier.weight(1.5f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {

                    // Tarjeta de Estado Dinámico (Cambia según el flag trackingActivo de tu Arduino)
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = tarjetaFondoTranslucido)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("SISTEMA ASTROTRACKER", color = textoSecundarioGris, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(trackingReal, color = if (trackingReal == "Rastreando") Color(0xFF00E676) else Color(0xFFFFD600), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Tarjeta de Posición Actual del Telescopio (Captura tu DATA:az,alt de Arduino)
                    Card(modifier = Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = tarjetaFondoTranslucido)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("POSICIÓN DEL TELECOPIO", color = textoSecundarioGris, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(12.dp))

                            Text("Azimut actual:", color = textoSecundarioGris, fontSize = 12.sp)
                            Text(azimutReal, color = textoPrincipalBlanco, fontSize = 22.sp, fontWeight = FontWeight.Bold)

                            Spacer(modifier = Modifier.height(8.dp))

                            Text("Altura actual:", color = textoSecundarioGris, fontSize = 12.sp)
                            Text(alturaReal, color = textoPrincipalBlanco, fontSize = 22.sp, fontWeight = FontWeight.Bold)

                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Error de desvío: $errorReal", color = textoSecundarioGris, fontSize = 13.sp)
                        }
                    }
                }

                // PANEL DERECHO: Botones de Acción Especiales
                Column(modifier = Modifier.weight(2f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(text = "Panel de Control Estelar", color = textoPrincipalBlanco, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {

                        // MODO AUTOMÁTICO (Envía las coordenadas y activa el tracking sideral)
                        Card(
                            modifier = Modifier.weight(1f).fillMaxHeight().clickable {
                                mainActivity?.conectarBluetooth()
                                mainActivity?.enviarComandoBluetooth('a') // Activa modo auto en Arduino

                                // Mandamos el paquete de simulación para Júpiter con saltos de línea (\n)
                                // Tu Arduino lee con readStringUntil('\n') y procesa con startsWith()
                                val comandoAz = "AZ_OBJ:125.4\n"
                                val comandoAlt = "ALT_OBJ:45.2\n"
                                val comandoTrack = "TRACK:ON\n"

                                // Inyectamos los textos directo al puerto serial
                                comandoAz.forEach { mainActivity?.enviarComandoBluetooth(it) }
                                comandoAlt.forEach { mainActivity?.enviarComandoBluetooth(it) }
                                comandoTrack.forEach { mainActivity?.enviarComandoBluetooth(it) }

                                Toast.makeText(mainActivity, "Apuntando automáticamente a Júpiter...", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = modoAutomaticoVioleta)
                        ) {
                            Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("✨", fontSize = 32.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("MODO AUTOMÁTICO", color = textoPrincipalBlanco, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("Rastrear objeto seleccionado", color = textoPrincipalBlanco.copy(alpha = 0.7f), fontSize = 11.sp, textAlign = TextAlign.Center)
                            }
                        }

                        // MODO MANUAL (Mantiene tu control exacto por flechas)
                        Card(
                            modifier = Modifier.weight(1f).fillMaxHeight().clickable {
                                mainActivity?.conectarBluetooth()
                                mainActivity?.enviarComandoBluetooth('m') // Asegura modo manual ('m') en Arduino
                                mainActivity?.mostrarModoManual?.value = true
                            },
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = modoManualAzul)
                        ) {
                            Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("🎮", fontSize = 32.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("MODO MANUAL", color = textoPrincipalBlanco, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                Text("Calibrar ejes y posición", color = textoPrincipalBlanco.copy(alpha = 0.7f), fontSize = 11.sp, textAlign = TextAlign.Center)
                            }
                        }
                    }

                    // Tarjeta Inferior de información o sugerencias
                    Card(modifier = Modifier.fillMaxWidth().height(80.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = tarjetaFondoTranslucido)) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Sugerencia de hoy: Júpiter (Visibilidad Óptima)", color = textoPrincipalBlanco, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    } else {
        // ================================================================
        // PANTALLA: CONTROL MANUAL (Mapeo exacto de caracteres de un byte)
        // ================================================================
        Box(
            modifier = Modifier.fillMaxSize().background(fondoEspacialOscuro).padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {

                Text(
                    "Control de Motores Manual",
                    color = textoPrincipalBlanco,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )

                // FLECHA ARRIBA -> Mueve Motor X adelante ('F')
                Card(
                    modifier = Modifier.size(70.dp).clickable { mainActivity?.enviarComandoBluetooth('F') },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = modoManualAzul)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("▲", color = textoPrincipalBlanco, fontSize = 24.sp)
                    }
                }

                // FILA CENTRAL: Flecha Izquierda, Botón ZERO y Flecha Derecha
                Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                    // FLECHA IZQUIERDA -> Mueve Motor Y a la izquierda ('L')
                    Card(
                        modifier = Modifier.size(70.dp).clickable { mainActivity?.enviarComandoBluetooth('L') },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = modoManualAzul)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("◀", color = textoPrincipalBlanco, fontSize = 24.sp)
                        }
                    }

                    // BOTÓN CENTRAL -> Envía posición actual como Cero ('Z')
                    Card(
                        modifier = Modifier.size(70.dp).clickable { mainActivity?.enviarComandoBluetooth('Z') },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF388E3C))
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("ZERO", color = textoPrincipalBlanco, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // FLECHA DERECHA -> Mueve Motor Y a la derecha ('R')
                    Card(
                        modifier = Modifier.size(70.dp).clickable { mainActivity?.enviarComandoBluetooth('R') },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = modoManualAzul)
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("▶", color = textoPrincipalBlanco, fontSize = 24.sp)
                        }
                    }
                }

                // FLECHA ABAJO -> Mueve Motor X atrás ('B')
                Card(
                    modifier = Modifier.size(70.dp).clickable { mainActivity?.enviarComandoBluetooth('B') },
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = modoManualAzul)
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("▼", color = textoPrincipalBlanco, fontSize = 24.sp)
                    }
                }

                // CONTROLES DE VELOCIDAD
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    // Botón Vel -
                    Card(
                        modifier = Modifier.height(45.dp).padding(horizontal = 8.dp).clickable { mainActivity?.enviarComandoBluetooth('-') },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
                    ) {
                        Box(modifier = Modifier.fillMaxHeight().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                            Text("Vel -", color = textoPrincipalBlanco)
                        }
                    }
                    // Botón Vel +
                    Card(
                        modifier = Modifier.height(45.dp).padding(horizontal = 8.dp).clickable { mainActivity?.enviarComandoBluetooth('+') },
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.DarkGray)
                    ) {
                        Box(modifier = Modifier.fillMaxHeight().padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                            Text("Vel +", color = textoPrincipalBlanco)
                        }
                    }
                }

                // BOTÓN VOLVER AL PANEL
                Text(
                    text = "◀ Volver al Panel",
                    color = textoSecundarioGris,
                    modifier = Modifier.clickable {
                        "MANUAL\n".forEach { mainActivity?.enviarComandoBluetooth(it) }
                        mainActivity?.mostrarModoManual?.value = false
                    }.padding(10.dp),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
