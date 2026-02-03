package com.example.nanomaps.ui.settings

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.nanomaps.R
import com.example.nanomaps.data.AspectRatio
import com.example.nanomaps.data.CustomStyle
import com.example.nanomaps.data.FantasyMap
import com.example.nanomaps.data.GenerationStyle
import com.example.nanomaps.data.ImageSize
import com.example.nanomaps.databinding.DialogAddFantasyMapBinding
import com.example.nanomaps.databinding.DialogCustomStyleBinding
import com.example.nanomaps.databinding.FragmentSettingsBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.radiobutton.MaterialRadioButton
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: SettingsViewModel
    private var selectedCustomStyleId: String? = null

    private var pendingFantasyMapDialogBinding: DialogAddFantasyMapBinding? = null
    private var selectedFantasyMapImageUri: Uri? = null

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            selectedFantasyMapImageUri = selectedUri
            pendingFantasyMapDialogBinding?.let { dialogBinding ->
                dialogBinding.imagePreview.setImageURI(selectedUri)
                dialogBinding.imagePreview.visibility = View.VISIBLE
                dialogBinding.imagePlaceholder.visibility = View.GONE
                dialogBinding.selectImageButton.text = getString(R.string.change_image)
            }
        }
    }

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

        val currentStyle = viewModel.getCurrentStyle()
        selectedCustomStyleId = viewModel.getSelectedCustomStyleId()

        when (currentStyle) {
            GenerationStyle.REALISTIC -> binding.chipRealistic.isChecked = true
            GenerationStyle.CINEMATIC -> binding.chipCinematic.isChecked = true
            GenerationStyle.RAINY -> binding.chipRainy.isChecked = true
            GenerationStyle.VINTAGE -> binding.chipVintage.isChecked = true
            GenerationStyle.ANIME -> binding.chipAnime.isChecked = true
            GenerationStyle.CUSTOM -> {
                binding.styleChipGroup.clearCheck()
            }
        }

        if (currentStyle == GenerationStyle.CUSTOM) {
            binding.styleDescription.text = getString(R.string.style_description)
        } else {
            updateStyleDescription(currentStyle)
        }

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
            GenerationStyle.CUSTOM -> R.string.custom_styles_description
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
                selectedCustomStyleId = null
                updateCustomStylesUI()
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

        binding.addCustomStyleButton.setOnClickListener {
            showCustomStyleDialog(null)
        }

        binding.addFantasyMapButton.setOnClickListener {
            showFantasyMapDialog(null)
        }

        binding.saveButton.setOnClickListener {
            saveSettings()
        }
    }

    private fun saveSettings() {
        val apiKey = binding.apiKeyInput.text?.toString() ?: ""

        val style = if (selectedCustomStyleId != null) {
            GenerationStyle.CUSTOM
        } else {
            when (binding.styleChipGroup.checkedChipId) {
                R.id.chip_realistic -> GenerationStyle.REALISTIC
                R.id.chip_cinematic -> GenerationStyle.CINEMATIC
                R.id.chip_rainy -> GenerationStyle.RAINY
                R.id.chip_vintage -> GenerationStyle.VINTAGE
                R.id.chip_anime -> GenerationStyle.ANIME
                else -> GenerationStyle.REALISTIC
            }
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

        viewModel.saveSettings(apiKey, style, selectedCustomStyleId, aspectRatio, imageSize)
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

        viewModel.customStyles.observe(viewLifecycleOwner) { styles ->
            updateCustomStylesUI(styles)
        }

        viewModel.fantasyMaps.observe(viewLifecycleOwner) { maps ->
            updateFantasyMapsUI(maps)
        }
    }

    private fun updateCustomStylesUI(styles: List<CustomStyle> = viewModel.customStyles.value ?: emptyList()) {
        binding.customStylesContainer.removeAllViews()

        if (styles.isEmpty()) {
            binding.noCustomStylesText.visibility = View.VISIBLE
        } else {
            binding.noCustomStylesText.visibility = View.GONE

            styles.forEach { style ->
                val itemView = layoutInflater.inflate(R.layout.item_custom_style, binding.customStylesContainer, false)

                val nameText = itemView.findViewById<TextView>(R.id.style_name)
                val promptPreview = itemView.findViewById<TextView>(R.id.style_prompt_preview)
                val radioButton = itemView.findViewById<MaterialRadioButton>(R.id.style_radio)
                val editButton = itemView.findViewById<MaterialButton>(R.id.edit_button)
                val deleteButton = itemView.findViewById<MaterialButton>(R.id.delete_button)
                val card = itemView as MaterialCardView

                nameText.text = style.name
                promptPreview.text = style.prompt

                radioButton.isChecked = selectedCustomStyleId == style.id

                card.setOnClickListener {
                    selectCustomStyle(style.id)
                }

                radioButton.setOnClickListener {
                    selectCustomStyle(style.id)
                }

                editButton.setOnClickListener {
                    showCustomStyleDialog(style)
                }

                deleteButton.setOnClickListener {
                    showDeleteConfirmation(style)
                }

                binding.customStylesContainer.addView(itemView)
            }
        }
    }

    private fun selectCustomStyle(styleId: String) {
        selectedCustomStyleId = styleId
        binding.styleChipGroup.clearCheck()
        binding.styleDescription.text = getString(R.string.style_description)
        updateCustomStylesUI()
    }

    private fun showCustomStyleDialog(existingStyle: CustomStyle?) {
        val dialogBinding = DialogCustomStyleBinding.inflate(layoutInflater)

        existingStyle?.let {
            dialogBinding.styleNameInput.setText(it.name)
            dialogBinding.stylePromptInput.setText(it.prompt)
        }

        val title = if (existingStyle == null) R.string.add_custom_style else R.string.edit_custom_style

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save_button) { _, _ ->
                val name = dialogBinding.styleNameInput.text?.toString() ?: ""
                val prompt = dialogBinding.stylePromptInput.text?.toString() ?: ""

                if (name.isBlank()) {
                    showSnackbar(getString(R.string.custom_style_name_required))
                    return@setPositiveButton
                }

                if (prompt.isBlank()) {
                    showSnackbar(getString(R.string.custom_style_prompt_required))
                    return@setPositiveButton
                }

                val success = viewModel.saveCustomStyle(name, prompt, existingStyle?.id)
                if (success) {
                    showSnackbar(getString(R.string.custom_style_saved))
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDeleteConfirmation(style: CustomStyle) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_custom_style)
            .setMessage("Delete \"${style.name}\"?")
            .setPositiveButton(R.string.delete_custom_style) { _, _ ->
                if (selectedCustomStyleId == style.id) {
                    selectedCustomStyleId = null
                    binding.chipRealistic.isChecked = true
                }
                viewModel.deleteCustomStyle(style.id)
                showSnackbar(getString(R.string.custom_style_deleted))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateFantasyMapsUI(maps: List<FantasyMap> = viewModel.fantasyMaps.value ?: emptyList()) {
        binding.fantasyMapsContainer.removeAllViews()

        if (maps.isEmpty()) {
            binding.noFantasyMapsText.visibility = View.VISIBLE
        } else {
            binding.noFantasyMapsText.visibility = View.GONE

            maps.forEach { map ->
                val itemView = layoutInflater.inflate(R.layout.item_fantasy_map, binding.fantasyMapsContainer, false)

                val nameText = itemView.findViewById<TextView>(R.id.map_name)
                val contextPreview = itemView.findViewById<TextView>(R.id.map_context_preview)
                val thumbnail = itemView.findViewById<ImageView>(R.id.map_thumbnail)
                val radioButton = itemView.findViewById<MaterialRadioButton>(R.id.map_radio)
                val editButton = itemView.findViewById<MaterialButton>(R.id.edit_button)
                val deleteButton = itemView.findViewById<MaterialButton>(R.id.delete_button)

                nameText.text = map.name
                contextPreview.text = if (map.worldContext.isNotBlank()) map.worldContext else "No description"

                radioButton.visibility = View.GONE

                lifecycleScope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        viewModel.loadMapThumbnail(map.imagePath)
                    }
                    bitmap?.let {
                        thumbnail.setImageBitmap(it)
                    }
                }

                editButton.setOnClickListener {
                    showFantasyMapDialog(map)
                }

                deleteButton.setOnClickListener {
                    showDeleteFantasyMapConfirmation(map)
                }

                binding.fantasyMapsContainer.addView(itemView)
            }
        }
    }

    private fun showFantasyMapDialog(existingMap: FantasyMap?) {
        val dialogBinding = DialogAddFantasyMapBinding.inflate(layoutInflater)
        pendingFantasyMapDialogBinding = dialogBinding
        selectedFantasyMapImageUri = null

        existingMap?.let { map ->
            dialogBinding.mapNameInput.setText(map.name)
            dialogBinding.worldContextInput.setText(map.worldContext)
            dialogBinding.selectImageButton.text = getString(R.string.change_image)

            lifecycleScope.launch {
                val bitmap = withContext(Dispatchers.IO) {
                    viewModel.loadMapThumbnail(map.imagePath)
                }
                bitmap?.let {
                    dialogBinding.imagePreview.setImageBitmap(it)
                    dialogBinding.imagePreview.visibility = View.VISIBLE
                    dialogBinding.imagePlaceholder.visibility = View.GONE
                }
            }
        }

        dialogBinding.selectImageButton.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        dialogBinding.imagePreviewCard.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }

        val title = if (existingMap == null) R.string.add_fantasy_map else R.string.edit_fantasy_map

        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save_button) { _, _ ->
                val name = dialogBinding.mapNameInput.text?.toString() ?: ""
                val worldContext = dialogBinding.worldContextInput.text?.toString() ?: ""

                if (name.isBlank()) {
                    showSnackbar(getString(R.string.fantasy_map_name_required))
                    return@setPositiveButton
                }

                if (existingMap == null && selectedFantasyMapImageUri == null) {
                    showSnackbar(getString(R.string.image_required))
                    return@setPositiveButton
                }

                val success = viewModel.saveFantasyMap(
                    name = name,
                    worldContext = worldContext,
                    imageUri = selectedFantasyMapImageUri,
                    existingId = existingMap?.id,
                    existingImagePath = existingMap?.imagePath
                )

                if (success) {
                    showSnackbar(getString(R.string.fantasy_map_saved))
                }

                pendingFantasyMapDialogBinding = null
                selectedFantasyMapImageUri = null
            }
            .setNegativeButton(R.string.cancel) { _, _ ->
                pendingFantasyMapDialogBinding = null
                selectedFantasyMapImageUri = null
            }
            .setOnDismissListener {
                pendingFantasyMapDialogBinding = null
            }
            .show()
    }

    private fun showDeleteFantasyMapConfirmation(map: FantasyMap) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.delete_fantasy_map)
            .setMessage("Delete \"${map.name}\"?")
            .setPositiveButton(R.string.delete_fantasy_map) { _, _ ->
                viewModel.deleteFantasyMap(map.id)
                showSnackbar(getString(R.string.fantasy_map_deleted))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showSnackbar(message: String) {
        view?.let { rootView ->
            Snackbar.make(rootView, message, Snackbar.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pendingFantasyMapDialogBinding = null
        _binding = null
    }
}
