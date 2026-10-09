package com.pebete.astrotracker.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.pebete.astrotracker.databinding.FragmentTextBinding

class TextFragment : Fragment() {

    private var _binding: FragmentTextBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTextBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val body = arguments?.getString(ARG_BODY) ?: ""
        binding.tvTextBody.text = body
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_BODY = "body"

        fun newInstance(body: String): TextFragment {
            val fragment = TextFragment()
            val args = Bundle()
            args.putString(ARG_BODY, body)
            fragment.arguments = args
            return fragment
        }
    }
}
