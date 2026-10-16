import { ImageWithFallback } from './components/figma/ImageWithFallback';
import { Star, Settings, Menu, Wifi, WifiOff, X, User, HelpCircle, BookOpen, LogOut, Bell, Gauge, ArrowLeft, Telescope, History } from 'lucide-react';
import { useState } from 'react';
import { HomeView } from './components/views/HomeView';
import { HelpView } from './components/views/HelpView';

type ViewType = 'home' | 'observations' | 'history' | 'catalog' | 'calibration' | 'notifications' | 'help';

export default function App() {
  const [isConnected] = useState(true);
  const [showMenu, setShowMenu] = useState(false);
  const [showSettings, setShowSettings] = useState(false);
  const [currentView, setCurrentView] = useState<ViewType>('home');

  const navigateTo = (view: ViewType) => {
    setCurrentView(view);
    setShowMenu(false);
  };
  
  return (
    <div className="min-h-screen relative overflow-hidden bg-slate-950">
      {/* Background image with overlay */}
      <div className="absolute inset-0">
        <ImageWithFallback 
          src="https://images.unsplash.com/photo-1419242902214-272b3f66ee7a"
          alt="Starry night sky"
          className="w-full h-full object-cover opacity-40"
        />
        <div className="absolute inset-0 bg-gradient-to-b from-slate-950/50 via-slate-950/70 to-slate-950/90"></div>
      </div>

      {/* Side Menu */}
      {showMenu && (
        <>
          <div 
            className="fixed inset-0 bg-black/50 z-40 backdrop-blur-sm"
            onClick={() => setShowMenu(false)}
          ></div>
          <div className="fixed left-0 top-0 bottom-0 w-80 bg-slate-900/95 backdrop-blur-md z-50 border-r border-white/10 animate-slide-in">
            <div className="p-6">
              <div className="flex items-center justify-between mb-8">
                <div className="flex items-center gap-3">
                  <div className="bg-gradient-to-br from-blue-500 to-purple-600 p-2 rounded-lg">
                    <Star className="w-6 h-6 text-white" fill="white" />
                  </div>
                  <div>
                    <h2 className="text-white font-semibold">Seguidor de Estrellas</h2>
                    <p className="text-slate-400 text-xs">v2.4.1</p>
                  </div>
                </div>
                <button 
                  onClick={() => setShowMenu(false)}
                  className="p-2 hover:bg-white/10 rounded-lg transition-colors"
                >
                  <X className="w-5 h-5 text-white" />
                </button>
              </div>

              {/* User Profile */}
              <div className="bg-white/5 rounded-xl p-4 mb-6 border border-white/10">
                <div className="flex items-center gap-3">
                  <div className="bg-blue-500/20 p-3 rounded-full">
                    <User className="w-6 h-6 text-blue-400" />
                  </div>
                  <div>
                    <p className="text-white font-semibold">Usuario</p>
                    <p className="text-slate-400 text-sm">Astrónomo Amateur</p>
                  </div>
                </div>
              </div>

              {/* Menu Items */}
              <nav className="space-y-2">
                <button 
                  onClick={() => navigateTo('observations')}
                  className="w-full flex items-center gap-3 px-4 py-3 text-white hover:bg-white/10 rounded-lg transition-colors"
                >
                  <Telescope className="w-5 h-5" />
                  <span>Mis Observaciones</span>
                </button>
                <button 
                  onClick={() => navigateTo('history')}
                  className="w-full flex items-center gap-3 px-4 py-3 text-white hover:bg-white/10 rounded-lg transition-colors"
                >
                  <History className="w-5 h-5" />
                  <span>Historial</span>
                </button>
                <button 
                  onClick={() => navigateTo('catalog')}
                  className="w-full flex items-center gap-3 px-4 py-3 text-white hover:bg-white/10 rounded-lg transition-colors"
                >
                  <BookOpen className="w-5 h-5" />
                  <span>Catálogo Celeste</span>
                </button>
                <button 
                  onClick={() => navigateTo('calibration')}
                  className="w-full flex items-center gap-3 px-4 py-3 text-white hover:bg-white/10 rounded-lg transition-colors"
                >
                  <Gauge className="w-5 h-5" />
                  <span>Calibración</span>
                </button>
                <button 
                  onClick={() => navigateTo('notifications')}
                  className="w-full flex items-center gap-3 px-4 py-3 text-white hover:bg-white/10 rounded-lg transition-colors"
                >
                  <Bell className="w-5 h-5" />
                  <span>Notificaciones</span>
                  <span className="ml-auto bg-red-500 text-white text-xs px-2 py-0.5 rounded-full">3</span>
                </button>
                <button 
                  onClick={() => navigateTo('help')}
                  className="w-full flex items-center gap-3 px-4 py-3 text-white hover:bg-white/10 rounded-lg transition-colors"
                >
                  <HelpCircle className="w-5 h-5" />
                  <span>Ayuda y Soporte</span>
                </button>
              </nav>

              <div className="absolute bottom-6 left-6 right-6">
                <button className="w-full flex items-center gap-3 px-4 py-3 text-red-400 hover:bg-red-500/10 rounded-lg transition-colors">
                  <LogOut className="w-5 h-5" />
                  <span>Cerrar Sesión</span>
                </button>
              </div>
            </div>
          </div>
        </>
      )}

      {/* Settings Panel */}
      {showSettings && (
        <>
          <div 
            className="fixed inset-0 bg-black/50 z-40 backdrop-blur-sm"
            onClick={() => setShowSettings(false)}
          ></div>
          <div className="fixed right-0 top-0 bottom-0 w-96 bg-slate-900/95 backdrop-blur-md z-50 border-l border-white/10 animate-slide-in-right">
            <div className="p-6">
              <div className="flex items-center justify-between mb-8">
                <div className="flex items-center gap-3">
                  <Settings className="w-6 h-6 text-white" />
                  <h2 className="text-white font-semibold text-xl">Configuración</h2>
                </div>
                <button 
                  onClick={() => setShowSettings(false)}
                  className="p-2 hover:bg-white/10 rounded-lg transition-colors"
                >
                  <X className="w-5 h-5 text-white" />
                </button>
              </div>

              <div className="space-y-6">
                {/* Connection Settings */}
                <div>
                  <h3 className="text-white font-semibold mb-3">Conexión</h3>
                  <div className="bg-white/5 rounded-xl p-4 border border-white/10 space-y-3">
                    <div className="flex items-center justify-between">
                      <span className="text-slate-300 text-sm">Auto-reconectar</span>
                      <button className="w-12 h-6 bg-blue-500 rounded-full relative">
                        <div className="absolute right-1 top-1 w-4 h-4 bg-white rounded-full"></div>
                      </button>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-slate-300 text-sm">Puerto</span>
                      <span className="text-white text-sm">COM3</span>
                    </div>
                    <button className="w-full bg-blue-600 hover:bg-blue-700 text-white py-2 rounded-lg transition-colors text-sm">
                      Configurar Conexión
                    </button>
                  </div>
                </div>

                {/* Location Settings */}
                <div>
                  <h3 className="text-white font-semibold mb-3">Ubicación</h3>
                  <div className="bg-white/5 rounded-xl p-4 border border-white/10 space-y-3">
                    <div className="flex items-center justify-between">
                      <span className="text-slate-300 text-sm">GPS Automático</span>
                      <button className="w-12 h-6 bg-blue-500 rounded-full relative">
                        <div className="absolute right-1 top-1 w-4 h-4 bg-white rounded-full"></div>
                      </button>
                    </div>
                    <button className="w-full bg-slate-700 hover:bg-slate-600 text-white py-2 rounded-lg transition-colors text-sm">
                      Cambiar Ubicación
                    </button>
                  </div>
                </div>

                {/* Display Settings */}
                <div>
                  <h3 className="text-white font-semibold mb-3">Pantalla</h3>
                  <div className="bg-white/5 rounded-xl p-4 border border-white/10 space-y-3">
                    <div className="flex items-center justify-between">
                      <span className="text-slate-300 text-sm">Modo nocturno</span>
                      <button className="w-12 h-6 bg-blue-500 rounded-full relative">
                        <div className="absolute right-1 top-1 w-4 h-4 bg-white rounded-full"></div>
                      </button>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-slate-300 text-sm">Brillo</span>
                      <input type="range" className="w-32" />
                    </div>
                  </div>
                </div>

                {/* Tracking Settings */}
                <div>
                  <h3 className="text-white font-semibold mb-3">Rastreo</h3>
                  <div className="bg-white/5 rounded-xl p-4 border border-white/10 space-y-3">
                    <div className="flex items-center justify-between">
                      <span className="text-slate-300 text-sm">Velocidad predeterminada</span>
                      <select className="bg-slate-700 text-white px-3 py-1 rounded text-sm">
                        <option>Lenta</option>
                        <option>Media</option>
                        <option>Rápida</option>
                      </select>
                    </div>
                    <div className="flex items-center justify-between">
                      <span className="text-slate-300 text-sm">Precisión</span>
                      <select className="bg-slate-700 text-white px-3 py-1 rounded text-sm">
                        <option>Alta</option>
                        <option>Media</option>
                        <option>Baja</option>
                      </select>
                    </div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </>
      )}

      {/* Content */}
      <div className="relative z-10 min-h-screen px-6 py-6">
        {/* Header */}
        <header className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-3">
            <button 
              onClick={() => setShowMenu(true)}
              className="p-2 hover:bg-white/10 rounded-lg transition-colors"
            >
              <Menu className="w-6 h-6 text-white" />
            </button>
            {currentView !== 'home' && (
              <button 
                onClick={() => setCurrentView('home')}
                className="p-2 hover:bg-white/10 rounded-lg transition-colors"
              >
                <ArrowLeft className="w-6 h-6 text-white" />
              </button>
            )}
          </div>
          
          <div className="flex items-center gap-3">
            {/* Connection Status */}
            <div className={`flex items-center gap-2 px-3 py-2 rounded-full text-xs ${
              isConnected 
                ? 'bg-green-500/20 text-green-300' 
                : 'bg-red-500/20 text-red-300'
            }`}>
              {isConnected ? (
                <>
                  <Wifi className="w-3 h-3" />
                  <span>Conectado</span>
                </>
              ) : (
                <>
                  <WifiOff className="w-3 h-3" />
                  <span>Desconectado</span>
                </>
              )}
            </div>
            
            <button 
              onClick={() => setShowSettings(true)}
              className="p-2 hover:bg-white/10 rounded-lg transition-colors"
            >
              <Settings className="w-6 h-6 text-white" />
            </button>
          </div>
        </header>

        {/* Dynamic Content Based on View */}
        {currentView === 'home' && <HomeView />}
        {currentView === 'observations' && <div className="text-white text-center py-20">Vista de Observaciones - En desarrollo</div>}
        {currentView === 'history' && <div className="text-white text-center py-20">Vista de Historial - En desarrollo</div>}
        {currentView === 'catalog' && <div className="text-white text-center py-20">Vista de Catálogo - En desarrollo</div>}
        {currentView === 'calibration' && <div className="text-white text-center py-20">Vista de Calibración - En desarrollo</div>}
        {currentView === 'notifications' && <div className="text-white text-center py-20">Vista de Notificaciones - En desarrollo</div>}
        {currentView === 'help' && <HelpView />}

        {/* Decorative stars */}
        <div className="absolute top-20 left-10 animate-pulse">
          <Star className="w-4 h-4 text-blue-400" fill="currentColor" />
        </div>
        <div className="absolute top-40 right-20 animate-pulse delay-75">
          <Star className="w-3 h-3 text-purple-400" fill="currentColor" />
        </div>
        <div className="absolute bottom-32 left-1/4 animate-pulse delay-150">
          <Star className="w-2 h-2 text-white" fill="currentColor" />
        </div>
        <div className="absolute bottom-48 right-1/3 animate-pulse delay-300">
          <Star className="w-3 h-3 text-blue-300" fill="currentColor" />
        </div>
        <div className="absolute top-1/3 left-1/2 animate-pulse delay-500">
          <Star className="w-3 h-3 text-yellow-300" fill="currentColor" />
        </div>
      </div>

      <style>{`
        @keyframes slide-in {
          from {
            transform: translateX(-100%);
          }
          to {
            transform: translateX(0);
          }
        }
        
        @keyframes slide-in-right {
          from {
            transform: translateX(100%);
          }
          to {
            transform: translateX(0);
          }
        }
        
        .animate-slide-in {
          animation: slide-in 0.3s ease-out;
        }
        
        .animate-slide-in-right {
          animation: slide-in-right 0.3s ease-out;
        }
      `}</style>
    </div>
  );
}