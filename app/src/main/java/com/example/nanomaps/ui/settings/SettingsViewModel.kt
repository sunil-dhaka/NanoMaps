package com.example.nanomaps.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.nanomaps.data.AspectRatio
import com.example.nanomaps.data.GenerationStyle
import com.example.nanomaps.data.ImageSize
import com.example.nanomaps.data.PreferencesRepository

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesRepository = PreferencesRepository.getInstance(application)

    private val _saveStatus = MutableLiveData<SaveStatus>()
    val saveStatus: LiveData<SaveStatus> = _saveStatus

    fun getCurrentApiKey(): String {
        return preferencesRepository.getApiKey() ?: ""
    }

    fun getCurrentStyle(): GenerationStyle {
        return preferencesRepository.getStyle()
    }

    fun getCurrentAspectRatio(): AspectRatio {
        return preferencesRepository.getAspectRatio()
    }

    fun getCurrentImageSize(): ImageSize {
        return preferencesRepository.getImageSize()
    }

    fun saveSettings(apiKey: String, style: GenerationStyle, aspectRatio: AspectRatio, imageSize: ImageSize) {
        if (apiKey.isBlank()) {
            _saveStatus.value = SaveStatus.Error("Please enter an API key")
            return
        }

        preferencesRepository.saveApiKey(apiKey.trim())
        preferencesRepository.saveStyle(style)
        preferencesRepository.saveAspectRatio(aspectRatio)
        preferencesRepository.saveImageSize(imageSize)
        _saveStatus.value = SaveStatus.Success
    }

    sealed class SaveStatus {
        data object Idle : SaveStatus()
        data object Success : SaveStatus()
        data class Error(val message: String) : SaveStatus()
    }
}
