package com.pebete.astrotracker.ui.auto

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
import com.pebete.astrotracker.data.model.AppMode
import com.pebete.astrotracker.data.model.TelemetryState
import com.pebete.astrotracker.databinding.FragmentAutoBinding
import com.pebete.astrotracker.ui.MainActivity
import com.pebete.astrotracker.ui.Screen
import com.pebete.astrotracker.viewmodel.AstroViewModel
import kotlinx.coroutines.launch
import java.util.Locale

class AutoFragment : Fragment() {

    private var _binding: FragmentAutoBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AstroViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAutoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val host = requireActivity() as MainActivity
        binding.btnBackManual.setOnClickListener { host.navigate(Screen.MANUAL) }
        
        binding.btnActivateAuto.setOnClickListener {
            viewModel.onSwitchMode(AppMode.AUTOMATIC)
        }

        binding.btnPoint.setOnClickListener {
            val azStr = binding.etAz.text.toString().replace(',', '.')
            val altStr = binding.etAlt.text.toString().replace(',', '.')
            
            var valid = true
            val az = azStr.toFloatOrNull()
            if (az == null) {
                binding.tilAz.error = getString(R.string.err_required)
                valid = false
            } else if (az !in 0f..360f) {
                binding.tilAz.error = getString(R.string.err_az_range)
                valid = false
            } else {
                binding.tilAz.error = null
            }
            
            val alt = altStr.toFloatOrNull()
            if (alt == null) {
                binding.tilAlt.error = getString(R.string.err_required)
                valid = false
            } else if (alt !in -90f..90f) {
                binding.tilAlt.error = getString(R.string.err_alt_range)
                valid = false
            } else {
                binding.tilAlt.error = null
            }

            if (valid) {
                viewModel.onSetTarget(az!!, alt!!)
            }
        }

        binding.swTracking.setOnCheckedChangeListener { buttonView, isChecked ->
            // Evitar ciclos infinitos si el estado viene del ViewModel
            if (buttonView.isPressed) {
                viewModel.onTrackingToggle(isChecked)
            }
        }

        binding.btnApplySpeed.setOnClickListener {
            val speedStr = binding.etSpeed.text.toString().replace(',', '.')
            val speed = speedStr.toFloatOrNull()
            if (speed == null) {
                binding.tilSpeed.error = getString(R.string.err_required)
            } else if (speed <= 0) {
                binding.tilSpeed.error = getString(R.string.err_positive)
            } else {
                binding.tilSpeed.error = null
                viewModel.onSpeedChanged(speed)
            }
        }

        // Calibración
        binding.btnCalStart.setOnClickListener { viewModel.onCalibrationStart() }
        binding.btnCalCancel.setOnClickListener { viewModel.onCalibrationCancel() }
        binding.btnCalApply.setOnClickListener {
            val calStr = binding.etCal.text.toString().replace(',', '.')
            val cal = calStr.toFloatOrNull()
            if (cal == null) {
                binding.tilCal.error = getString(R.string.err_required)
            } else if (cal <= 0) {
                binding.tilCal.error = getString(R.string.err_positive)
            } else {
                binding.tilCal.error = null
                viewModel.onCalibrationValue(cal)
            }
        }

        observeViewModel()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    val connected = state.connectionState == ConnectionState.CONNECTED
                    val isAuto = state.mode == AppMode.AUTOMATIC
                    
                    // Activar automático
                    binding.cardActivate.visibility = if (connected && !isAuto) View.VISIBLE else View.GONE
                    binding.autoControls.visibility = if (connected && isAuto) View.VISIBLE else View.GONE

                    // Sensor
                    binding.tvNoSensor.visibility = if (!state.isSensorAvailable) View.VISIBLE else View.GONE

                    // Telemetría
                    val tel = state.telemetry
                    if (!connected) {
                        binding.tvTelLink.text = getString(R.string.link_none)
                        binding.tvTelAz.text = getString(R.string.value_unknown)
                        binding.tvTelAlt.text = getString(R.string.value_unknown)
                        binding.tvTelError.text = getString(R.string.value_unknown)
                        binding.tvTelTracking.text = getString(R.string.value_unknown)
                    } else if (tel == null) {
                        binding.tvTelLink.text = getString(R.string.link_waiting)
                        binding.tvTelAz.text = getString(R.string.value_unknown)
                        binding.tvTelAlt.text = getString(R.string.value_unknown)
                        binding.tvTelError.text = getString(R.string.value_unknown)
                        binding.tvTelTracking.text = getString(R.string.value_unknown)
                    } else {
                        binding.tvTelLink.text = if (tel.state == TelemetryState.STALE) getString(R.string.link_stale) else getString(R.string.link_ok)
                        binding.tvTelAz.text = getString(R.string.fmt_degrees, tel.azActual)
                        binding.tvTelAlt.text = getString(R.string.fmt_degrees, tel.altActual)
                        binding.tvTelError.text = getString(R.string.fmt_degrees, tel.errorAz)
                        binding.tvTelTracking.text = if (tel.isTracking) getString(R.string.tel_on) else getString(R.string.tel_off)
                        
                        // Actualizar switch sin disparar listener recursivo
                        if (!binding.swTracking.isPressed) {
                            binding.swTracking.isChecked = tel.isTracking
                        }
                    }

                    // Calibración
                    val srcStr = if (state.isStepsCalibrated) getString(R.string.cal_info_saved) else getString(R.string.cal_info_default)
                    binding.tvCalInfo.text = getString(R.string.cal_info, String.format(Locale.US, "%.2f", state.stepsPerDegree), srcStr)

                    if (state.isCalibrating) {
                        binding.btnCalStart.visibility = View.GONE
                        binding.calStep2.visibility = View.VISIBLE
                    } else {
                        binding.btnCalStart.visibility = View.VISIBLE
                        binding.calStep2.visibility = View.GONE
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as MainActivity).onScreenShown(Screen.AUTO)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
