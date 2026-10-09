package com.pebete.astrotracker.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.pebete.astrotracker.R
import com.pebete.astrotracker.data.bluetooth.ConnectionState
import com.pebete.astrotracker.data.model.TelemetryState
import com.pebete.astrotracker.databinding.ActivityMainBinding
import com.pebete.astrotracker.ui.auto.AutoFragment
import com.pebete.astrotracker.ui.connection.ConnectionFragment
import com.pebete.astrotracker.ui.home.HomeFragment
import com.pebete.astrotracker.ui.manual.ManualFragment
import com.pebete.astrotracker.ui.settings.SettingsFragment
import com.pebete.astrotracker.ui.settings.TextFragment
import com.pebete.astrotracker.viewmodel.AstroViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Pantallas de la app. Cada una sabe su título y su ítem del menú lateral.
 * (enum class de Kotlin = enum de Java, pero puede llevar propiedades en el constructor.)
 */
enum class Screen(@StringRes val titleRes: Int, val navId: Int) {
    HOME(R.string.screen_home, R.id.nav_home),
    CONNECTION(R.string.screen_connection, R.id.nav_connection),
    MANUAL(R.string.screen_manual, R.id.nav_manual),
    AUTO(R.string.screen_auto, R.id.nav_auto),
    SETTINGS(R.string.screen_settings, R.id.nav_settings),
    OBSERVATIONS(R.string.screen_observations, R.id.nav_observations),
    HISTORY(R.string.screen_history, R.id.nav_history),
    CATALOG(R.string.screen_catalog, R.id.nav_catalog),
    NOTIFICATIONS(R.string.screen_notifications, R.id.nav_notifications),
    HELP(R.string.screen_help, R.id.nav_help)
}

