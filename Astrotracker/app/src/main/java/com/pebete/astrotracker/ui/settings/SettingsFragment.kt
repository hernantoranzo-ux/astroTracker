package com.pebete.astrotracker.ui.settings

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.pebete.astrotracker.R
import com.pebete.astrotracker.databinding.FragmentSettingsBinding
import com.pebete.astrotracker.ui.MainActivity
import com.pebete.astrotracker.ui.Screen
import com.pebete.astrotracker.viewmodel.AstroViewModel
import kotlinx.coroutines.launch
import java.util.Locale

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AstroViewModel by activityViewModels()

    // Permiso de ubicación APROXIMADA (alcanza para calcular el cielo). Se registra al crear el fragmento.
    private val locationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) fetchPhoneLocation() else showMessage(R.string.settings_location_denied)
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Nombre del usuario: se precarga UNA vez con el valor guardado (no en cada emisión del estado,
        // para no pisar lo que el usuario está escribiendo).
        binding.etUserName.setText(viewModel.uiState.value.userName)
        binding.btnSaveName.setOnClickListener { saveUserName() }
        binding.etUserName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) { saveUserName(); true } else false
        }

        // Ubicación: también se precarga una sola vez
        viewModel.uiState.value.let { s ->
            s.observerLat?.let { binding.etLat.setText(formatCoord(it)) }
            s.observerLon?.let { binding.etLon.setText(formatCoord(it)) }
        }
        binding.btnSaveLocation.setOnClickListener { saveManualLocation() }
        binding.btnPhoneLocation.setOnClickListener { onPhoneLocationClicked() }

        binding.btnForgetDevice.setOnClickListener {
            viewModel.onForgetDevice()
            showMessage(R.string.settings_device_forgotten)
        }

        binding.swAutoReconnect.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed) {
                viewModel.onAutoReconnectChanged(isChecked)
            }
        }

        binding.swRedMode.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed) {
                viewModel.onRedNightModeChanged(isChecked)
            }
        }

        binding.swSensorsBg.setOnCheckedChangeListener { buttonView, isChecked ->
            if (buttonView.isPressed) {
                viewModel.onSensorsOffInBackgroundChanged(isChecked)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (!binding.swAutoReconnect.isPressed) {
                        binding.swAutoReconnect.isChecked = state.autoReconnect
                    }
                    if (!binding.swRedMode.isPressed) {
                        binding.swRedMode.isChecked = state.redNightMode
                    }
                    if (!binding.swSensorsBg.isPressed) {
                        binding.swSensorsBg.isChecked = state.sensorsOffInBackground
                    }

                    binding.tvSettingsSteps.text = getString(R.string.settings_steps, String.format(Locale.US, "%.2f", state.stepsPerDegree))

                    // Último telescopio: mostramos el nombre si sigue emparejado, y siempre la MAC
                    val mac = state.lastDeviceMac
                    binding.btnForgetDevice.isEnabled = mac != null
                    binding.tvLastDevice.text = if (mac == null) {
                        getString(R.string.settings_device_none)
                    } else {
                        val name = state.pairedDevices.firstOrNull { it.macAddress == mac }?.name
                        if (name != null) getString(R.string.settings_device_value, name, mac) else mac
                    }

                    // Ubicación guardada
                    val lat = state.observerLat
                    val lon = state.observerLon
                    binding.tvLocationStatus.text = if (lat == null || lon == null) {
                        getString(R.string.settings_location_none)
                    } else {
                        getString(R.string.settings_location_value, formatCoord(lat), formatCoord(lon))
                    }
                }
            }
        }
    }

    private fun formatCoord(value: Double) = String.format(Locale.US, "%.4f", value)

    private fun showMessage(@StringRes res: Int) {
        _binding?.let { Snackbar.make(it.root, res, Snackbar.LENGTH_LONG).show() }
    }

    // ------------------------------------------------------------------
    // UBICACIÓN
    // ------------------------------------------------------------------

    private fun saveManualLocation() {
        val lat = binding.etLat.text?.toString().orEmpty().replace(',', '.').toDoubleOrNull()
        val lon = binding.etLon.text?.toString().orEmpty().replace(',', '.').toDoubleOrNull()
        if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            showMessage(R.string.settings_location_invalid)
            return
        }
        viewModel.onObserverLocationChanged(lat, lon)
        showMessage(R.string.settings_location_saved)
    }

    private fun onPhoneLocationClicked() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) fetchPhoneLocation()
        else locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    /** Usa la última ubicación conocida (rápido); si no hay, pide una nueva por red (Android 11+). */
    @SuppressLint("MissingPermission") // el permiso se verifica antes de llamar a esta función
    private fun fetchPhoneLocation() {
        val lm = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val lastKnown = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { provider -> runCatching { lm.getLastKnownLocation(provider) }.getOrNull() }
            .maxByOrNull { it.time }
        if (lastKnown != null) {
            applyPhoneLocation(lastKnown)
            return
        }

        val networkOn = runCatching { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && networkOn) {
            runCatching {
                lm.getCurrentLocation(
                    LocationManager.NETWORK_PROVIDER, null, ContextCompat.getMainExecutor(requireContext())
                ) { location ->
                    if (location != null) applyPhoneLocation(location) else showMessage(R.string.settings_location_unavailable)
                }
            }.onFailure { showMessage(R.string.settings_location_unavailable) }
        } else {
            showMessage(R.string.settings_location_unavailable)
        }
    }

    private fun applyPhoneLocation(location: Location) {
        val b = _binding ?: return // el fragmento pudo destruirse mientras esperábamos la respuesta
        viewModel.onObserverLocationChanged(location.latitude, location.longitude)
        b.etLat.setText(formatCoord(location.latitude))
        b.etLon.setText(formatCoord(location.longitude))
        showMessage(R.string.settings_location_saved)
    }

    private fun saveUserName() {
        viewModel.onUserNameChanged(binding.etUserName.text?.toString().orEmpty())
        // Reflejamos el valor normalizado (si lo dejó vacío vuelve al nombre por defecto)
        binding.etUserName.setText(viewModel.uiState.value.userName)
        binding.etUserName.clearFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etUserName.windowToken, 0)
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as MainActivity).onScreenShown(Screen.SETTINGS)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
