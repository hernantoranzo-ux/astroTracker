package com.pebete.astrotracker.ui.help

import android.content.Context
import android.transition.TransitionManager
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.pebete.astrotracker.R

/**
 * Tarjeta "acordeón": muestra solo el título y, al tocarla, despliega el detalle (y gira la flecha).
 * Es un LinearLayout vertical que infla view_expandable_card.xml dentro de sí mismo (<merge>),
 * así se reutiliza para cada pregunta de la pantalla de Ayuda.
 *
 * @JvmOverloads genera los constructores de Java que Android necesita al inflar desde XML.
 */
class ExpandableCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val header: View
    private val titleView: TextView
    private val bodyView: TextView
    private val chevron: ImageView
    private val iconView: ImageView

    var isExpanded = false
        private set

    init {
        orientation = VERTICAL
        background = ContextCompat.getDrawable(context, R.drawable.bg_glass_card)
        LayoutInflater.from(context).inflate(R.layout.view_expandable_card, this, true)
        header = findViewById(R.id.exp_header)
        titleView = findViewById(R.id.exp_title)
        bodyView = findViewById(R.id.exp_body)
        chevron = findViewById(R.id.exp_chevron)
        iconView = findViewById(R.id.exp_icon)

        header.setOnClickListener { toggle() }
    }

    /** Define contenido. [iconRes] = null oculta el ícono (para las preguntas frecuentes). */
    fun bind(@StringRes title: Int, @StringRes body: Int, @DrawableRes iconRes: Int? = null) {
        titleView.setText(title)
        bodyView.setText(body)
        if (iconRes == null) {
            iconView.visibility = GONE
        } else {
            iconView.setImageResource(iconRes)
            iconView.visibility = VISIBLE
        }
    }

    private fun toggle() {
        // Anima el cambio de altura de la tarjeta y de las que están debajo en el contenedor
        (parent as? ViewGroup)?.let { TransitionManager.beginDelayedTransition(it) }
        isExpanded = !isExpanded
        bodyView.visibility = if (isExpanded) VISIBLE else GONE
        chevron.animate().rotation(if (isExpanded) 90f else 0f).setDuration(200).start()
    }
}
