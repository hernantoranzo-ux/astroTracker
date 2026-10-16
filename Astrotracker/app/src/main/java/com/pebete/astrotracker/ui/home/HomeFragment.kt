package com.pebete.astrotracker.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.pebete.astrotracker.R
import com.pebete.astrotracker.data.bluetooth.ConnectionState
import com.pebete.astrotracker.databinding.FragmentHomeBinding
import com.pebete.astrotracker.ui.MainActivity
import com.pebete.astrotracker.ui.Screen
import com.pebete.astrotracker.viewmodel.AstroViewModel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    // activityViewModels(): obtenemos el mismo ViewModel que la MainActivity y el resto de fragmentos.
    private val viewModel: AstroViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val host = requireActivity() as MainActivity

        binding.btnModeManual.setOnClickListener {
            if (viewModel.uiState.value.connectionState == ConnectionState.CONNECTED) {
                host.navigate(Screen.MANUAL)
            } else {
                host.navigate(Screen.CONNECTION)
            }
        }

        binding.btnModeAuto.setOnClickListener {
            if (viewModel.uiState.value.connectionState == ConnectionState.CONNECTED) {
                host.navigate(Screen.AUTO)
            } else {
                host.navigate(Screen.CONNECTION)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState
                    .map { state ->
                        Pair(state.connectionState, state.selectedDeviceMac)
                    }
                    .distinctUntilChanged()
                    .collect { (connection, deviceMac) ->
                        val connected = connection == ConnectionState.CONNECTED
                        
                        // Si está conectado muestra el dispositivo, sino el estado
                        if (connected) {
                            val deviceName = viewModel.uiState.value.pairedDevices.find { it.macAddress == deviceMac }?.name ?: "Dispositivo"
                            binding.homeStatus.text = "Conectado a:"
                            binding.homeDevice.text = deviceName
                            binding.homeDevice.visibility = View.VISIBLE
                            binding.homeHint.text = getString(R.string.home_hint_ready)
                        } else {
                            binding.homeStatus.text = "Estado: Desconectado"
                            binding.homeDevice.visibility = View.GONE
                            binding.homeHint.text = getString(R.string.home_hint_disconnected)
                        }
                    }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as MainActivity).onScreenShown(Screen.HOME)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
