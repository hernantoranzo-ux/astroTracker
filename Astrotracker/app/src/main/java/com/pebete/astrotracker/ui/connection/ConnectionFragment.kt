package com.pebete.astrotracker.ui.connection

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pebete.astrotracker.R
import com.pebete.astrotracker.data.bluetooth.ConnectionState
import com.pebete.astrotracker.databinding.FragmentConnectionBinding
import com.pebete.astrotracker.ui.MainActivity
import com.pebete.astrotracker.ui.Screen
import com.pebete.astrotracker.viewmodel.AstroViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class ConnectionFragment : Fragment() {

    private var _binding: FragmentConnectionBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AstroViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConnectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val host = requireActivity() as MainActivity

        binding.btnGrantPermission.setOnClickListener { host.requestBluetoothPermission() }
        binding.btnEnableBt.setOnClickListener { host.requestEnableBluetooth() }
        binding.btnOpenBtSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }

        binding.btnConnect.setOnClickListener {
            if (!host.hasBluetoothPermission()) {
                host.requestBluetoothPermission()
                return@setOnClickListener
            }
            
            val currentState = viewModel.uiState.value.connectionState
            if (currentState == ConnectionState.CONNECTED || currentState == ConnectionState.CONNECTING) {
                viewModel.onDisconnectClicked()
            } else {
                viewModel.onConnectClicked()
            }
        }

        // Selección de dispositivo en el RadioGroup
        binding.rgDevices.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId != View.NO_ID) {
                // El ID del RadioButton es el índice en la lista guardado por nosotros
                val view = binding.rgDevices.findViewById<RadioButton>(checkedId)
                val mac = view?.tag as? String
                if (mac != null) {
                    viewModel.onDeviceSelected(mac)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                
                // Observar cambios generales
                launch {
                    viewModel.uiState.collect { state ->
                        // 1. Permisos y estado del Bluetooth
                        val hasPerm = host.hasBluetoothPermission()
                        if (!hasPerm) {
                            binding.tvBtStatus.text = getString(R.string.conn_no_permission)
                            binding.btnGrantPermission.visibility = View.VISIBLE
                            binding.btnEnableBt.visibility = View.GONE
                            binding.btnOpenBtSettings.visibility = View.GONE
                        } else if (!state.isBluetoothEnabled) {
                            binding.tvBtStatus.text = getString(R.string.conn_bt_off)
                            binding.btnGrantPermission.visibility = View.GONE
                            binding.btnEnableBt.visibility = View.VISIBLE
                            binding.btnOpenBtSettings.visibility = View.GONE
                        } else {
                            binding.tvBtStatus.text = getString(R.string.conn_bt_on)
                            binding.btnGrantPermission.visibility = View.GONE
                            binding.btnEnableBt.visibility = View.GONE
                            binding.btnOpenBtSettings.visibility = View.VISIBLE
                        }

                        // 2. Estado de conexión
                        val connected = state.connectionState == ConnectionState.CONNECTED
                        val connecting = state.connectionState == ConnectionState.CONNECTING
                        
                        when {
                            connecting -> {
                                binding.btnConnect.text = getString(R.string.conn_connecting)
                                binding.btnConnect.isEnabled = false
                                binding.tvConnState.text = getString(R.string.conn_connecting)
                            }
                            connected -> {
                                binding.btnConnect.text = getString(R.string.conn_disconnect)
                                binding.btnConnect.isEnabled = true
                                binding.tvConnState.text = getString(R.string.conn_state_connected)
                            }
                            else -> {
                                binding.btnConnect.text = getString(R.string.conn_connect)
                                binding.btnConnect.isEnabled = true
                                binding.tvConnState.text = getString(R.string.conn_state_disconnected)
                            }
                        }

                        // Habilitar la selección solo si no estamos conectados ni conectando
                        val canSelect = !connected && !connecting
                        for (i in 0 until binding.rgDevices.childCount) {
                            binding.rgDevices.getChildAt(i).isEnabled = canSelect
                        }

                        // Error y reconexión
                        if (state.reconnectAttempt > 0 && !connected) {
                            binding.tvConnError.text = getString(
                                R.string.conn_reconnecting, 
                                state.reconnectAttempt, 
                                AstroViewModel.MAX_RECONNECT_ATTEMPTS
                            )
                            binding.tvConnError.visibility = View.VISIBLE
                        } else if (state.errorMessage != null) {
                            binding.tvConnError.text = state.errorMessage
                            binding.tvConnError.visibility = View.VISIBLE
                        } else {
                            binding.tvConnError.visibility = View.GONE
                        }
                    }
                }

                // Observar específicamente la lista de dispositivos (solo redibujar si cambia)
                launch {
                    viewModel.uiState.map { Pair(it.pairedDevices, it.selectedDeviceMac) }
                        .distinctUntilChanged()
                        .collect { (devices, selectedMac) ->
                            binding.rgDevices.removeAllViews()
                            if (devices.isEmpty()) {
                                binding.tvNoDevices.visibility = View.VISIBLE
                            } else {
                                binding.tvNoDevices.visibility = View.GONE
                                devices.forEachIndexed { index, device ->
                                    val rb = RadioButton(requireContext()).apply {
                                        id = View.generateViewId() // ID único
                                        text = "${device.name}\n${device.macAddress}"
                                        tag = device.macAddress // Guardamos la MAC en el tag
                                        setPadding(0, 16, 0, 16)
                                    }
                                    binding.rgDevices.addView(rb)
                                    if (device.macAddress == selectedMac) {
                                        binding.rgDevices.check(rb.id)
                                    }
                                }
                            }
                        }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val host = requireActivity() as MainActivity
        host.onScreenShown(Screen.CONNECTION)
        // Refrescar estado del Bluetooth al entrar por si el usuario lo cambió por fuera
        viewModel.refreshBluetoothInfo()
    }

    override fun onDestroyView() {
        // Limpiamos el mensaje de error persistente al salir de la pantalla
        viewModel.onErrorShown()
        super.onDestroyView()
        _binding = null
    }
}
