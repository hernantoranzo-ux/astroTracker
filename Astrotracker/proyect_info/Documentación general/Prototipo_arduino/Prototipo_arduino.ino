// Actualizacion 23/10/2026 Version 1.8:
// (1) Correccion de giro continuo en modo automatico: solo se recalcula la trayectoria cuando los motores
//     terminaron el movimiento anterior (motorX.distanceToGo() == 0 && motorY.distanceToGo() == 0).
// (2) Se aumento BANDA_MUERTA_GRADOS a 1.0 grado para tolerar el ruido y jitter de los sensores del celular.
// (3) Sincronizacion de archivos y compatibilidad con Arduino IDE.
// Actualizacion 08/10/2026 Version 1.6: (1) La calibracion ya no es pisada por el lazo automatico (flag 'calibrando').
// (2) Failsafe de enlace: si en modo automatico no llegan AZ_TEL en 5 s se apaga el tracking y se detienen los motores.
// Actualizacion 29/03/2026 Version timmy 1.5:
// Se implemento una separacion estricta entre el modo manual y automatico, evitando conflictos en la lectura de datos Bluetooth.
// ============================================================
// SISTEMA DE SEGUIMIENTO ASTRONOMICO AUTOMATICO
// Este programa controla dos motores paso a paso (X e Y)
// mediante Bluetooth desde una app movil.
// ============================================================
// - MODO MANUAL -> comandos simples (char)
// - MODO AUTOMATICO -> mensajes tipo "CLAVE:VALOR"

// Libreria para controlar motores paso a paso con aceleracion y velocidad
#include <AccelStepper.h>

// Libreria para crear un puerto serie adicional (para Bluetooth HC-05)
#include <SoftwareSerial.h>


// ================== DEFINICION DE PINES ==================

// Pin que envia pulsos al motor X (movimiento paso a paso)
const int pinPasoX = 2;

// Pin que define la direccion de giro del motor X
const int pinDireccionX = 5;

// Pin de pasos del motor Y
const int pinPasoY = 3;

// Pin de direccion del motor Y
const int pinDireccionY = 6;


// ================== CREACION DE MOTORES ==================

// Se crea el motor X indicando que usa un driver externo (tipo A4988)
AccelStepper motorX(AccelStepper::DRIVER, pinPasoX, pinDireccionX);

// Se crea el motor Y
AccelStepper motorY(AccelStepper::DRIVER, pinPasoY, pinDireccionY);


// ================== BLUETOOTH ==================

// Se crea un puerto serial por software
SoftwareSerial bluetoothSerial(0, 1); // Pines para HC-05 (0 y 1 o 10 y 11 segun conexion fisica)


// ================== VARIABLES DE CONTROL ==================
int posicionCeroX = 0;
int posicionCeroY = 0;

int posicionMaximaX = 0;
int posicionMaximaY = 0;

// Velocidad maxima de los motores en modo manual
int velocidadX = 1000;
int velocidadY = 1000;

// Arrays para guardar posiciones (memoria de puntos)
int puntosMovimientoX[10];
int puntosMovimientoY[10];

int numPuntos = 0;


// ================== MODO AUTOMATICO ==================
bool modoAutomatico = false;
bool trackingActivo = false;

// Coordenadas del objeto a seguir (enviadas por la app)
float azObjetivo = 0;   // azimut
float altObjetivo = 0;  // altura

// Coordenadas actuales del celular (sensores)
float azActual = 0;
float altActual = 0;

// Velocidad de seguimiento (tracking sideral)
float velocidadSideral = 50;


// ================== FAILSAFE DE ENLACE (v1.6) ==================
const unsigned long TIMEOUT_ENLACE_MS = 5000;
unsigned long ultimoAzTel = 0;
bool enlaceActivo = true;

// v1.8: Bandera para actualizar la trayectoria del motor SOLO al recibir nuevas coordenadas
bool nuevaPosicionRecibida = false;

// v1.8: Banda muerta de 1.0 grado para tolerar el ruido y oscilacion natural de los sensores del celular
const float BANDA_MUERTA_GRADOS = 1.0;


// ================== CALIBRACION (flag v1.6) ==================
bool calibrando = false;
float pasosPorGrado = 10.0;


// ================== SETUP ==================
void setup() {

  // Configura velocidad maxima del motor X
  motorX.setMaxSpeed(velocidadX);

  // Configura aceleracion del motor X
  motorX.setAcceleration(500);

  // Configura velocidad maxima del motor Y
  motorY.setMaxSpeed(velocidadY);

  // Configura aceleracion del motor Y
  motorY.setAcceleration(500);

  // Inicializa comunicacion Bluetooth
  bluetoothSerial.begin(9600);
}


