// Actualizacion 29/03/2026 Version timmy 1.5 (Ni idea de las anteriores versiones),esta version se caracteriza por:
//Se implementó una separación estricta entre el modo manual y automático, evitando conflictos en la lectura de datos Bluetooth. Además, se mejoró el protocolo de comunicación, la estabilidad del sistema y la lógica de calibración, manteniendo compatibilidad con funciones existentes como la lectura de posición.
// ============================================================
// SISTEMA DE SEGUIMIENTO ASTRONOMICO AUTOMATICO
// Este programa controla dos motores paso a paso (X e Y)
// mediante Bluetooth desde una app móvil.
// ============================================================
// - MODO MANUAL → comandos simples (char)
// - MODO AUTOMATICO → mensajes tipo "CLAVE:VALOR"

// Librería para controlar motores paso a paso con aceleración y velocidad
#include <AccelStepper.h>

// Librería para crear un puerto serie adicional (para Bluetooth HC-05)
#include <SoftwareSerial.h>


// ================== DEFINICIÓN DE PINES ==================

// Pin que envía pulsos al motor X (movimiento paso a paso)
const int pinPasoX = 2;

// Pin que define la dirección de giro del motor X
const int pinDireccionX = 5;

// Pin de pasos del motor Y
const int pinPasoY = 3;

// Pin de dirección del motor Y
const int pinDireccionY = 6;


// ================== CREACIÓN DE MOTORES ==================

// Se crea el motor X indicando que usa un driver externo (tipo A4988 como ahora,en el futuro puede ser otro tipo)
AccelStepper motorX(AccelStepper::DRIVER, pinPasoX, pinDireccionX);

// Se crea el motor Y
AccelStepper motorY(AccelStepper::DRIVER, pinPasoY, pinDireccionY);


// ================== BLUETOOTH ==================

// Se crea un puerto serial por software en pines 10 y 11. 
// RX (recibe datos), TX (envía datos)
SoftwareSerial bluetoothSerial(10, 11); //Puede ser que este delirando y sea necesario poner los pines 0 y 1 en vez de 10 y 11.


// ================== VARIABLES DE CONTROL ==================
//(Recordar que para que estos valores sean mas validos primero declarar toda posicion en el modo manual).
// Guarda la posición actual como referencia cero
int posicionCeroX = 0;
int posicionCeroY = 0;

// Define límites máximos para evitar que el sistema se rompa
int posicionMaximaX = 0;
int posicionMaximaY = 0;


// Velocidad máxima de los motores en modo manual
int velocidadX = 1000;
int velocidadY = 1000;


// Arrays para guardar posiciones (memoria de puntos)
int puntosMovimientoX[10];
int puntosMovimientoY[10];

// Cantidad de puntos guardados
int numPuntos = 0;


// ================== MODO AUTOMATICO ==================
//(Es importante que para que esto funcione primero se tiene que dejar bien claro los valores que se obtienen del modo Manual).
// Indica si el sistema está en modo automático
bool modoAutomatico = false;

// Indica si está siguiendo un objeto continuamente
bool trackingActivo = false;


// Coordenadas del objeto a seguir (enviadas por la app)
float azObjetivo = 0;   // azimut
float altObjetivo = 0;  // altura


// Coordenadas actuales del celular (sensores)
float azActual = 0;
float altActual = 0;


// Velocidad de seguimiento (tracking sideral)
float velocidadSideral = 50;


// ================== CALIBRACION ==================

// Relación entre pasos del motor y grados reales
// ESTE VALOR SE AJUSTA DESDE LA APP
float pasosPorGrado = 10.0;
// ============================================================
// FORMATO AUTOMATICO:
// AZ_OBJ:x
// ALT_OBJ:x
// AZ_TEL:x
// ALT_TEL:x
// TRACK:ON / OFF
// CAL:START
// CAL:valor
// MANUAL
// ============================================================

// ================== SETUP ==================
void setup() {

  // Configura velocidad máxima del motor X
  motorX.setMaxSpeed(velocidadX);

  // Configura aceleración del motor X
  motorX.setAcceleration(500);

  // Configura velocidad máxima del motor Y
  motorY.setMaxSpeed(velocidadY);

  // Configura aceleración del motor Y
  motorY.setAcceleration(500);

  // Inicializa comunicación Bluetooth
  bluetoothSerial.begin(9600);
}


