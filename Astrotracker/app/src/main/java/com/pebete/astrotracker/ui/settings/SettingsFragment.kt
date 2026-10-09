package com.pebete.astrotracker.ui.settings

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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

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

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    if (!binding.swAutoReconnect.isPressed) {
                        binding.swAutoReconnect.isChecked = state.autoReconnect
                    }
                    if (!binding.swRedMode.isPressed) {
                        binding.swRedMode.isChecked = state.redNightMode
                    }
                    
                    binding.tvSettingsSteps.text = getString(R.string.settings_steps, String.format(Locale.US, "%.2f", state.stepsPerDegree))
                }
            }
        }
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
