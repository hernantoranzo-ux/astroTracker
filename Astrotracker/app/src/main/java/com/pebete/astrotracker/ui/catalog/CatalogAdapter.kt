package com.pebete.astrotracker.ui.catalog

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.pebete.astrotracker.R
import com.pebete.astrotracker.data.catalog.CelestialObject
import com.pebete.astrotracker.data.catalog.ObjectType
import com.pebete.astrotracker.databinding.ItemCelestialObjectBinding
import java.util.Locale

/** Un objeto del catálogo junto con su posición calculada para este instante y lugar. */
data class CatalogRow(
    val obj: CelestialObject,
    val azimuthDeg: Double,
    val altitudeDeg: Double
) {
    val isAboveHorizon: Boolean get() = altitudeDeg > 0.0
}

class CatalogAdapter(
    private val onPoint: (CatalogRow) -> Unit
) : ListAdapter<CatalogRow, CatalogAdapter.ViewHolder>(Diff) {

    private object Diff : DiffUtil.ItemCallback<CatalogRow>() {
        override fun areItemsTheSame(a: CatalogRow, b: CatalogRow) = a.obj.id == b.obj.id
        override fun areContentsTheSame(a: CatalogRow, b: CatalogRow) = a == b
    }

    inner class ViewHolder(private val b: ItemCelestialObjectBinding) : RecyclerView.ViewHolder(b.root) {
        fun bind(row: CatalogRow) {
            val ctx = b.root.context
            val obj = row.obj

            b.tvName.setText(obj.nameRes)

            val typeText = ctx.getString(
                when (obj.type) {
                    ObjectType.SOLAR_SYSTEM -> R.string.catalog_type_solar
                    ObjectType.DEEP_SPACE -> R.string.catalog_type_deep
                    ObjectType.STAR -> R.string.catalog_type_star
                }
            )
            b.tvType.text = obj.constellationRes
                ?.let { ctx.getString(R.string.catalog_type_with_constellation, typeText, ctx.getString(it)) }
                ?: typeText

            b.tvDesc.setText(obj.descriptionRes)

            val mag = String.format(Locale.US, "%.1f", obj.magnitude)
            b.tvMagnitude.text = ctx.getString(
                if (obj.magnitudeIsTypical) R.string.catalog_magnitude_typical else R.string.catalog_magnitude, mag
            )

            b.tvCoords.text = ctx.getString(
                R.string.catalog_coords,
                String.format(Locale.US, "%.1f", row.azimuthDeg),
                String.format(Locale.US, "%.1f", row.altitudeDeg)
            )

            b.tvHorizon.setText(if (row.isAboveHorizon) R.string.catalog_above_horizon else R.string.catalog_below_horizon)
            b.tvHorizon.setTextColor(
                ContextCompat.getColor(ctx, if (row.isAboveHorizon) R.color.state_ok else R.color.state_warn)
            )

            // Bajo el horizonte no se puede apuntar (altitud negativa): el botón se deshabilita
            b.btnPoint.isEnabled = row.isAboveHorizon
            b.btnPoint.setText(if (row.isAboveHorizon) R.string.catalog_point else R.string.catalog_not_visible)
            b.btnPoint.setOnClickListener { onPoint(row) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(ItemCelestialObjectBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))
}
