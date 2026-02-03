package com.example.nanomaps.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject

enum class GenerationStyle {
    REALISTIC,
    CINEMATIC,
    RAINY,
    VINTAGE,
    ANIME,
    CUSTOM
}

data class CustomStyle(
    val id: String,
    val name: String,
    val prompt: String
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("prompt", prompt)
    }

    companion object {
        fun fromJson(json: JSONObject): CustomStyle = CustomStyle(
            id = json.getString("id"),
            name = json.getString("name"),
            prompt = json.getString("prompt")
        )
    }
}

enum class AspectRatio(val value: String, val displayName: String) {
    RATIO_16_9("16:9", "16:9 - Widescreen"),
    RATIO_4_3("4:3", "4:3 - Standard"),
    RATIO_3_4("3:4", "3:4 - Portrait"),
    RATIO_1_1("1:1", "1:1 - Square"),
    RATIO_9_16("9:16", "9:16 - Vertical"),
    RATIO_21_9("21:9", "21:9 - Ultrawide")
}

enum class ImageSize(val value: String, val displayName: String) {
    SIZE_1K("1K", "1K - Standard"),
    SIZE_2K("2K", "2K - High Quality"),
    SIZE_4K("4K", "4K - Ultra High")
}

class PreferencesRepository(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "nanomaps_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveApiKey(apiKey: String) {
        sharedPreferences.edit().putString(KEY_API_KEY, apiKey).apply()
    }

    fun getApiKey(): String? {
        return sharedPreferences.getString(KEY_API_KEY, null)
    }

    fun hasApiKey(): Boolean {
        return !getApiKey().isNullOrBlank()
    }

    fun saveStyle(style: GenerationStyle) {
        sharedPreferences.edit().putString(KEY_STYLE, style.name).apply()
    }

    fun getStyle(): GenerationStyle {
        val styleName = sharedPreferences.getString(KEY_STYLE, GenerationStyle.REALISTIC.name)
        return try {
            GenerationStyle.valueOf(styleName ?: GenerationStyle.REALISTIC.name)
        } catch (e: Exception) {
            GenerationStyle.REALISTIC
        }
    }

    fun saveSelectedCustomStyleId(styleId: String?) {
        sharedPreferences.edit().putString(KEY_SELECTED_CUSTOM_STYLE_ID, styleId).apply()
    }

    fun getSelectedCustomStyleId(): String? {
        return sharedPreferences.getString(KEY_SELECTED_CUSTOM_STYLE_ID, null)
    }

    fun saveCustomStyle(style: CustomStyle) {
        val styles = getCustomStyles().toMutableList()
        val existingIndex = styles.indexOfFirst { it.id == style.id }
        if (existingIndex >= 0) {
            styles[existingIndex] = style
        } else {
            styles.add(style)
        }
        saveCustomStyles(styles)
    }

    fun deleteCustomStyle(styleId: String) {
        val styles = getCustomStyles().filter { it.id != styleId }
        saveCustomStyles(styles)
        if (getSelectedCustomStyleId() == styleId) {
            saveSelectedCustomStyleId(null)
            saveStyle(GenerationStyle.REALISTIC)
        }
    }

    fun getCustomStyles(): List<CustomStyle> {
        val json = sharedPreferences.getString(KEY_CUSTOM_STYLES, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(json)
            (0 until jsonArray.length()).map { CustomStyle.fromJson(jsonArray.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getCustomStyleById(id: String): CustomStyle? {
        return getCustomStyles().find { it.id == id }
    }

    private fun saveCustomStyles(styles: List<CustomStyle>) {
        val jsonArray = JSONArray()
        styles.forEach { jsonArray.put(it.toJson()) }
        sharedPreferences.edit().putString(KEY_CUSTOM_STYLES, jsonArray.toString()).apply()
    }

    fun saveAspectRatio(ratio: AspectRatio) {
        sharedPreferences.edit().putString(KEY_ASPECT_RATIO, ratio.name).apply()
    }

    fun getAspectRatio(): AspectRatio {
        val ratioName = sharedPreferences.getString(KEY_ASPECT_RATIO, AspectRatio.RATIO_16_9.name)
        return try {
            AspectRatio.valueOf(ratioName ?: AspectRatio.RATIO_16_9.name)
        } catch (e: Exception) {
            AspectRatio.RATIO_16_9
        }
    }

    fun saveImageSize(size: ImageSize) {
        sharedPreferences.edit().putString(KEY_IMAGE_SIZE, size.name).apply()
    }

    fun getImageSize(): ImageSize {
        val sizeName = sharedPreferences.getString(KEY_IMAGE_SIZE, ImageSize.SIZE_2K.name)
        return try {
            ImageSize.valueOf(sizeName ?: ImageSize.SIZE_2K.name)
        } catch (e: Exception) {
            ImageSize.SIZE_2K
        }
    }

    companion object {
        private const val KEY_API_KEY = "gemini_api_key"
        private const val KEY_STYLE = "generation_style"
        private const val KEY_ASPECT_RATIO = "aspect_ratio"
        private const val KEY_IMAGE_SIZE = "image_size"
        private const val KEY_CUSTOM_STYLES = "custom_styles"
        private const val KEY_SELECTED_CUSTOM_STYLE_ID = "selected_custom_style_id"

        @Volatile
        private var instance: PreferencesRepository? = null

        fun getInstance(context: Context): PreferencesRepository {
            return instance ?: synchronized(this) {
                instance ?: PreferencesRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
