package com.pebete.astrotracker.ui.manual

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.pebete.astrotracker.R
import com.pebete.astrotracker.data.bluetooth.ConnectionState
import com.pebete.astrotracker.data.model.AppMode
import com.pebete.astrotracker.data.model.TelemetryState
import com.pebete.astrotracker.databinding.FragmentManualBinding
import com.pebete.astrotracker.protocol.CommandProtocol
import com.pebete.astrotracker.ui.MainActivity
import com.pebete.astrotracker.ui.Screen
import com.pebete.astrotracker.viewmodel.AstroViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ManualFragment : Fragment() {

    private var _binding: FragmentManualBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AstroViewModel by activityViewModels()

    // Para repetir el comando mientras el usuario mantiene presionado
    private var repeatJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentManualBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupButtons()
        observeViewModel()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupButtons() {
        val host = requireActivity() as MainActivity
        
        binding.btnGoAuto.setOnClickListener { host.navigate(Screen.AUTO) }
        
        binding.btnBackToManual.setOnClickListener { 
            viewModel.onSwitchMode(AppMode.MANUAL)
        }

        binding.btnRequestPos.setOnClickListener { viewModel.onManualCommand(CommandProtocol.MANUAL_REQUEST_POS) }
        binding.btnSpeedUp.setOnClickListener { viewModel.onManualCommand(CommandProtocol.MANUAL_SPEED_UP) }
        binding.btnSpeedDown.setOnClickListener { viewModel.onManualCommand(CommandProtocol.MANUAL_SPEED_DOWN) }

        binding.btnSetZero.setOnClickListener { showConfirmDialog(CommandProtocol.MANUAL_SET_ZERO) }
        binding.btnSetMax.setOnClickListener { showConfirmDialog(CommandProtocol.MANUAL_SET_MAX) }

        // Configuración de los botones del D-pad para repetir mientras se presionan
        setupRepeatButton(binding.btnUp, CommandProtocol.MANUAL_FORWARD)
        setupRepeatButton(binding.btnDown, CommandProtocol.MANUAL_BACK)
        setupRepeatButton(binding.btnLeft, CommandProtocol.MANUAL_LEFT)
        setupRepeatButton(binding.btnRight, CommandProtocol.MANUAL_RIGHT)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupRepeatButton(button: View, command: Char) {
        button.setOnTouchListener { v, event ->
            // Solo actuar si está habilitado (conectado y en modo manual)
            if (!v.isEnabled) return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    startRepeating(command)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    stopRepeating()
                    // Si soltaron dentro del botón, cuenta como click
                    if (event.action == MotionEvent.ACTION_UP) {
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun startRepeating(command: Char) {
        repeatJob?.cancel()
        repeatJob = viewLifecycleOwner.lifecycleScope.launch {
            while (true) {
                // Enviar el comando
                viewModel.onManualCommand(command)
                // Esperar un poco antes del siguiente (unos 100ms debería dar fluidez sin saturar)
                delay(100)
            }
        }
    }

    private fun stopRepeating() {
        repeatJob?.cancel()
        repeatJob = null
    }

    private fun showConfirmDialog(command: Char) {
        val title = if (command == CommandProtocol.MANUAL_SET_ZERO) R.string.dialog_zero_title else R.string.dialog_max_title
        val msg = if (command == CommandProtocol.MANUAL_SET_ZERO) R.string.dialog_zero_msg else R.string.dialog_max_msg

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(title)
            .setMessage(msg)
            .setPositiveButton(R.string.dialog_confirm) { _, _ ->
                viewModel.onManualCommand(command)
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val connected = state.connectionState == ConnectionState.CONNECTED
                    val isManual = state.mode == AppMode.MANUAL

                    // Habilitar/Deshabilitar controles
                    val canControl = connected && isManual
                    binding.btnRequestPos.isEnabled = canControl
                    binding.btnSetZero.isEnabled = canControl
                    binding.btnSetMax.isEnabled = canControl
                    binding.btnSpeedUp.isEnabled = canControl
                    binding.btnSpeedDown.isEnabled = canControl
                    binding.btnUp.isEnabled = canControl
                    binding.btnDown.isEnabled = canControl
                    binding.btnLeft.isEnabled = canControl
                    binding.btnRight.isEnabled = canControl
                    
                    // Detener movimiento si se desconecta o cambia de modo
                    if (!canControl) stopRepeating()

                    // Mostrar aviso si está en modo automático
                    binding.cardInAuto.visibility = if (connected && !isManual) View.VISIBLE else View.GONE

                    // Estado de los sensores (Orientación del teléfono)
                    if (!state.isSensorAvailable) {
                        binding.tvSensorNote.text = getString(R.string.manual_no_sensor)
                        binding.tvSensorNote.setTextColor(requireContext().getColor(R.color.state_warn))
                    } else {
                        binding.tvSensorNote.text = getString(R.string.manual_phone_note)
                        binding.tvSensorNote.setTextColor(requireContext().getColor(R.color.text_secondary))
                    }

                    if (state.phonePosition != null) {
                        binding.tvAz.text = getString(R.string.fmt_degrees, state.phonePosition.azimuth)
                        binding.tvAlt.text = getString(R.string.fmt_degrees, state.phonePosition.altitude)
                    } else {
                        binding.tvAz.text = getString(R.string.value_unknown)
                        binding.tvAlt.text = getString(R.string.value_unknown)
                    }

                    // Enlace y Posición de los motores
                    if (connected) {
                        val tel = state.telemetry
                        if (tel == null) {
                            binding.tvLink.text = getString(R.string.link_waiting)
                            binding.tvLink.setTextColor(requireContext().getColor(R.color.state_warn))
                        } else if (tel.state == TelemetryState.STALE) {
                            binding.tvLink.text = getString(R.string.link_stale)
                            binding.tvLink.setTextColor(requireContext().getColor(R.color.state_danger))
                        } else {
                            binding.tvLink.text = getString(R.string.link_ok)
                            binding.tvLink.setTextColor(requireContext().getColor(R.color.state_ok))
                        }
                    } else {
                        binding.tvLink.text = getString(R.string.link_none)
                        binding.tvLink.setTextColor(requireContext().getColor(R.color.text_secondary))
                    }

                    if (state.motorPosition != null && connected) {
                        binding.tvMotors.text = getString(R.string.manual_pos_format, state.motorPosition.stepsX, state.motorPosition.stepsY)
                    } else {
                        binding.tvMotors.text = getString(R.string.manual_pos_unknown)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as MainActivity).onScreenShown(Screen.MANUAL)
        
        // Solo para asegurar que cambiamos al modo manual al entrar, si estamos conectados y en AUTO
        // (Aunque lo mejor es que el usuario lo cambie con el botón, podemos dejarlo opcional. 
        //  Por ahora respetamos el card_in_auto para que sea explícito).
    }

    override fun onStop() {
        stopRepeating() // Por si el usuario sale de la app pulsando el botón
        super.onStop()
    }

    override fun onDestroyView() {
        stopRepeating()
        super.onDestroyView()
        _binding = null
    }
}
