package com.example.nanomaps.ui.settings

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.nanomaps.data.AspectRatio
import com.example.nanomaps.data.CustomStyle
import com.example.nanomaps.data.FantasyMap
import com.example.nanomaps.data.FantasyMapStorage
import com.example.nanomaps.data.GenerationStyle
import com.example.nanomaps.data.ImageSize
import com.example.nanomaps.data.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesRepository = PreferencesRepository.getInstance(application)
    private val fantasyMapStorage = FantasyMapStorage.getInstance(application)

    private val _saveStatus = MutableLiveData<SaveStatus>()
    val saveStatus: LiveData<SaveStatus> = _saveStatus

    private val _customStyles = MutableLiveData<List<CustomStyle>>()
    val customStyles: LiveData<List<CustomStyle>> = _customStyles

    private val _fantasyMaps = MutableLiveData<List<FantasyMap>>()
    val fantasyMaps: LiveData<List<FantasyMap>> = _fantasyMaps

    init {
        loadCustomStyles()
        loadFantasyMaps()
    }

    fun getCurrentApiKey(): String {
        return preferencesRepository.getApiKey() ?: ""
    }

    fun getCurrentStyle(): GenerationStyle {
        return preferencesRepository.getStyle()
    }

    fun getSelectedCustomStyleId(): String? {
        return preferencesRepository.getSelectedCustomStyleId()
    }

    fun getCurrentAspectRatio(): AspectRatio {
        return preferencesRepository.getAspectRatio()
    }

    fun getCurrentImageSize(): ImageSize {
        return preferencesRepository.getImageSize()
    }

    fun saveSettings(
        apiKey: String,
        style: GenerationStyle,
        customStyleId: String?,
        aspectRatio: AspectRatio,
        imageSize: ImageSize
    ) {
        if (apiKey.isBlank()) {
            _saveStatus.value = SaveStatus.Error("Please enter an API key")
            return
        }

        preferencesRepository.saveApiKey(apiKey.trim())
        preferencesRepository.saveStyle(style)
        preferencesRepository.saveSelectedCustomStyleId(customStyleId)
        preferencesRepository.saveAspectRatio(aspectRatio)
        preferencesRepository.saveImageSize(imageSize)
        _saveStatus.value = SaveStatus.Success
    }

    fun loadCustomStyles() {
        _customStyles.value = preferencesRepository.getCustomStyles()
    }

    fun saveCustomStyle(name: String, prompt: String, existingId: String? = null): Boolean {
        if (name.isBlank() || prompt.isBlank()) return false

        val style = CustomStyle(
            id = existingId ?: UUID.randomUUID().toString(),
            name = name.trim(),
            prompt = prompt.trim()
        )
        preferencesRepository.saveCustomStyle(style)
        loadCustomStyles()
        return true
    }

    fun deleteCustomStyle(styleId: String) {
        preferencesRepository.deleteCustomStyle(styleId)
        loadCustomStyles()
    }

    fun getCustomStyleById(id: String): CustomStyle? {
        return preferencesRepository.getCustomStyleById(id)
    }

    fun loadFantasyMaps() {
        _fantasyMaps.value = preferencesRepository.getFantasyMaps()
    }

    fun saveFantasyMap(
        name: String,
        worldContext: String,
        imageUri: Uri?,
        existingId: String? = null,
        existingImagePath: String? = null
    ): Boolean {
        if (name.isBlank()) return false

        val imagePath = if (imageUri != null) {
            fantasyMapStorage.saveMapImage(imageUri)
        } else {
            existingImagePath
        }

        if (imagePath == null) return false

        if (existingImagePath != null && imageUri != null && existingImagePath != imagePath) {
            fantasyMapStorage.deleteMapImage(existingImagePath)
        }

        val map = FantasyMap(
            id = existingId ?: UUID.randomUUID().toString(),
            name = name.trim(),
            imagePath = imagePath,
            worldContext = worldContext.trim(),
            createdAt = System.currentTimeMillis()
        )
        preferencesRepository.saveFantasyMap(map)
        loadFantasyMaps()
        return true
    }

    fun deleteFantasyMap(mapId: String) {
        val map = preferencesRepository.getFantasyMapById(mapId)
        if (map != null) {
            fantasyMapStorage.deleteMapImage(map.imagePath)
        }
        preferencesRepository.deleteFantasyMap(mapId)
        loadFantasyMaps()
    }

    fun getFantasyMapById(id: String): FantasyMap? {
        return preferencesRepository.getFantasyMapById(id)
    }

    fun loadMapThumbnail(path: String): android.graphics.Bitmap? {
        return fantasyMapStorage.loadMapBitmap(path)
    }

    sealed class SaveStatus {
        data object Idle : SaveStatus()
        data object Success : SaveStatus()
        data class Error(val message: String) : SaveStatus()
    }
}