/**
 * Única Activity de la app: aloja el menú lateral, la cabecera (título + estado de conexión) y el fragmento actual.
 * NO contiene lógica de negocio: observa el [AstroViewModel] y delega todo en él.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // by viewModels(): obtiene (o crea la primera vez) el ViewModel asociado a esta Activity.
    // Los fragmentos usan activityViewModels() y reciben EL MISMO objeto: así comparten estado.
    private val viewModel: AstroViewModel by viewModels()

    // Pantalla que está mostrándose (la actualiza cada fragmento en onResume vía onScreenShown)
    private var currentScreen: Screen = Screen.HOME

    // Lanzadores de "resultados de Activity": reemplazan al viejo onActivityResult().
    // Deben registrarse al crear la Activity (por eso son propiedades y no se crean dentro de un click).
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // Haya sido concedido o no, releemos dispositivos y estado del Bluetooth
            viewModel.refreshBluetoothInfo()
        }
    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            viewModel.refreshBluetoothInfo()
        }

    // Se entera cuando el usuario enciende/apaga el Bluetooth desde los ajustes del teléfono
    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            viewModel.refreshBluetoothInfo()
        }
    }

    // Solo está habilitado mientras el menú lateral está abierto: el botón "atrás" lo cierra
    private val drawerBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            binding.main.closeDrawer(GravityCompat.START)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Edge-to-edge: la app dibuja detrás de las barras del sistema, así que dejamos margen interno
        // para que el contenido no quede tapado (barras de estado/navegación y teclado).
        ViewCompat.setOnApplyWindowInsetsListener(binding.contentColumn) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setupNavigation()

        // Solo en el PRIMER arranque (savedInstanceState == null) mostramos Home.
        // Si la Activity se recrea, Android restaura solo el fragmento anterior.
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                replace(R.id.fragment_container, HomeFragment())
            }
        }

        observeViewModel()
    }

    // ------------------------------------------------------------------
    // CICLO DE VIDA: sensores y receptor solo con la app visible (ahorro de batería)
    // ------------------------------------------------------------------

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this, bluetoothStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        viewModel.refreshBluetoothInfo() // por si el usuario cambió algo mientras estábamos en segundo plano
        viewModel.onAppVisibilityChanged(true)
    }

    override fun onStop() {
        viewModel.onAppVisibilityChanged(false)
        unregisterReceiver(bluetoothStateReceiver)
        super.onStop()
    }

    // ------------------------------------------------------------------
    // NAVEGACIÓN
    // ------------------------------------------------------------------

    private fun setupNavigation() {
        binding.btnMenu.setOnClickListener { binding.main.openDrawer(GravityCompat.START) }
        binding.connectionChip.setOnClickListener { navigate(Screen.CONNECTION) }

        onBackPressedDispatcher.addCallback(this, drawerBackCallback)
        binding.main.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) { drawerBackCallback.isEnabled = true }
            override fun onDrawerClosed(drawerView: View) { drawerBackCallback.isEnabled = false }
        })

        binding.navView.setNavigationItemSelectedListener { item ->
            val target = when (item.itemId) {
                R.id.nav_home -> Screen.HOME
                R.id.nav_connection -> Screen.CONNECTION
                R.id.nav_manual -> Screen.MANUAL
                // "Calibración" vive dentro de Modo Automático (doc 04: setupCalibrationUI en AutoFragment)
                R.id.nav_auto, R.id.nav_calibration -> Screen.AUTO
                R.id.nav_settings -> Screen.SETTINGS
                R.id.nav_observations -> Screen.OBSERVATIONS
                R.id.nav_history -> Screen.HISTORY
                R.id.nav_catalog -> Screen.CATALOG
                R.id.nav_notifications -> Screen.NOTIFICATIONS
                R.id.nav_help -> Screen.HELP
                else -> null
            }
            target?.let { navigate(it) }
            true
        }
    }

    /** Cambia de pantalla. Home es la base; el resto se apila encima (el botón "atrás" vuelve a Home). */
    fun navigate(screen: Screen) {
        binding.main.closeDrawer(GravityCompat.START)
        if (screen == currentScreen) return

        val fm = supportFragmentManager
        // Vacía la pila: vuelve a Home, para no acumular pantallas al saltar entre secciones
        fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        if (screen != Screen.HOME) {
            fm.commit {
                setReorderingAllowed(true)
                replace(R.id.fragment_container, createFragment(screen))
                addToBackStack(null)
            }
        }
    }

    private fun createFragment(screen: Screen): Fragment = when (screen) {
        Screen.HOME -> HomeFragment()
        Screen.CONNECTION -> ConnectionFragment()
        Screen.MANUAL -> ManualFragment()
        Screen.AUTO -> AutoFragment()
        Screen.SETTINGS -> SettingsFragment()
        Screen.HELP -> TextFragment.newInstance(getString(R.string.help_body))
        // Secciones del diseño de Figma que todavía no tienen fuente de datos
        Screen.OBSERVATIONS, Screen.HISTORY, Screen.CATALOG, Screen.NOTIFICATIONS ->
            TextFragment.newInstance(getString(R.string.dev_text))
    }

    /** Los fragmentos lo llaman en onResume para que la cabecera y el menú reflejen la pantalla visible. */
    fun onScreenShown(screen: Screen) {
        currentScreen = screen
        binding.screenTitle.setText(screen.titleRes)
        binding.navView.setCheckedItem(screen.navId)
    }

    // ------------------------------------------------------------------
    // PERMISOS Y BLUETOOTH (los usa ConnectionFragment)
    // ------------------------------------------------------------------

    /** Antes de Android 12 no hay permiso de runtime para Bluetooth clásico: siempre true. */
    fun hasBluetoothPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    fun requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissionLauncher.launch(arrayOf(Manifest.permission.BLUETOOTH_CONNECT))
        }
    }

    fun requestEnableBluetooth() {
        // Pedir encender el Bluetooth también requiere el permiso en Android 12+
        if (!hasBluetoothPermission()) {
            requestBluetoothPermission()
            return
        }
        enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
    }

    // ------------------------------------------------------------------
    // OBSERVAR AL VIEWMODEL
    // ------------------------------------------------------------------

    private data class HeaderState(
        val connection: ConnectionState,
        val reconnectAttempt: Int,
        val stale: Boolean,
        val redMode: Boolean
    )

    private fun observeViewModel() {
        lifecycleScope.launch {
            // repeatOnLifecycle(STARTED): solo recolecta con la pantalla visible; al pasar a segundo plano
            // se cancela solo y al volver se reanuda. Evita trabajo (y batería) cuando nadie mira.
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    // uiState cambia muchas veces por segundo (sensor): nos quedamos con lo que usa la cabecera
                    // y distinctUntilChanged evita redibujar si no cambió nada de eso.
                    viewModel.uiState
                        .map {
                            HeaderState(
                                connection = it.connectionState,
                                reconnectAttempt = it.reconnectAttempt,
                                stale = it.connectionState == ConnectionState.CONNECTED &&
                                    it.telemetry?.state == TelemetryState.STALE,
                                redMode = it.redNightMode
                            )
                        }
                        .distinctUntilChanged()
                        .collect { renderHeader(it) }
                }
                launch {
                    viewModel.messages.collect { msg ->
                        Snackbar.make(binding.contentRoot, msg, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun renderHeader(state: HeaderState) {
        val reconnecting = state.reconnectAttempt > 0 && state.connection != ConnectionState.CONNECTED
        val text: String
        @ColorRes val color: Int
        when {
            reconnecting -> {
                text = getString(R.string.chip_reconnecting, state.reconnectAttempt, AstroViewModel.MAX_RECONNECT_ATTEMPTS)
                color = R.color.state_warn
            }
            state.connection == ConnectionState.CONNECTED -> { text = getString(R.string.chip_connected); color = R.color.state_ok }
            state.connection == ConnectionState.CONNECTING -> { text = getString(R.string.chip_connecting); color = R.color.state_warn }
            state.connection == ConnectionState.ERROR -> { text = getString(R.string.chip_error); color = R.color.state_danger }
            else -> { text = getString(R.string.chip_disconnected); color = R.color.state_danger }
        }
        binding.connectionChip.text = text
        binding.connectionChip.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, color))
        binding.staleBanner.visibility = if (state.stale) View.VISIBLE else View.GONE
        applyRedMode(state.redMode)
    }

    /**
     * Modo nocturno rojo: aplicamos a TODA la ventana un filtro de color que deja solo el canal rojo
     * (con la luminosidad de la imagen original). Así no hace falta un tema distinto ni recrear pantallas.
     * Limitación: los diálogos (ej. confirmación de "Fijar cero") son ventanas aparte y no se filtran.
     */
    private fun applyRedMode(enabled: Boolean) {
        val decor = window.decorView
        if (enabled) {
            val matrix = ColorMatrix(
                floatArrayOf(
                    0.33f, 0.59f, 0.11f, 0f, 0f, // R = luminancia
                    0f, 0f, 0f, 0f, 0f,          // G = 0
                    0f, 0f, 0f, 0f, 0f,          // B = 0
                    0f, 0f, 0f, 1f, 0f           // A igual
                )
            )
            val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
            decor.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
        } else {
            decor.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }
}