import { MessageCircle, Mail, Phone, ChevronRight, Eye, Bluetooth, AlertCircle, CheckCircle, Wifi } from 'lucide-react';

export function HelpView() {
  return (
    <div className="max-w-6xl mx-auto">
      <div className="mb-6">
        <h1 className="text-3xl font-bold text-white mb-2">Ayuda y Soporte</h1>
        <p className="text-slate-400">Encuentra respuestas a tus preguntas o contáctanos</p>
      </div>

      {/* Contact Options */}
      <div className="grid lg:grid-cols-3 gap-6 mb-8">
        <div className="bg-white/5 backdrop-blur-md rounded-xl p-6 border border-white/10 hover:bg-white/10 transition-colors cursor-pointer">
          <MessageCircle className="w-10 h-10 text-blue-400 mb-3" />
          <h3 className="text-white font-semibold mb-2">Chat en vivo</h3>
          <p className="text-slate-400 text-sm">Habla con nuestro equipo de soporte</p>
        </div>
        <div className="bg-white/5 backdrop-blur-md rounded-xl p-6 border border-white/10 hover:bg-white/10 transition-colors cursor-pointer">
          <Mail className="w-10 h-10 text-purple-400 mb-3" />
          <h3 className="text-white font-semibold mb-2">Email</h3>
          <p className="text-slate-400 text-sm">support@seguidor-estrellas.com</p>
        </div>
        <div className="bg-white/5 backdrop-blur-md rounded-xl p-6 border border-white/10 hover:bg-white/10 transition-colors cursor-pointer">
          <Phone className="w-10 h-10 text-green-400 mb-3" />
          <h3 className="text-white font-semibold mb-2">Teléfono</h3>
          <p className="text-slate-400 text-sm">+54 11 1234-5678</p>
        </div>
      </div>

      {/* Bluetooth Connection Guide */}
      <div className="bg-gradient-to-br from-blue-500/10 to-purple-500/10 backdrop-blur-md rounded-xl p-6 border border-blue-500/30 mb-8">
        <div className="flex items-center gap-3 mb-4">
          <div className="bg-blue-500/20 p-3 rounded-lg">
            <Bluetooth className="w-8 h-8 text-blue-400" />
          </div>
          <div>
            <h2 className="text-white font-semibold text-xl">Guía de Conexión Bluetooth</h2>
            <p className="text-slate-300 text-sm">Conecta tu Arduino al sistema de rastreo</p>
          </div>
        </div>

        <div className="space-y-4">
          <div className="bg-white/5 rounded-lg p-4">
            <div className="flex items-start gap-3">
              <div className="bg-blue-500 text-white rounded-full w-6 h-6 flex items-center justify-center text-sm font-semibold flex-shrink-0">1</div>
              <div>
                <h4 className="text-white font-semibold mb-1">Activa el Bluetooth en tu dispositivo</h4>
                <p className="text-slate-300 text-sm">Asegúrate de que el Bluetooth esté encendido en tu teléfono o tablet. Ve a Configuración y verifica que esté activado.</p>
              </div>
            </div>
          </div>

          <div className="bg-white/5 rounded-lg p-4">
            <div className="flex items-start gap-3">
              <div className="bg-blue-500 text-white rounded-full w-6 h-6 flex items-center justify-center text-sm font-semibold flex-shrink-0">2</div>
              <div>
                <h4 className="text-white font-semibold mb-1">Enciende tu módulo Arduino</h4>
                <p className="text-slate-300 text-sm">Alimenta tu Arduino con el módulo Bluetooth HC-05 o HC-06. El LED del módulo debe parpadear indicando que está en modo de emparejamiento.</p>
              </div>
            </div>
          </div>

          <div className="bg-white/5 rounded-lg p-4">
            <div className="flex items-start gap-3">
              <div className="bg-blue-500 text-white rounded-full w-6 h-6 flex items-center justify-center text-sm font-semibold flex-shrink-0">3</div>
              <div>
                <h4 className="text-white font-semibold mb-1">Busca dispositivos disponibles</h4>
                <p className="text-slate-300 text-sm">En la app, ve a Configuración → Conexión → Buscar Dispositivos. El módulo aparecerá como "HC-05", "HC-06" o "Seguidor Estrellas".</p>
              </div>
            </div>
          </div>

          <div className="bg-white/5 rounded-lg p-4">
            <div className="flex items-start gap-3">
              <div className="bg-blue-500 text-white rounded-full w-6 h-6 flex items-center justify-center text-sm font-semibold flex-shrink-0">4</div>
              <div>
                <h4 className="text-white font-semibold mb-1">Empareja el dispositivo</h4>
                <p className="text-slate-300 text-sm mb-2">Si es la primera vez, ingresa el PIN de emparejamiento:</p>
                <div className="bg-blue-500/20 rounded px-3 py-2 inline-block">
                  <span className="text-blue-300 font-mono">PIN: 1234 o 0000</span>
                </div>
              </div>
            </div>
          </div>

          <div className="bg-white/5 rounded-lg p-4">
            <div className="flex items-start gap-3">
              <div className="bg-green-500 text-white rounded-full w-6 h-6 flex items-center justify-center text-sm font-semibold flex-shrink-0">5</div>
              <div>
                <h4 className="text-white font-semibold mb-1">¡Conexión exitosa!</h4>
                <p className="text-slate-300 text-sm">El indicador de conexión en la parte superior debe mostrar "Conectado" en verde. Ahora puedes controlar tu telescopio.</p>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Troubleshooting */}
      <div className="bg-white/5 backdrop-blur-md rounded-xl p-6 border border-white/10 mb-6">
        <div className="flex items-center gap-3 mb-4">
          <AlertCircle className="w-6 h-6 text-yellow-400" />
          <h2 className="text-white font-semibold text-xl">Solución de Problemas Bluetooth</h2>
        </div>

        <div className="space-y-3">
          <details className="group">
            <summary className="flex items-center justify-between cursor-pointer text-white font-semibold py-3 px-4 bg-white/5 rounded-lg hover:bg-white/10">
              <div className="flex items-center gap-2">
                <AlertCircle className="w-4 h-4 text-yellow-400" />
                <span>No encuentro mi dispositivo Bluetooth</span>
              </div>
              <ChevronRight className="w-5 h-5 group-open:rotate-90 transition-transform" />
            </summary>
            <div className="mt-2 px-4 py-3 text-slate-300 text-sm space-y-2">
              <p><strong>Soluciones:</strong></p>
              <ul className="list-disc pl-5 space-y-1">
                <li>Verifica que el Arduino esté alimentado correctamente (LED encendido)</li>
                <li>Asegúrate de que el módulo Bluetooth esté conectado correctamente a los pines TX/RX del Arduino</li>
                <li>Reinicia el Bluetooth de tu dispositivo móvil</li>
                <li>Acércate más al módulo Bluetooth (máximo 10 metros)</li>
                <li>Si usas HC-05, verifica que esté en modo esclavo (slave)</li>
              </ul>
            </div>
          </details>

          <details className="group">
            <summary className="flex items-center justify-between cursor-pointer text-white font-semibold py-3 px-4 bg-white/5 rounded-lg hover:bg-white/10">
              <div className="flex items-center gap-2">
                <AlertCircle className="w-4 h-4 text-yellow-400" />
                <span>La conexión se pierde constantemente</span>
              </div>
              <ChevronRight className="w-5 h-5 group-open:rotate-90 transition-transform" />
            </summary>
            <div className="mt-2 px-4 py-3 text-slate-300 text-sm space-y-2">
              <p><strong>Causas comunes:</strong></p>
              <ul className="list-disc pl-5 space-y-1">
                <li><strong>Interferencia:</strong> Aléjate de otros dispositivos Bluetooth o WiFi</li>
                <li><strong>Batería baja:</strong> Verifica que tu Arduino tenga suficiente energía</li>
                <li><strong>Distancia:</strong> Mantén una distancia máxima de 5-10 metros</li>
                <li><strong>Obstáculos:</strong> Evita paredes gruesas o objetos metálicos entre dispositivos</li>
                <li><strong>Cables sueltos:</strong> Revisa todas las conexiones del módulo Bluetooth</li>
              </ul>
            </div>
          </details>

          <details className="group">
            <summary className="flex items-center justify-between cursor-pointer text-white font-semibold py-3 px-4 bg-white/5 rounded-lg hover:bg-white/10">
              <div className="flex items-center gap-2">
                <AlertCircle className="w-4 h-4 text-yellow-400" />
                <span>El PIN no funciona o no es aceptado</span>
              </div>
              <ChevronRight className="w-5 h-5 group-open:rotate-90 transition-transform" />
            </summary>
            <div className="mt-2 px-4 py-3 text-slate-300 text-sm space-y-2">
              <p><strong>PINs comunes por defecto:</strong></p>
              <ul className="list-disc pl-5 space-y-1">
                <li>HC-05: <span className="font-mono bg-white/10 px-2 py-0.5 rounded">1234</span></li>
                <li>HC-06: <span className="font-mono bg-white/10 px-2 py-0.5 rounded">1234</span></li>
                <li>Algunos módulos: <span className="font-mono bg-white/10 px-2 py-0.5 rounded">0000</span></li>
              </ul>
              <p className="mt-2">Si ninguno funciona, es posible que el PIN haya sido modificado. Consulta la documentación de tu módulo o resetéalo a valores de fábrica.</p>
            </div>
          </details>

          <details className="group">
            <summary className="flex items-center justify-between cursor-pointer text-white font-semibold py-3 px-4 bg-white/5 rounded-lg hover:bg-white/10">
              <div className="flex items-center gap-2">
                <AlertCircle className="w-4 h-4 text-yellow-400" />
                <span>Los comandos no responden</span>
              </div>
              <ChevronRight className="w-5 h-5 group-open:rotate-90 transition-transform" />
            </summary>
            <div className="mt-2 px-4 py-3 text-slate-300 text-sm space-y-2">
              <p><strong>Verificaciones:</strong></p>
              <ul className="list-disc pl-5 space-y-1">
                <li>Confirma que el código Arduino esté cargado correctamente</li>
                <li>Verifica la velocidad de baudios (debe ser 9600 o 38400 típicamente)</li>
                <li>Revisa que los pines TX del Bluetooth estén conectados al RX del Arduino y viceversa</li>
                <li>Asegúrate de que los motores estén alimentados con su propia fuente</li>
                <li>Verifica las conexiones de los drivers de motor (L298N, ULN2003, etc.)</li>
              </ul>
            </div>
          </details>

          <details className="group">
            <summary className="flex items-center justify-between cursor-pointer text-white font-semibold py-3 px-4 bg-white/5 rounded-lg hover:bg-white/10">
              <div className="flex items-center gap-2">
                <AlertCircle className="w-4 h-4 text-yellow-400" />
                <span>El LED del módulo no parpadea</span>
              </div>
              <ChevronRight className="w-5 h-5 group-open:rotate-90 transition-transform" />
            </summary>
            <div className="mt-2 px-4 py-3 text-slate-300 text-sm space-y-2">
              <p><strong>Posibles causas:</strong></p>
              <ul className="list-disc pl-5 space-y-1">
                <li>Módulo no está recibiendo alimentación (VCC y GND mal conectados)</li>
                <li>Voltaje incorrecto (los módulos HC-05/06 requieren 3.3V-6V)</li>
                <li>Módulo defectuoso - prueba con otro</li>
                <li>Pines VCC y GND invertidos</li>
              </ul>
            </div>
          </details>
        </div>
      </div>

      {/* FAQ General */}
      <div className="bg-white/5 backdrop-blur-md rounded-xl p-6 border border-white/10 mb-6">
        <h2 className="text-white font-semibold text-xl mb-4">Preguntas Frecuentes</h2>
        
        <div className="space-y-4">
          <details className="group">
            <summary className="flex items-center justify-between cursor-pointer text-white font-semibold py-3 px-4 bg-white/5 rounded-lg hover:bg-white/10">
              ¿Cómo calibro mi telescopio por primera vez?
              <ChevronRight className="w-5 h-5 group-open:rotate-90 transition-transform" />
            </summary>
            <div className="mt-2 px-4 py-3 text-slate-300 text-sm">
              Para calibrar tu telescopio por primera vez, ve a la sección de Calibración en el menú. Asegúrate de que el telescopio esté nivelado y apuntando al norte. Luego, sigue el asistente de alineación de 3 estrellas que te guiará paso a paso. Es importante realizar esto con el Bluetooth conectado para que los ajustes se guarden en el Arduino.
            </div>
          </details>

          <details className="group">
            <summary className="flex items-center justify-between cursor-pointer text-white font-semibold py-3 px-4 bg-white/5 rounded-lg hover:bg-white/10">
              ¿Qué diferencia hay entre modo manual y automático?
              <ChevronRight className="w-5 h-5 group-open:rotate-90 transition-transform" />
            </summary>
            <div className="mt-2 px-4 py-3 text-slate-300 text-sm">
              El <strong>modo manual</strong> te permite controlar directamente la dirección y velocidad del telescopio usando los controles de la app, enviando comandos en tiempo real al Arduino vía Bluetooth. El <strong>modo automático</strong> busca y rastrea objetos celestes automáticamente usando coordenadas astronómicas, ideal para fotografía de larga exposición.
            </div>
          </details>

          <details className="group">
            <summary className="flex items-center justify-between cursor-pointer text-white font-semibold py-3 px-4 bg-white/5 rounded-lg hover:bg-white/10">
              ¿Qué módulos Bluetooth son compatibles?
              <ChevronRight className="w-5 h-5 group-open:rotate-90 transition-transform" />
            </summary>
            <div className="mt-2 px-4 py-3 text-slate-300 text-sm space-y-2">
              <p>Los módulos Bluetooth compatibles son:</p>
              <ul className="list-disc pl-5 space-y-1">
                <li><strong>HC-05:</strong> Recomendado, puede funcionar como maestro o esclavo</li>
                <li><strong>HC-06:</strong> Compatible, solo funciona como esclavo</li>
                <li><strong>HM-10:</strong> Compatible (Bluetooth 4.0 BLE)</li>
              </ul>
              <p className="mt-2">Asegúrate de configurar la velocidad de baudios correcta (9600 bps es la más común).</p>
            </div>
          </details>

          <details className="group">
            <summary className="flex items-center justify-between cursor-pointer text-white font-semibold py-3 px-4 bg-white/5 rounded-lg hover:bg-white/10">
              ¿Cómo guardo mis observaciones?
              <ChevronRight className="w-5 h-5 group-open:rotate-90 transition-transform" />
            </summary>
            <div className="mt-2 px-4 py-3 text-slate-300 text-sm">
              Durante una sesión de rastreo, presiona el botón "Guardar Observación" en la pantalla principal. Podrás agregar notas, fotos y detalles sobre tu observación. Todas las observaciones se guardan automáticamente en la sección "Mis Observaciones".
            </div>
          </details>

          <details className="group">
            <summary className="flex items-center justify-between cursor-pointer text-white font-semibold py-3 px-4 bg-white/5 rounded-lg hover:bg-white/10">
              ¿Cómo actualizo la ubicación GPS?
              <ChevronRight className="w-5 h-5 group-open:rotate-90 transition-transform" />
            </summary>
            <div className="mt-2 px-4 py-3 text-slate-300 text-sm">
              Ve a Configuración → Ubicación y activa "GPS Automático". La aplicación detectará automáticamente tu ubicación. También puedes ingresar las coordenadas manualmente si lo prefieres. La ubicación precisa es crucial para el rastreo exacto de objetos celestes.
            </div>
          </details>

          <details className="group">
            <summary className="flex items-center justify-between cursor-pointer text-white font-semibold py-3 px-4 bg-white/5 rounded-lg hover:bg-white/10">
              ¿Con qué frecuencia debo recalibrar?
              <ChevronRight className="w-5 h-5 group-open:rotate-90 transition-transform" />
            </summary>
            <div className="mt-2 px-4 py-3 text-slate-300 text-sm">
              Se recomienda recalibrar el telescopio cada 30 días o después de moverlo a una nueva ubicación. También es buena idea recalibrar si notas que el rastreo pierde precisión con el tiempo.
            </div>
          </details>

          <details className="group">
            <summary className="flex items-center justify-between cursor-pointer text-white font-semibold py-3 px-4 bg-white/5 rounded-lg hover:bg-white/10">
              ¿Qué Arduino necesito?
              <ChevronRight className="w-5 h-5 group-open:rotate-90 transition-transform" />
            </summary>
            <div className="mt-2 px-4 py-3 text-slate-300 text-sm space-y-2">
              <p>Puedes usar cualquier placa Arduino con puerto serial. Las más recomendadas son:</p>
              <ul className="list-disc pl-5 space-y-1">
                <li><strong>Arduino Uno:</strong> La más común y recomendada</li>
                <li><strong>Arduino Nano:</strong> Más compacta, perfecta para espacios reducidos</li>
                <li><strong>Arduino Mega:</strong> Para proyectos más complejos con más motores</li>
              </ul>
              <p className="mt-2">Asegúrate de tener el código sketch compatible instalado en tu Arduino.</p>
            </div>
          </details>
        </div>
      </div>

      {/* Technical Specs */}
      <div className="bg-white/5 backdrop-blur-md rounded-xl p-6 border border-white/10 mb-6">
        <h2 className="text-white font-semibold text-xl mb-4">Especificaciones Técnicas</h2>
        
        <div className="grid md:grid-cols-2 gap-6">
          <div>
            <h3 className="text-white font-semibold mb-3 flex items-center gap-2">
              <Bluetooth className="w-5 h-5 text-blue-400" />
              Bluetooth
            </h3>
            <div className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-slate-400">Protocolo:</span>
                <span className="text-white">Bluetooth 2.0/4.0</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-400">Alcance:</span>
                <span className="text-white">Hasta 10 metros</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-400">Velocidad baudios:</span>
                <span className="text-white">9600 bps (default)</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-400">Voltaje:</span>
                <span className="text-white">3.3V - 6V</span>
              </div>
            </div>
          </div>

          <div>
            <h3 className="text-white font-semibold mb-3 flex items-center gap-2">
              <Wifi className="w-5 h-5 text-purple-400" />
              Arduino
            </h3>
            <div className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-slate-400">Modelos soportados:</span>
                <span className="text-white">Uno, Nano, Mega</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-400">Pines usados:</span>
                <span className="text-white">TX/RX (0,1)</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-400">Alimentación:</span>
                <span className="text-white">5V vía USB o 7-12V</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-400">Firmware:</span>
                <span className="text-white">v1.2.3 o superior</span>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* Video Tutorials */}
      <div className="bg-white/5 backdrop-blur-md rounded-xl p-6 border border-white/10">
        <h2 className="text-white font-semibold text-xl mb-4">Tutoriales en Video</h2>
        <div className="grid md:grid-cols-2 gap-4">
          <div className="bg-white/5 rounded-lg p-4 hover:bg-white/10 transition-colors cursor-pointer">
            <div className="bg-blue-500/20 w-full h-32 rounded-lg mb-3 flex items-center justify-center">
              <Eye className="w-12 h-12 text-blue-400" />
            </div>
            <h3 className="text-white font-semibold mb-1">Configuración Arduino + Bluetooth</h3>
            <p className="text-slate-400 text-sm">Aprende a conectar y configurar tu módulo HC-05/HC-06</p>
          </div>
          <div className="bg-white/5 rounded-lg p-4 hover:bg-white/10 transition-colors cursor-pointer">
            <div className="bg-purple-500/20 w-full h-32 rounded-lg mb-3 flex items-center justify-center">
              <Eye className="w-12 h-12 text-purple-400" />
            </div>
            <h3 className="text-white font-semibold mb-1">Solución de Problemas Bluetooth</h3>
            <p className="text-slate-400 text-sm">Guía paso a paso para resolver conexiones fallidas</p>
          </div>
          <div className="bg-white/5 rounded-lg p-4 hover:bg-white/10 transition-colors cursor-pointer">
            <div className="bg-green-500/20 w-full h-32 rounded-lg mb-3 flex items-center justify-center">
              <Eye className="w-12 h-12 text-green-400" />
            </div>
            <h3 className="text-white font-semibold mb-1">Calibración Inicial</h3>
            <p className="text-slate-400 text-sm">Tutorial completo de primera calibración</p>
          </div>
          <div className="bg-white/5 rounded-lg p-4 hover:bg-white/10 transition-colors cursor-pointer">
            <div className="bg-orange-500/20 w-full h-32 rounded-lg mb-3 flex items-center justify-center">
              <Eye className="w-12 h-12 text-orange-400" />
            </div>
            <h3 className="text-white font-semibold mb-1">Rastreo Automático Avanzado</h3>
            <p className="text-slate-400 text-sm">Maximiza el rendimiento del modo automático</p>
          </div>
        </div>
      </div>
    </div>
  );
}
