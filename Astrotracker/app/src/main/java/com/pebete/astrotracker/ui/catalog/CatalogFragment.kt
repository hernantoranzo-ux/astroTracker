package com.pebete.astrotracker.ui.catalog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import com.pebete.astrotracker.R
import com.pebete.astrotracker.data.bluetooth.ConnectionState
import com.pebete.astrotracker.data.catalog.CelestialCatalog
import com.pebete.astrotracker.data.catalog.CelestialObject
import com.pebete.astrotracker.data.catalog.ObjectType
import com.pebete.astrotracker.data.catalog.SkyCalculator
import com.pebete.astrotracker.databinding.FragmentCatalogBinding
import com.pebete.astrotracker.ui.MainActivity
import com.pebete.astrotracker.ui.Screen
import com.pebete.astrotracker.viewmodel.AstroViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale
import kotlin.math.round

class CatalogFragment : Fragment() {

    private companion object {
        // El cielo se mueve ~15°/h => cada 30 s son ~0,13°: suficiente para mantener los datos "vivos"
        const val REFRESH_MS = 30_000L
    }

    private var _binding: FragmentCatalogBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AstroViewModel by activityViewModels()
    private lateinit var adapter: CatalogAdapter

    private var query = ""
    private var typeFilter: ObjectType? = null // null = todos

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCatalogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = CatalogAdapter { row -> onPointClicked(row.obj) }
        binding.rvCatalog.adapter = adapter

        binding.etSearch.doOnTextChanged { text, _, _, _ ->
            query = text?.toString().orEmpty().trim()
            rebuild()
        }

        binding.chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            typeFilter = when (checkedIds.firstOrNull()) {
                R.id.chip_planets -> ObjectType.SOLAR_SYSTEM
                R.id.chip_deep -> ObjectType.DEEP_SPACE
                R.id.chip_stars -> ObjectType.STAR
                else -> null
            }
            rebuild()
        }

        binding.btnConfigureLocation.setOnClickListener {
            (requireActivity() as MainActivity).navigate(Screen.SETTINGS)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Recalcula cuando cambia la ubicación guardada...
                launch {
                    viewModel.uiState
                        .map { it.observerLat to it.observerLon }
                        .distinctUntilChanged()
                        .collect { rebuild() }
                }
                // ...y periódicamente mientras la pantalla está visible
                launch {
                    while (isActive) {
                        delay(REFRESH_MS)
                        rebuild()
                    }
                }
            }
        }
    }

    /** Recalcula posiciones y filtra. Se llama al buscar, filtrar, cambiar la ubicación y cada 30 s. */
    private fun rebuild() {
        val b = _binding ?: return
        val state = viewModel.uiState.value
        val lat = state.observerLat
        val lon = state.observerLon

        if (lat == null || lon == null) {
            b.cardNoLocation.visibility = View.VISIBLE
            b.tvEmpty.visibility = View.GONE
            adapter.submitList(emptyList())
            return
        }
        b.cardNoLocation.visibility = View.GONE

        val jd = SkyCalculator.julianDay(System.currentTimeMillis())
        val rows = CelestialCatalog.objects
            .filter { obj -> (typeFilter == null || obj.type == typeFilter) && matchesQuery(obj) }
            .map { obj ->
                val h = CelestialCatalog.horizontalOf(obj, jd, lat, lon)
                CatalogRow(obj, h.azimuthDeg, h.altitudeDeg)
            }

        adapter.submitList(rows)
        b.tvEmpty.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
    }

    /** Busca por nombre, tipo o constelación, sin distinguir mayúsculas ni tildes ("orion" encuentra "Orión"). */
    private fun matchesQuery(obj: CelestialObject): Boolean {
        if (query.isEmpty()) return true
        val ctx = requireContext()
        val typeRes = when (obj.type) {
            ObjectType.SOLAR_SYSTEM -> R.string.catalog_type_solar
            ObjectType.DEEP_SPACE -> R.string.catalog_type_deep
            ObjectType.STAR -> R.string.catalog_type_star
        }
        val haystack = normalize(
            ctx.getString(obj.nameRes) + " " + ctx.getString(typeRes) + " " +
                (obj.constellationRes?.let { ctx.getString(it) } ?: "")
        )
        return haystack.contains(normalize(query))
    }

    private fun normalize(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .lowercase(Locale.ROOT)

    private fun onPointClicked(obj: CelestialObject) {
        val host = requireActivity() as MainActivity
        val state = viewModel.uiState.value

        if (state.connectionState != ConnectionState.CONNECTED) {
            // Usamos la vista de la Activity: el aviso sobrevive al cambio de pantalla
            Snackbar.make(host.findViewById(android.R.id.content), R.string.catalog_need_connection, Snackbar.LENGTH_LONG).show()
            host.navigate(Screen.CONNECTION)
            return
        }
        val lat = state.observerLat ?: return
        val lon = state.observerLon ?: return

        // Posición fresca en el momento del toque (la fila mostrada puede tener hasta 30 s)
        val jd = SkyCalculator.julianDay(System.currentTimeMillis())
        val h = CelestialCatalog.horizontalOf(obj, jd, lat, lon)
        if (h.altitudeDeg < 0.0) return

        val az = (round(h.azimuthDeg * 100) / 100).toFloat()
        val alt = (round(h.altitudeDeg * 100) / 100).toFloat()
        viewModel.setPendingTarget(az, alt)
        host.navigate(Screen.AUTO) // AutoFragment precarga los campos; el usuario confirma con "Apuntar"
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as MainActivity).onScreenShown(Screen.CATALOG)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.rvCatalog.adapter = null
        _binding = null
    }
}
