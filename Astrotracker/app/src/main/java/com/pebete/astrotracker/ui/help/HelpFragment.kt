package com.pebete.astrotracker.ui.help

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.pebete.astrotracker.R
import com.pebete.astrotracker.databinding.FragmentHelpBinding
import com.pebete.astrotracker.databinding.ItemHelpStepBinding
import com.pebete.astrotracker.ui.MainActivity
import com.pebete.astrotracker.ui.Screen

/**
 * Pantalla "Ayuda y Soporte": guía de conexión paso a paso + problemas comunes + preguntas frecuentes.
 * Todo el contenido son recursos de strings (strings_help.xml); acá solo se arman las vistas.
 */
class HelpFragment : Fragment() {

    private var _binding: FragmentHelpBinding? = null
    private val binding get() = _binding!!

    // Título y detalle de cada paso de la guía (el último se pinta de verde = "listo")
    private val steps: List<Pair<Int, Int>> = listOf(
        R.string.help_step1_title to R.string.help_step1_body,
        R.string.help_step2_title to R.string.help_step2_body,
        R.string.help_step3_title to R.string.help_step3_body,
        R.string.help_step4_title to R.string.help_step4_body,
        R.string.help_step5_title to R.string.help_step5_body
    )

    private val problems: List<Pair<Int, Int>> = listOf(
        R.string.help_p1_title to R.string.help_p1_body,
        R.string.help_p2_title to R.string.help_p2_body,
        R.string.help_p3_title to R.string.help_p3_body,
        R.string.help_p4_title to R.string.help_p4_body,
        R.string.help_p5_title to R.string.help_p5_body
    )

    private val faqs: List<Pair<Int, Int>> = listOf(
        R.string.help_f1_title to R.string.help_f1_body,
        R.string.help_f2_title to R.string.help_f2_body,
        R.string.help_f3_title to R.string.help_f3_body,
        R.string.help_f4_title to R.string.help_f4_body
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHelpBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        buildSteps()
        fillAccordions(binding.problemsContainer, problems, R.drawable.ic_warning)
        fillAccordions(binding.faqContainer, faqs, null)
    }

    private fun buildSteps() {
        val inflater = LayoutInflater.from(requireContext())
        steps.forEachIndexed { index, (title, body) ->
            val row = ItemHelpStepBinding.inflate(inflater, binding.stepsContainer, false)
            row.tvStepNumber.text = (index + 1).toString()
            row.tvStepTitle.setText(title)
            row.tvStepBody.setText(body)
            if (index == steps.lastIndex) {
                row.tvStepNumber.backgroundTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.state_ok))
                row.tvStepNumber.setTextColor(ContextCompat.getColor(requireContext(), R.color.slate_950))
            }
            binding.stepsContainer.addView(row.root)
        }
    }

    private fun fillAccordions(
        container: LinearLayout,
        items: List<Pair<Int, Int>>,
        @DrawableRes icon: Int?
    ) {
        val gap = (8 * resources.displayMetrics.density).toInt()
        items.forEach { (title, body) ->
            val card = ExpandableCardView(requireContext())
            card.bind(title, body, icon)
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = gap
            container.addView(card, lp)
        }
    }

    override fun onResume() {
        super.onResume()
        (requireActivity() as MainActivity).onScreenShown(Screen.HELP)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
