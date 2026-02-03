package com.example.nanomaps.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.nanomaps.R
import com.example.nanomaps.data.AspectRatio
import com.example.nanomaps.data.GenerationStyle
import com.example.nanomaps.data.ImageSize
import com.example.nanomaps.databinding.FragmentSettingsBinding
import com.google.android.material.snackbar.Snackbar

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SettingsViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        viewModel = ViewModelProvider(this)[SettingsViewModel::class.java]
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        loadCurrentSettings()
        setupListeners()
        observeViewModel()
    }

    private fun loadCurrentSettings() {
        binding.apiKeyInput.setText(viewModel.getCurrentApiKey())

        when (viewModel.getCurrentStyle()) {
            GenerationStyle.REALISTIC -> binding.chipRealistic.isChecked = true
            GenerationStyle.CINEMATIC -> binding.chipCinematic.isChecked = true
            GenerationStyle.RAINY -> binding.chipRainy.isChecked = true
            GenerationStyle.VINTAGE -> binding.chipVintage.isChecked = true
            GenerationStyle.ANIME -> binding.chipAnime.isChecked = true
        }
        updateStyleDescription(viewModel.getCurrentStyle())

        when (viewModel.getCurrentAspectRatio()) {
            AspectRatio.RATIO_16_9 -> binding.chipRatio169.isChecked = true
            AspectRatio.RATIO_4_3 -> binding.chipRatio43.isChecked = true
            AspectRatio.RATIO_3_4 -> binding.chipRatio34.isChecked = true
            AspectRatio.RATIO_1_1 -> binding.chipRatio11.isChecked = true
            AspectRatio.RATIO_9_16 -> binding.chipRatio916.isChecked = true
            AspectRatio.RATIO_21_9 -> binding.chipRatio219.isChecked = true
        }

        when (viewModel.getCurrentImageSize()) {
            ImageSize.SIZE_1K -> binding.btnSize1k.isChecked = true
            ImageSize.SIZE_2K -> binding.btnSize2k.isChecked = true
            ImageSize.SIZE_4K -> binding.btnSize4k.isChecked = true
        }
    }

    private fun updateStyleDescription(style: GenerationStyle) {
        val descriptionRes = when (style) {
            GenerationStyle.REALISTIC -> R.string.style_realistic_desc
            GenerationStyle.CINEMATIC -> R.string.style_cinematic_desc
            GenerationStyle.RAINY -> R.string.style_rainy_desc
            GenerationStyle.VINTAGE -> R.string.style_vintage_desc
            GenerationStyle.ANIME -> R.string.style_anime_desc
        }
        binding.styleDescription.text = getString(descriptionRes)
    }

    private fun setupListeners() {
        binding.getKeyButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://aistudio.google.com/apikey"))
            startActivity(intent)
        }

        binding.styleChipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            if (checkedIds.isNotEmpty()) {
                val style = when (checkedIds.first()) {
                    R.id.chip_realistic -> GenerationStyle.REALISTIC
                    R.id.chip_cinematic -> GenerationStyle.CINEMATIC
                    R.id.chip_rainy -> GenerationStyle.RAINY
                    R.id.chip_vintage -> GenerationStyle.VINTAGE
                    R.id.chip_anime -> GenerationStyle.ANIME
                    else -> GenerationStyle.REALISTIC
                }
                updateStyleDescription(style)
            }
        }

        binding.saveButton.setOnClickListener {
            val apiKey = binding.apiKeyInput.text?.toString() ?: ""

            val style = when (binding.styleChipGroup.checkedChipId) {
                R.id.chip_realistic -> GenerationStyle.REALISTIC
                R.id.chip_cinematic -> GenerationStyle.CINEMATIC
                R.id.chip_rainy -> GenerationStyle.RAINY
                R.id.chip_vintage -> GenerationStyle.VINTAGE
                R.id.chip_anime -> GenerationStyle.ANIME
                else -> GenerationStyle.REALISTIC
            }

            val aspectRatio = when (binding.aspectRatioChipGroup.checkedChipId) {
                R.id.chip_ratio_16_9 -> AspectRatio.RATIO_16_9
                R.id.chip_ratio_4_3 -> AspectRatio.RATIO_4_3
                R.id.chip_ratio_3_4 -> AspectRatio.RATIO_3_4
                R.id.chip_ratio_1_1 -> AspectRatio.RATIO_1_1
                R.id.chip_ratio_9_16 -> AspectRatio.RATIO_9_16
                R.id.chip_ratio_21_9 -> AspectRatio.RATIO_21_9
                else -> AspectRatio.RATIO_16_9
            }

            val imageSize = when (binding.sizeToggleGroup.checkedButtonId) {
                R.id.btn_size_1k -> ImageSize.SIZE_1K
                R.id.btn_size_2k -> ImageSize.SIZE_2K
                R.id.btn_size_4k -> ImageSize.SIZE_4K
                else -> ImageSize.SIZE_2K
            }

            viewModel.saveSettings(apiKey, style, aspectRatio, imageSize)
        }
    }

    private fun observeViewModel() {
        viewModel.saveStatus.observe(viewLifecycleOwner) { status ->
            when (status) {
                is SettingsViewModel.SaveStatus.Success -> {
                    showSnackbar(getString(R.string.settings_saved))
                }
                is SettingsViewModel.SaveStatus.Error -> {
                    binding.statusText.visibility = View.VISIBLE
                    binding.statusText.text = status.message
                    binding.statusText.setTextColor(
                        ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark)
                    )
                }
                is SettingsViewModel.SaveStatus.Idle -> {
                    binding.statusText.visibility = View.GONE
                }
                null -> {}
            }
        }
    }

    private fun showSnackbar(message: String) {
        view?.let { rootView ->
            Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT)
                .setAnchorView(requireActivity().findViewById(R.id.nav_view))
                .show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
