import { Star, Hand, Zap, Info, MapPin, CloudMoon, Telescope, Clock, TrendingUp, History, Moon } from 'lucide-react';

export function HomeView() {
  return (
    <>
      {/* Title Section */}
      <div className="text-center mb-8">
        <div className="inline-flex items-center justify-center mb-4">
          <div className="relative">
            <div className="absolute inset-0 bg-blue-500/20 blur-2xl rounded-full"></div>
            <div className="relative bg-gradient-to-br from-blue-500 to-purple-600 p-4 rounded-full shadow-2xl">
              <Star className="w-12 h-12 text-white" fill="white" />
            </div>
          </div>
        </div>
        <h1 className="text-4xl font-bold text-white mb-2 tracking-tight">
          Seguidor de Estrellas
        </h1>
        <p className="text-slate-300">
          Explora el universo con precisión
        </p>
      </div>

      <div className="max-w-6xl mx-auto grid lg:grid-cols-3 gap-6">
        {/* Left Column - Info Cards */}
        <div className="lg:col-span-1 space-y-4">
          {/* Location & Time */}
          <div className="bg-white/5 backdrop-blur-md rounded-xl p-4 border border-white/10">
            <div className="flex items-center gap-2 mb-3">
              <MapPin className="w-4 h-4 text-blue-400" />
              <span className="text-sm text-slate-300">Ubicación Actual</span>
            </div>
            <p className="text-white font-semibold">Buenos Aires, AR</p>
            <p className="text-slate-400 text-sm">34.6037° S, 58.3816° W</p>
            
            <div className="mt-4 pt-4 border-t border-white/10">
              <div className="flex items-center gap-2 mb-2">
                <Clock className="w-4 h-4 text-purple-400" />
                <span className="text-sm text-slate-300">Hora Local</span>
              </div>
              <p className="text-white font-semibold">22:47 ART</p>
            </div>
          </div>

          {/* Sky Conditions */}
          <div className="bg-white/5 backdrop-blur-md rounded-xl p-4 border border-white/10">
            <div className="flex items-center gap-2 mb-3">
              <CloudMoon className="w-4 h-4 text-indigo-400" />
              <span className="text-sm text-slate-300">Condiciones</span>
            </div>
            <div className="space-y-2">
              <div className="flex justify-between items-center">
                <span className="text-slate-400 text-sm">Visibilidad</span>
                <span className="text-green-400 text-sm font-semibold">Excelente</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-slate-400 text-sm">Nubosidad</span>
                <span className="text-white text-sm">12%</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-slate-400 text-sm">Luna</span>
                <div className="flex items-center gap-1">
                  <Moon className="w-3 h-3 text-slate-400" />
                  <span className="text-white text-sm">Cuarto creciente</span>
                </div>
              </div>
            </div>
          </div>

          {/* Stats */}
          <div className="bg-white/5 backdrop-blur-md rounded-xl p-4 border border-white/10">
            <div className="flex items-center gap-2 mb-3">
              <TrendingUp className="w-4 h-4 text-emerald-400" />
              <span className="text-sm text-slate-300">Estadísticas</span>
            </div>
            <div className="grid grid-cols-2 gap-3">
              <div>
                <p className="text-2xl font-bold text-white">47</p>
                <p className="text-xs text-slate-400">Sesiones totales</p>
              </div>
              <div>
                <p className="text-2xl font-bold text-white">156</p>
                <p className="text-xs text-slate-400">Objetos rastreados</p>
              </div>
            </div>
          </div>
        </div>

        {/* Center Column - Main Content */}
        <div className="lg:col-span-2 space-y-6">
          {/* Mode selection section */}
          <div>
            <h2 className="text-white font-semibold mb-4 text-xl">Selecciona tu Modo de Rastreo</h2>
            <div className="space-y-4">
              {/* Manual Mode Button */}
              <button className="w-full group relative overflow-hidden bg-gradient-to-r from-blue-600 to-blue-700 hover:from-blue-700 hover:to-blue-800 text-white rounded-2xl p-6 shadow-xl transition-all duration-300 hover:shadow-2xl hover:scale-[1.02] active:scale-[0.98]">
                <div className="absolute inset-0 bg-white/10 opacity-0 group-hover:opacity-100 transition-opacity"></div>
                <div className="relative flex items-center gap-4">
                  <div className="bg-white/20 p-3 rounded-xl">
                    <Hand className="w-8 h-8" />
                  </div>
                  <div className="flex-1 text-left">
                    <h2 className="text-2xl font-semibold mb-1">Modo Manual</h2>
                    <p className="text-blue-100 text-sm">
                      Control total de la dirección y velocidad
                    </p>
                  </div>
                </div>
              </button>

              {/* Automatic Mode Button */}
              <button className="w-full group relative overflow-hidden bg-gradient-to-r from-purple-600 to-purple-700 hover:from-purple-700 hover:to-purple-800 text-white rounded-2xl p-6 shadow-xl transition-all duration-300 hover:shadow-2xl hover:scale-[1.02] active:scale-[0.98]">
                <div className="absolute inset-0 bg-white/10 opacity-0 group-hover:opacity-100 transition-opacity"></div>
                <div className="relative flex items-center gap-4">
                  <div className="bg-white/20 p-3 rounded-xl">
                    <Zap className="w-8 h-8" />
                  </div>
                  <div className="flex-1 text-left">
                    <h2 className="text-2xl font-semibold mb-1">Modo Automático</h2>
                    <p className="text-purple-100 text-sm">
                      Rastreo inteligente de objetos celestes
                    </p>
                  </div>
                </div>
              </button>
            </div>
          </div>

          {/* Featured Objects Today */}
          <div className="bg-white/5 backdrop-blur-md rounded-xl p-5 border border-white/10">
            <div className="flex items-center gap-2 mb-4">
              <Telescope className="w-5 h-5 text-yellow-400" />
              <h3 className="text-white font-semibold">Objetos Destacados Hoy</h3>
            </div>
            <div className="space-y-3">
              <div className="flex items-center justify-between p-3 bg-white/5 rounded-lg hover:bg-white/10 transition-colors cursor-pointer">
                <div className="flex items-center gap-3">
                  <Star className="w-5 h-5 text-yellow-400" fill="currentColor" />
                  <div>
                    <p className="text-white font-medium text-sm">Júpiter</p>
                    <p className="text-slate-400 text-xs">Visible 21:00 - 05:30</p>
                  </div>
                </div>
                <span className="text-xs text-green-400 font-semibold">Óptimo</span>
              </div>
              <div className="flex items-center justify-between p-3 bg-white/5 rounded-lg hover:bg-white/10 transition-colors cursor-pointer">
                <div className="flex items-center gap-3">
                  <Star className="w-5 h-5 text-red-400" fill="currentColor" />
                  <div>
                    <p className="text-white font-medium text-sm">Marte</p>
                    <p className="text-slate-400 text-xs">Visible 23:15 - 06:00</p>
                  </div>
                </div>
                <span className="text-xs text-yellow-400 font-semibold">Bueno</span>
              </div>
              <div className="flex items-center justify-between p-3 bg-white/5 rounded-lg hover:bg-white/10 transition-colors cursor-pointer">
                <div className="flex items-center gap-3">
                  <Star className="w-5 h-5 text-blue-400" fill="currentColor" />
                  <div>
                    <p className="text-white font-medium text-sm">M31 (Andrómeda)</p>
                    <p className="text-slate-400 text-xs">Toda la noche</p>
                  </div>
                </div>
                <span className="text-xs text-green-400 font-semibold">Óptimo</span>
              </div>
            </div>
          </div>

          {/* Recent Activity */}
          <div className="bg-white/5 backdrop-blur-md rounded-xl p-5 border border-white/10">
            <div className="flex items-center gap-2 mb-4">
              <History className="w-5 h-5 text-blue-400" />
              <h3 className="text-white font-semibold">Actividad Reciente</h3>
            </div>
            <div className="space-y-2 text-sm">
              <div className="flex items-center justify-between py-2">
                <span className="text-slate-300">Última sesión</span>
                <span className="text-white">Hace 2 días</span>
              </div>
              <div className="flex items-center justify-between py-2">
                <span className="text-slate-300">Último objeto</span>
                <span className="text-white">Saturno</span>
              </div>
              <div className="flex items-center justify-between py-2">
                <span className="text-slate-300">Tiempo total</span>
                <span className="text-white">42h 15m</span>
              </div>
            </div>
          </div>

          {/* System Status */}
          <div className="flex items-center justify-center gap-2 text-slate-400 text-sm py-4">
            <Info className="w-4 h-4" />
            <span>Sistema calibrado y listo para operar</span>
          </div>
        </div>
      </div>
    </>
  );
}
