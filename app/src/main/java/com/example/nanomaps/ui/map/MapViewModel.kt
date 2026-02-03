package com.example.nanomaps.ui.map

import android.app.Application
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.nanomaps.data.GeminiApiService
import com.example.nanomaps.data.PreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import java.io.File
import java.io.FileOutputStream

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesRepository = PreferencesRepository.getInstance(application)
    private val geminiService = GeminiApiService.getInstance()

    private val _location = MutableLiveData<GeoPoint?>()
    val location: LiveData<GeoPoint?> = _location

    private val _direction = MutableLiveData<Int?>()
    val direction: LiveData<Int?> = _direction

    private val _generationState = MutableLiveData<GenerationState>(GenerationState.Idle)
    val generationState: LiveData<GenerationState> = _generationState

    private val _canGenerate = MutableLiveData(false)
    val canGenerate: LiveData<Boolean> = _canGenerate

    private val _requirementHint = MutableLiveData<RequirementHint>(RequirementHint.LOCATION)
    val requirementHint: LiveData<RequirementHint> = _requirementHint

    private val _saveResult = MutableLiveData<SaveResult?>()
    val saveResult: LiveData<SaveResult?> = _saveResult

    private var currentBitmap: Bitmap? = null
    private var generationJob: Job? = null

    var mapCenter: GeoPoint = GeoPoint(37.7749, -122.4194)
        private set
    var mapZoom: Double = 15.0
        private set
    var isSatelliteLayer: Boolean = false

    fun setMapState(center: GeoPoint, zoom: Double) {
        mapCenter = center
        mapZoom = zoom
    }

    fun setLocation(geoPoint: GeoPoint) {
        _location.value = geoPoint
        updateCanGenerate()
    }

    fun setDirection(degrees: Int) {
        _direction.value = degrees
        updateCanGenerate()
    }

    fun clearSelection() {
        _location.value = null
        _direction.value = null
        _generationState.value = GenerationState.Idle
        currentBitmap = null
        updateCanGenerate()
    }

    private fun updateCanGenerate() {
        val hasLocation = _location.value != null
        val hasDirection = _direction.value != null
        val hasApiKey = preferencesRepository.hasApiKey()

        _canGenerate.value = hasLocation && hasDirection && hasApiKey

        _requirementHint.value = when {
            !hasLocation -> RequirementHint.LOCATION
            !hasDirection -> RequirementHint.DIRECTION
            !hasApiKey -> RequirementHint.API_KEY
            else -> RequirementHint.READY
        }
    }

    fun generate(mapBitmap: Bitmap, customPrompt: String?, isSatellite: Boolean) {
        val loc = _location.value ?: return
        val dir = _direction.value ?: return
        val apiKey = preferencesRepository.getApiKey()

        if (apiKey.isNullOrBlank()) {
            _generationState.value = GenerationState.Error("Please set your API key in Settings")
            return
        }

        val style = preferencesRepository.getStyle()
        val aspectRatio = preferencesRepository.getAspectRatio()
        val imageSize = preferencesRepository.getImageSize()

        _generationState.value = GenerationState.Loading

        generationJob = viewModelScope.launch {
            val result = geminiService.generateStreetView(
                apiKey = apiKey,
                latitude = loc.latitude,
                longitude = loc.longitude,
                direction = dir,
                mapBitmap = mapBitmap,
                customPrompt = customPrompt,
                isSatelliteView = isSatellite,
                style = style,
                aspectRatio = aspectRatio,
                imageSize = imageSize
            )

            result.fold(
                onSuccess = { bitmap ->
                    currentBitmap = bitmap
                    _generationState.value = GenerationState.Success(bitmap)
                },
                onFailure = { error ->
                    _generationState.value = GenerationState.Error(
                        error.message ?: "Generation failed"
                    )
                }
            )
        }
    }

    fun saveCurrentImage() {
        val bitmap = currentBitmap ?: return

        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                try {
                    saveImageToGallery(bitmap)
                    true
                } catch (e: Exception) {
                    false
                }
            }
            _saveResult.value = if (success) SaveResult.Success else SaveResult.Failed
        }
    }

    private fun saveImageToGallery(bitmap: Bitmap): Boolean {
        val context = getApplication<Application>()
        val filename = "NanoMaps_${System.currentTimeMillis()}.png"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NanoMaps")
            }

            val uri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: return false

            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
        } else {
            val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val nanoMapsDir = File(picturesDir, "NanoMaps")
            if (!nanoMapsDir.exists()) {
                nanoMapsDir.mkdirs()
            }
            val file = File(nanoMapsDir, filename)
            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
        }
        return true
    }

    fun clearSaveResult() {
        _saveResult.value = null
    }

    fun getDirectionName(degrees: Int): String {
        val directions = listOf(
            "N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE",
            "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW"
        )
        val index = ((degrees + 11.25) / 22.5).toInt() % 16
        return directions[index]
    }

    fun checkApiKeyExists(): Boolean {
        return preferencesRepository.hasApiKey()
    }

    fun refreshCanGenerate() {
        updateCanGenerate()
    }

    fun cancelGeneration() {
        generationJob?.cancel()
        generationJob = null
        _generationState.value = GenerationState.Idle
    }

    fun getCurrentBitmap(): Bitmap? = currentBitmap

    sealed class GenerationState {
        data object Idle : GenerationState()
        data object Loading : GenerationState()
        data class Success(val bitmap: Bitmap) : GenerationState()
        data class Error(val message: String) : GenerationState()
    }

    sealed class SaveResult {
        data object Success : SaveResult()
        data object Failed : SaveResult()
    }

    enum class RequirementHint {
        LOCATION,
        DIRECTION,
        API_KEY,
        READY
    }
}
