package com.pebete.astrotracker.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.pebete.astrotracker.protocol.CommandProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import java.io.IOException
import java.io.OutputStream

//Posibles estados
enum class ConnectionState {
    DISCONNECTED,
    CONNECTED,
    CONNECTING,
    ERROR
}

//Suprimimos las alertas de permisos. Suponemos el usuario ya las otorgó en la UI
@SuppressLint("MissingPermission")

//Le pasamos como parámetro el scope de corrutinas para la gestión en segundo plano
class BluetoothManager(private val ioScope: CoroutineScope) {

    //socket representa la donexión física y lógica entre el celular y el HC-05
    private var socket: BluetoothSocket? = null

        //outputStream sería el medio por el que enviamos datos desde la app al HC-05
    private var outputStream: OutputStream? = null

    //Establecemos un flujo privado para emitir el estado
    //replay = 1 para que el último estado se guarde
    //Funciona como el patrón observer: Puedo usarla para enviar los datos necesarios respecto de la conexión
    private val _connectionState = MutableSharedFlow<ConnectionState>(replay = 1)

    //Flujo público de los estados para solo lectura, de forma que la app los vea
    //Es el observer como tal: esta no podría hacer nada más que mostrar los datos emitidos por _connectionState
    val connectionState: SharedFlow<ConnectionState> = _connectionState

    //Inicializamos el estado por defecto
    init {
        _connectionState.tryEmit(ConnectionState.DISCONNECTED)
    }

    //Conexión de forma asincrónica
    fun connect(device: BluetoothDevice) {
        //Mandamos una corrutina en el hilo de entrada/salida entre la app y el módulo HC-05
        ioScope.launch(Dispatchers.IO) {
            _connectionState.emit(ConnectionState.CONNECTING)
            try {
                //Instanciamos el socket usando el UUID del HC-05
                socket = device.createRfcommSocketToServiceRecord(CommandProtocol.SPP_UUID)

                //Hacemos la conexión (esto bloquearía el hilo hasta que responda o falle)
                socket?.connect()
                outputStream = socket?.outputStream

                _connectionState.emit(ConnectionState.CONNECTED)
            } catch (e: IOException) {
                //En caso de fallo, cerramos lo que estaba abierto y reportamos el error
                e.printStackTrace()
                disconnect()
                _connectionState.emit(ConnectionState.ERROR)
            }
        }
    }

    //Función para la desconexión
    fun disconnect() {
        //Funciona similar a un try-catch. Lo usamos para cortar de forma segura
        runCatching {
            outputStream?.close()
            socket?.close()
        }

        socket = null
        outputStream = null
        _connectionState.tryEmit(ConnectionState.DISCONNECTED)

    }

    //Función para el envío de datos
    fun write(data: String) {
        ioScope.launch(Dispatchers.IO) {
            runCatching {
                //Hacemos el envío del dato si outputStream no es null
                //toByteArray() convierte el String a bytes
                outputStream?.write(data.toByteArray())
            }.onFailure {
                //Si falla en envío de datos, desconectamos y notificamos el error
                disconnect()
                _connectionState.emit(ConnectionState.ERROR)
            }
        }
    }

    //Función para la lectura de los datos
    fun listenForLines(): Flow<String> = flow {
        //inputStream obtiene el flujo de entrada que mandaría el arduino por el HC-05
        //BufferedReader almacena los datos en un buffer hasta que llega el salto de línea \n
        val reader = socket?.inputStream?.bufferedReader() ?: return@flow

        while (true) {
            try {
                //Suspende la corrutina leyendo hasta que el arduino mande el salto de línea
                val line = reader.readLine()

                //Si retorna un null, la conexión se cerró en algún lado
                if (line == null) break

                //Emitimos lo que hayamos recibido
                emit(line)
            } catch (e: IOException) {
                break
            }
        }

        //Si llegamos acá, se salió del bucle por desconexión
        disconnect()
        _connectionState.emit(ConnectionState.DISCONNECTED)

    }.flowOn(Dispatchers.IO) //Aseguramos que todo este bloque se ejecute en el hilo IO
}