// ================== LOOP PRINCIPAL ==================
void loop() {

  // ============================================================
  // ================== MODO MANUAL ==================
  // ============================================================
  if (!modoAutomatico && bluetoothSerial.available() > 0) {

    char comando = bluetoothSerial.read();

    switch (comando) {

      // Mueve motor X hacia adelante
      case 'F':
        motorX.moveTo(motorX.currentPosition() + 200);
        break;

      // Mueve motor X hacia atras
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

      // Define la posicion actual como cero
      case 'Z':
        posicionCeroX = motorX.currentPosition();
        posicionCeroY = motorY.currentPosition();
        break;

      // Define la posicion actual como limite maximo
      case 'M':
        posicionMaximaX = motorX.currentPosition();
        posicionMaximaY = motorY.currentPosition();
        break;

      // Envia la posicion actual al celular
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

      // Activa modo automatico
      case 'a':
        modoAutomatico = true;
        ultimoAzTel = millis();   // arranca el reloj del failsafe de enlace
        enlaceActivo = true;
        break;

      // Vuelve a modo manual
      case 'm':
        modoAutomatico = false;
        trackingActivo = false;
        calibrando = false;
        break;
    }
  }


  // ============================================================
  // ================== MODO AUTOMATICO ==================
  // ============================================================
  if (modoAutomatico && bluetoothSerial.available() > 0) {

    String msg = bluetoothSerial.readStringUntil('\n');
    msg.trim();

    // ===== CAMBIO DE MODO =====
    if (msg == "MANUAL") {
      modoAutomatico = false;
      trackingActivo = false;
      calibrando = false;
      motorX.stop();   // v1.6: frena suavemente al salir de automatico
      motorY.stop();
    }

    // Recibe coordenadas del objeto
    else if (msg.startsWith("AZ_OBJ:")) {
      azObjetivo = msg.substring(7).toFloat();
      nuevaPosicionRecibida = true;
    }

    else if (msg.startsWith("ALT_OBJ:")) {
      altObjetivo = msg.substring(8).toFloat();
      nuevaPosicionRecibida = true;
    }

    // Recibe datos del celular
    else if (msg.startsWith("AZ_TEL:")) {
      azActual = msg.substring(7).toFloat();
      ultimoAzTel = millis();   // heartbeat del enlace
      enlaceActivo = true;
      nuevaPosicionRecibida = true;
    }

    else if (msg.startsWith("ALT_TEL:")) {
      altActual = msg.substring(8).toFloat();
      ultimoAzTel = millis();
      enlaceActivo = true;
      nuevaPosicionRecibida = true;
    }

    // Activa o desactiva tracking
    else if (msg == "TRACK:ON") {
      if (!calibrando) trackingActivo = true;
    }

    else if (msg == "TRACK:OFF")
      trackingActivo = false;

    // Ajusta velocidad automatica
    else if (msg.startsWith("VEL:"))
      velocidadSideral = msg.substring(4).toFloat();

    // ================== CALIBRACION ==================
    else if (msg == "CAL:START") {
      trackingActivo = false;
      calibrando = true;
      motorX.move(90 * pasosPorGrado);
    }

    else if (msg.startsWith("CAL:")) {
      float nuevo = msg.substring(4).toFloat();
      if (nuevo > 0) pasosPorGrado = nuevo;
      calibrando = false;
    }
  }


  // ================== LOGICA AUTOMATICA (v1.8) ==================
  if (modoAutomatico) {

    // FAILSAFE (v1.6): si no llega AZ_TEL en TIMEOUT_ENLACE_MS, se considera perdido el enlace con la app
    if (millis() - ultimoAzTel > TIMEOUT_ENLACE_MS) {
      if (enlaceActivo) {
        trackingActivo = false;   // apaga el seguimiento sideral
        motorX.stop();            // frena los motores
        motorY.stop();
        enlaceActivo = false;
      }
    }

    // Solo se controla el movimiento si hay enlace y no se esta calibrando
    if (enlaceActivo && !calibrando) {

      // Si no esta en tracking, apunta al objeto
      if (!trackingActivo) {

        // v1.8: Solo recalculamos la trayectoria si llegaron datos nuevos Y el movimiento previo ya finalizo.
        // Esto evita que cada nuevo paquete de la app (cada 200 ms) reinicie o extienda el movimiento indefinidamente.
        if (nuevaPosicionRecibida && motorX.distanceToGo() == 0 && motorY.distanceToGo() == 0) {
          nuevaPosicionRecibida = false;

          // Calcula diferencia angular
          float errorAz = azObjetivo - azActual;
          float errorAlt = altObjetivo - altActual;

          // Corrige el giro para el camino mas corto (-180 a 180)
          if (errorAz > 180) errorAz -= 360;
          if (errorAz < -180) errorAz += 360;

          // Aplica banda muerta para evitar vibraciones o micropasos erraticos
          if (abs(errorAz) > BANDA_MUERTA_GRADOS) {
            motorX.move(errorAz * pasosPorGrado);
          } else {
            motorX.stop();
          }

          if (abs(errorAlt) > BANDA_MUERTA_GRADOS) {
            motorY.move(errorAlt * pasosPorGrado);
          } else {
            motorY.stop();
          }
        }
      }

      // Si esta en tracking, mueve continuamente
      else {
        motorX.setSpeed(velocidadSideral);
        motorX.runSpeed();
      }
    }
  }


  // Ejecuta movimiento real de los motores
  motorX.run();
  motorY.run();


  // Envia datos al celular
  enviarDatos();
}


// ================== ENVIO DE DATOS ==================
void enviarDatos() {

  static unsigned long t = 0;

  // Envia datos cada 500 ms
  if (millis() - t > 500) {

    t = millis();

    float errorAz = azObjetivo - azActual;

    // Envia datos en formato:
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