package com.pebete.astrotracker.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.pebete.astrotracker.data.model.PhonePosition
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlin.math.PI

/**
 * Esta clase actúa como un "Adaptador" (Patrón Adapter).
 * El sistema de Android usa "Callbacks" (SensorEventListener) que son anticuados y difíciles de coordinar.
 * Lo que hace esta clase es "atrapar" esos eventos de Android y convertirlos en un "Flow" moderno
 * que el resto de nuestra aplicación puede escuchar reactivamente.
 */
class PhoneSensorManager(context: Context) : SensorEventListener {

    // 1. Obtenemos el "Jefe de Sensores" (SensorManager) del sistema Android.
    // IMPORTANTE (anti memory-leak): usamos context.applicationContext, que vive tanto como la app entera.
    // Si guardáramos el Context de una Activity, esta no podría ser liberada al rotar/cerrar la pantalla
    // (en Java sería el clásico leak de "Activity referenciada desde un singleton").
    private val androidSensorMgr: SensorManager =
        context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // 2. Buscamos específicamente el sensor "ROTATION_VECTOR"
    // Según tu ICD (Sección 5), es el mejor porque combina giroscopio, acelerómetro y brújula 
    // matemáticamente para evitar temblores.
    private val rotationVectorSensor: Sensor? = androidSensorMgr.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    // 3. Nuestro "Tubo" (Flow) por el que emitiremos la posición calculada (Azimut y Altitud)
    // replay = 1 asegura que quien se conecte reciba instantáneamente el último valor calculado.
    // extraBufferCapacity = 64 evita que se congele si la interfaz tarda en dibujar los números.
    private val _positionFlow = MutableSharedFlow<PhonePosition>(replay = 1, extraBufferCapacity = 64)
    val positionFlow: SharedFlow<PhonePosition> = _positionFlow

    /**
     * true si el teléfono tiene el sensor ROTATION_VECTOR. Si es false, la UI debería avisar
     * al usuario que su dispositivo no es compatible (en vez de quedarse esperando datos que nunca llegan).
     */
    val isSensorAvailable: Boolean get() = rotationVectorSensor != null

    // Matrices matemáticas requeridas por Android para calcular la orientación
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    /**
     * Inicia la lectura del sensor.
     * @param delay El ritmo de actualización (SENSOR_DELAY_NORMAL para auto, SENSOR_DELAY_UI para manual)
     */
    fun startListening(delay: Int) {
        // Solo registramos el sensor si el celular realmente lo tiene
        rotationVectorSensor?.let { sensor ->
            // OJO: si el listener ya estaba registrado, Android ignora el nuevo "delay".
            // Por eso primero lo damos de baja: así cambiar de modo (UI <-> NORMAL) funciona de verdad.
            androidSensorMgr.unregisterListener(this)
            // "this" indica que los datos llegarán a la función onSensorChanged de esta misma clase
            androidSensorMgr.registerListener(this, sensor, delay)
        }
    }

    /**
     * Detiene la lectura. ¡VITAL para no drenar la batería del celular!
     */
    fun stopListening() {
        androidSensorMgr.unregisterListener(this)
        // Borramos la última posición guardada (replay) para que nadie reciba un dato viejo como si fuera actual
        _positionFlow.resetReplayCache()
    }

    /**
     * Esta función la llama el sistema Android CIENTOS de veces por segundo cada vez que el celular se mueve.
     */
    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return

        // Extraemos las matemáticas del vector de rotación
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)

        // Convertimos la matriz compleja en Ángulos de Euler (Azimut, Inclinación, Giro)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)

        // orientationAngles[0] -> Azimut (Rumbo de la brújula en radianes) (-π a π)
        // orientationAngles[1] -> Pitch (Inclinación del celular respecto al horizonte en radianes)

        // Convertimos Radianes a Grados para que el humano (y el Arduino) lo entiendan.
        // Formula: grados = radianes * (180 / π)
        var azimuthDeg = (orientationAngles[0] * 180 / PI).toFloat()

        // La brújula da el Azimut de -180 a 180 grados. 
        // En astronomía, el Azimut va de 0 a 360 grados. Ajustamos esto:
        if (azimuthDeg < 0) {
            azimuthDeg += 360f
        }

        // El pitch de Android es negativo cuando levantas la punta superior del teléfono hacia el cielo.
        // Multiplicamos por -1 para que la Altitud (apuntar al cielo) sea un valor positivo.
        val altitudeDeg = -(orientationAngles[1] * 180 / PI).toFloat()

        // Creamos el objeto de posición
        val currentPosition = PhonePosition(azimuthDeg, altitudeDeg)

        // Empujamos el valor por nuestro "tubo" (Flow). 
        // Usamos tryEmit porque estamos en un hilo sincrónico y si el tubo está lleno, simplemente suelta el dato.
        _positionFlow.tryEmit(currentPosition)
    }

    /**
     * Obligatorio implementarlo por la interfaz SensorEventListener, pero no nos interesa
     * si la precisión cambia, por ende lo dejamos vacío.
     */
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No hacer nada
    }
}
