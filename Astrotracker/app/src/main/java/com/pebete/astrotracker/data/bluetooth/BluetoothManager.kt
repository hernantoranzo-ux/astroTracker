package com.pebete.astrotracker.data.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.pebete.astrotracker.protocol.CommandProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
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

    //Largo máximo de una línea recibida (ICD sección 7: se descartan líneas > 128 bytes)
    private companion object {
        const val MAX_LINE_LENGTH = 128
    }

    //socket representa la conexión física y lógica entre el celular y el HC-05
    //@Volatile: estas variables se leen/escriben desde hilos distintos (main e IO).
    //Es el equivalente al "volatile" de Java: garantiza que cada hilo vea el valor más reciente.
    @Volatile
    private var socket: BluetoothSocket? = null

    //outputStream sería el medio por el que enviamos datos desde la app al HC-05
    @Volatile
    private var outputStream: OutputStream? = null

    //Establecemos un flujo privado para emitir el estado
    //replay = 1 para que el último estado se guarde
    //extraBufferCapacity + DROP_OLDEST: garantiza que tryEmit() NUNCA falle ni se quede esperando,
    //aunque un observador sea lento (sin esto, un tryEmit podía perderse silenciosamente).
    //Funciona como el patrón observer: Puedo usarla para enviar los datos necesarios respecto de la conexión
    private val _connectionState = MutableSharedFlow<ConnectionState>(
        replay = 1,
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    //Flujo público de los estados para solo lectura, de forma que la app los vea
    //Es el observer como tal: esta no podría hacer nada más que mostrar los datos emitidos por _connectionState
    val connectionState: SharedFlow<ConnectionState> = _connectionState

    //Cola de mensajes a enviar. Un Channel es como una BlockingQueue de Java pero para corrutinas.
    //Un SOLO consumidor (ver init) la va vaciando EN ORDEN. Así dos write() seguidos
    //(ej. "AZ_TEL" y luego "ALT_TEL", o 'F' 'F') nunca se mezclan ni se desordenan,
    //cosa que sí podía pasar lanzando una corrutina nueva por cada write().
    private val writeQueue = Channel<String>(Channel.UNLIMITED)

    //Inicializamos el estado por defecto
    init {
        _connectionState.tryEmit(ConnectionState.DISCONNECTED)

        //Corrutina "escritora": vive mientras viva ioScope y envía los mensajes de a uno
        ioScope.launch(Dispatchers.IO) {
            for (data in writeQueue) {
                //Si no hay conexión, el mensaje simplemente se descarta (outputStream es null)
                val out = outputStream ?: continue
                try {
                    //toByteArray() convierte el String a bytes (ASCII, ver ICD sección 2)
                    out.write(data.toByteArray(Charsets.US_ASCII))
                    out.flush()
                } catch (e: IOException) {
                    //Si falla el envío de datos, desconectamos y notificamos el error
                    disconnect()
                    _connectionState.tryEmit(ConnectionState.ERROR)
                }
            }
        }
    }

    //Conexión de forma asincrónica
    fun connect(device: BluetoothDevice) {
        //Evitamos conexiones duplicadas (ej. doble tap en "Conectar")
        val current = _connectionState.replayCache.lastOrNull()
        if (current == ConnectionState.CONNECTING || current == ConnectionState.CONNECTED) return

        //Lo emitimos ACÁ (de forma síncrona), antes de lanzar la corrutina, para que un segundo
        //llamado inmediato ya vea el estado CONNECTING y sea ignorado.
        _connectionState.tryEmit(ConnectionState.CONNECTING)

        //Mandamos una corrutina en el hilo de entrada/salida entre la app y el módulo HC-05
        ioScope.launch(Dispatchers.IO) {
            var newSocket: BluetoothSocket? = null
            try {
                //Instanciamos el socket usando el UUID del HC-05
                newSocket = device.createRfcommSocketToServiceRecord(CommandProtocol.SPP_UUID)
                socket = newSocket

                //Hacemos la conexión (esto bloquearía el hilo hasta que responda o falle)
                newSocket.connect()
                outputStream = newSocket.outputStream

                _connectionState.emit(ConnectionState.CONNECTED)
            } catch (e: Exception) {
                //IOException: el HC-05 no respondió / está apagado / fuera de rango.
                //SecurityException: el usuario revocó el permiso BLUETOOTH_CONNECT.
                e.printStackTrace()

                //Si socket !== newSocket es porque el usuario llamó a disconnect() mientras
                //conectábamos (eso cierra el socket y hace fallar connect()): NO es un error real.
                val cancelledByUser = socket !== newSocket
                if (!cancelledByUser) {
                    //En caso de fallo, cerramos lo que estaba abierto y reportamos el error
                    disconnect()
                    _connectionState.emit(ConnectionState.ERROR)
                }
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
    //No bloquea: solo encola el mensaje; la corrutina escritora (init) lo envía en orden.
    fun write(data: String) {
        writeQueue.trySend(data)
    }

    //Función para la lectura de los datos
    //IMPORTANTE: hay que llamarla DESPUÉS de que el estado sea CONNECTED (si no, no hay socket y termina al instante)
    fun listenForLines(): Flow<String> = flow {
        //Guardamos "nuestro" socket: si más tarde se abre una conexión nueva, no queremos cerrarla por error
        val mySocket = socket ?: return@flow

        //inputStream obtiene el flujo de entrada que mandaría el arduino por el HC-05
        val reader = mySocket.inputStream.bufferedReader(Charsets.US_ASCII)

        val line = StringBuilder()
        var overflow = false //true = la línea actual ya superó el máximo: se ignora hasta el próximo \n

        while (true) {
            //Suspende la corrutina leyendo hasta que el arduino mande un caracter (-1 = conexión cerrada)
            val c = try {
                reader.read()
            } catch (e: IOException) {
                -1
            }

            //Si retorna -1, la conexión se cerró en algún lado
            if (c == -1) break

            when (c.toChar()) {
                '\n' -> {
                    //Línea completa: emitimos lo que hayamos recibido (si no está vacía ni desbordada)
                    if (!overflow && line.isNotEmpty()) emit(line.toString().trim())
                    line.setLength(0)
                    overflow = false
                }
                '\r' -> { /* El Arduino usa println() => "\r\n". Ignoramos el \r */ }
                else -> if (!overflow) {
                    //Protección contra basura sin salto de línea (ICD sección 7)
                    if (line.length >= MAX_LINE_LENGTH) {
                        overflow = true
                        line.setLength(0)
                    } else {
                        line.append(c.toChar())
                    }
                }
            }
        }

        //Si llegamos acá, se salió del bucle por desconexión.
        //Solo limpiamos si el socket sigue siendo el nuestro: si fue el usuario (disconnect) o un
        //error de escritura, ya se limpió y emitió el estado correcto (no pisamos un ERROR con DISCONNECTED).
        if (socket === mySocket) {
            disconnect()
        }

    }.flowOn(Dispatchers.IO) //Aseguramos que todo este bloque se ejecute en el hilo IO
}