// ================== LOOP PRINCIPAL ==================
void loop() {

  // ============================================================
  // ================== MODO MANUAL ==================
  // ============================================================
  if (!modoAutomatico && bluetoothSerial.available() > 0) {

    // Lee un carácter enviado desde la app
    char comando = bluetoothSerial.read();

    // Evalúa qué comando se recibió
    switch (comando) {

      // Mueve motor X hacia adelante
      case 'F':
        motorX.moveTo(motorX.currentPosition() + 200);
        break;

      // Mueve motor X hacia atrás
      case 'B':
        motorX.moveTo(motorX.currentPosition() - 200);
        break;

      // Mueve motor Y a la derecha
      case 'R':
        motorY.moveTo(motorY.currentPosition() + 200);
        break;

      // Mueve motor Y a la izquierda
      case 'L':
        motorY.moveTo(motorY.currentPosition() - 200);
        break;

      // Define la posición actual como cero
      case 'Z':
        posicionCeroX = motorX.currentPosition();
        posicionCeroY = motorY.currentPosition();
        break;

      // Define la posición actual como límite máximo
      case 'M':
        posicionMaximaX = motorX.currentPosition();
        posicionMaximaY = motorY.currentPosition();
        break;

      // Envía la posición actual al celular
      case 'P': {
        int posX = motorX.currentPosition();
        int posY = motorY.currentPosition();
        bluetoothSerial.println(String(posX) + "," + String(posY));
        break;
      }

      // Aumenta velocidad
      case '+':
        velocidadX += 100;
        velocidadY += 100;
        motorX.setMaxSpeed(velocidadX);
        motorY.setMaxSpeed(velocidadY);
        break;

      // Disminuye velocidad
      case '-':
        velocidadX -= 100;
        velocidadY -= 100;
        if (velocidadX < 100) velocidadX = 100;
        if (velocidadY < 100) velocidadY = 100;
        motorX.setMaxSpeed(velocidadX);
        motorY.setMaxSpeed(velocidadY);
        break;

      // Activa modo automático
      case 'a':
        modoAutomatico = true;
        break;

      // Vuelve a modo manual
      case 'm':
        modoAutomatico = false;
        trackingActivo = false;
        break;
    }
  }


  // ============================================================
  // ================== MODO AUTOMATICO ==================
  // ============================================================
  if (modoAutomatico && bluetoothSerial.available() > 0) {

    // Lee texto completo hasta salto de línea
    String msg = bluetoothSerial.readStringUntil('\n');

    // Elimina espacios o saltos
    msg.trim();

    // ===== CAMBIO DE MODO =====
    if (msg == "MANUAL") {
      modoAutomatico = false;
      trackingActivo = false;
    }

    // Recibe coordenadas del objeto
    else if (msg.startsWith("AZ_OBJ:"))
      azObjetivo = msg.substring(7).toFloat();

    else if (msg.startsWith("ALT_OBJ:"))
      altObjetivo = msg.substring(8).toFloat();

    // Recibe datos del celular
    else if (msg.startsWith("AZ_TEL:"))
      azActual = msg.substring(7).toFloat();

    else if (msg.startsWith("ALT_TEL:"))
      altActual = msg.substring(8).toFloat();

    // Activa o desactiva tracking
    else if (msg == "TRACK:ON")
      trackingActivo = true;

    else if (msg == "TRACK:OFF")
      trackingActivo = false;

    // Ajusta velocidad automática
    else if (msg.startsWith("VEL:"))
      velocidadSideral = msg.substring(4).toFloat();

    // ================== CALIBRACION ==================

    // Inicia calibración moviendo 90°
    else if (msg == "CAL:START") {

      // Desactiva tracking pero mantiene modo automático
      trackingActivo = false;

      // Mueve el motor 90 grados teóricos
      motorX.move(90 * pasosPorGrado);
    }

    // Recibe nuevo valor calibrado
    else if (msg.startsWith("CAL:")) {
      pasosPorGrado = msg.substring(4).toFloat();
    }
  }


  // ================== LOGICA AUTOMATICA ==================
  if (modoAutomatico) {

    // Si no está en tracking, apunta al objeto
    if (!trackingActivo) {

      // Calcula diferencia angular
      float errorAz = azObjetivo - azActual;
      float errorAlt = altObjetivo - altActual;

      // Corrige el giro para el camino más corto
      if (errorAz > 180) errorAz -= 360;
      if (errorAz < -180) errorAz += 360;

      // Mueve motores según error
      motorX.move(errorAz * pasosPorGrado);
      motorY.move(errorAlt * pasosPorGrado);
    }

    // Si está en tracking, mueve continuamente
    else {
      motorX.setSpeed(velocidadSideral);
      motorX.runSpeed();
    }
  }


  // Ejecuta movimiento real de los motores
  motorX.run();
  motorY.run();


  // Envía datos al celular
  enviarDatos();
}


// ================== ENVIO DE DATOS ==================
void enviarDatos() {

  static unsigned long t = 0;

  // Envía datos cada 500 ms
  if (millis() - t > 500) {

    t = millis();

    float errorAz = azObjetivo - azActual;

    // Envía datos en formato:
    // DATA:az,alt,error,tracking
    bluetoothSerial.print("DATA:");
    bluetoothSerial.print(azActual);
    bluetoothSerial.print(",");
    bluetoothSerial.print(altActual);
    bluetoothSerial.print(",");
    bluetoothSerial.print(errorAz);
    bluetoothSerial.print(",");
    bluetoothSerial.println(trackingActivo);
  }
}