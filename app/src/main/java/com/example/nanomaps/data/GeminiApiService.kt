package com.example.nanomaps.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class GeminiApiService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val model = "gemini-3-pro-image-preview"

    suspend fun generateStreetView(
        apiKey: String,
        latitude: Double,
        longitude: Double,
        direction: Int,
        mapBitmap: Bitmap,
        customPrompt: String?,
        isSatelliteView: Boolean,
        style: GenerationStyle,
        customStylePrompt: String?,
        aspectRatio: AspectRatio,
        imageSize: ImageSize
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            val result = generateWithModel(
                apiKey, latitude, longitude, direction,
                mapBitmap, customPrompt, isSatelliteView, style, customStylePrompt, aspectRatio, imageSize
            )
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun generateFantasyView(
        apiKey: String,
        fantasyMap: FantasyMap,
        location: FantasyLocation,
        direction: Int,
        mapBitmap: Bitmap,
        customPrompt: String?,
        style: GenerationStyle,
        customStylePrompt: String?,
        aspectRatio: AspectRatio,
        imageSize: ImageSize
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        try {
            val result = generateFantasyWithModel(
                apiKey, fantasyMap, location, direction,
                mapBitmap, customPrompt, style, customStylePrompt, aspectRatio, imageSize
            )
            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun generateFantasyWithModel(
        apiKey: String,
        fantasyMap: FantasyMap,
        location: FantasyLocation,
        direction: Int,
        mapBitmap: Bitmap,
        customPrompt: String?,
        style: GenerationStyle,
        customStylePrompt: String?,
        aspectRatio: AspectRatio,
        imageSize: ImageSize
    ): Bitmap {
        val prompt = buildFantasyPrompt(fantasyMap, location, direction, customPrompt, style, customStylePrompt)
        val mapBase64 = bitmapToBase64(mapBitmap)

        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                        put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", "image/png")
                                put("data", mapBase64)
                            })
                        })
                    })
                })
            })
            put("generation_config", JSONObject().apply {
                put("response_modalities", JSONArray().apply {
                    put("TEXT")
                    put("IMAGE")
                })
                put("image_config", JSONObject().apply {
                    put("aspect_ratio", aspectRatio.value)
                    put("image_size", imageSize.value)
                })
            })
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("x-goog-api-key", apiKey)
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            val errorJson = try {
                JSONObject(errorBody)
            } catch (e: Exception) {
                null
            }
            val errorMessage = errorJson?.optJSONObject("error")?.optString("message") ?: errorBody

            when {
                response.code == 429 || errorMessage.contains("quota") ->
                    throw Exception("Quota exceeded. Get a key from aistudio.google.com")
                response.code == 400 && errorMessage.contains("API key") ->
                    throw Exception("Invalid API key")
                else -> throw Exception(errorMessage)
            }
        }

        val responseBody = response.body?.string() ?: throw Exception("Empty response")
        val responseJson = JSONObject(responseBody)

        val candidates = responseJson.optJSONArray("candidates")
            ?: throw Exception("No candidates in response")

        if (candidates.length() == 0) {
            throw Exception("Empty candidates array")
        }

        val content = candidates.getJSONObject(0).optJSONObject("content")
            ?: throw Exception("No content in candidate")

        val parts = content.optJSONArray("parts")
            ?: throw Exception("No parts in content")

        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            val inlineData = part.optJSONObject("inlineData")
            if (inlineData != null) {
                val imageData = inlineData.optString("data")
                if (imageData.isNotEmpty()) {
                    return base64ToBitmap(imageData)
                }
            }
        }

        throw Exception("No image was generated")
    }

    private fun buildFantasyPrompt(
        fantasyMap: FantasyMap,
        location: FantasyLocation,
        direction: Int,
        customPrompt: String?,
        style: GenerationStyle,
        customStylePrompt: String?
    ): String {
        val directionName = getDirectionName(direction)
        val xPercent = String.format("%.1f", location.xPercent * 100)
        val yPercent = String.format("%.1f", location.yPercent * 100)

        val worldContext = if (fantasyMap.worldContext.isNotBlank()) {
            """
FANTASY WORLD CONTEXT:
${fantasyMap.worldContext}
            """.trimIndent()
        } else {
            ""
        }

        val stylePrompt = if (style == GenerationStyle.CUSTOM && !customStylePrompt.isNullOrBlank()) {
            """
STYLE: Custom User Style
$customStylePrompt
            """.trimIndent()
        } else when (style) {
            GenerationStyle.REALISTIC -> """
STYLE: Photorealistic Fantasy
Create a crystal-clear, ultra-realistic photograph as if you were actually standing in this fantasy world.
- Perfect exposure and white balance
- Sharp details on architecture, textures, and natural elements
- Natural lighting conditions appropriate to the fantasy setting
- Accurate materials and surfaces (stone, wood, metal, fabric)
- Realistic depth of field
The image should feel like a photograph taken in a real fantasy world.
            """.trimIndent()

            GenerationStyle.CINEMATIC -> """
STYLE: Epic Fantasy Cinematic
Create a breathtaking cinematic shot worthy of a fantasy film.
- Dramatic golden hour or magical lighting
- Long, atmospheric shadows
- Rich, saturated colors with fantasy color grading
- Atmospheric haze, mist, or magical particles
- Epic sense of scale and grandeur
Make it look like a scene from Lord of the Rings, Game of Thrones, or a high-budget fantasy production.
            """.trimIndent()

            GenerationStyle.RAINY -> """
STYLE: Moody Fantasy Rain
Create an atmospheric rainy scene in this fantasy world.
- Wet, glistening cobblestones and surfaces reflecting torchlight
- Puddles creating mirror-like reflections
- Soft, diffused lighting from overcast skies
- Light rain or mist visible in the air
- Warm light from tavern windows and lanterns bleeding into the wet surfaces
- That cozy medieval fantasy atmosphere of shelter from a storm
            """.trimIndent()

            GenerationStyle.VINTAGE -> """
STYLE: Ancient Chronicle
Transform this into an aged illustration from an ancient tome or chronicle.
- Faded, parchment-like color palette with warm sepia tones
- Hand-drawn quality with visible brushwork
- Slight texture as if on old paper or canvas
- The aesthetic of a discovered artifact or historical record
- Medieval illuminated manuscript inspiration
            """.trimIndent()

            GenerationStyle.ANIME -> """
STYLE: Fantasy Anime World
Reimagine this location in beautiful Japanese anime style.
- Vibrant, saturated colors that pop with life
- Dreamy skies with dramatic clouds
- Clean linework with soft cel-shading
- Magical, whimsical atmosphere
- Lush vegetation and architectural details in anime style
- Small magical details (floating particles, magical lights, ethereal glow)
Channel the spirit of Studio Ghibli fantasy films.
            """.trimIndent()

            GenerationStyle.CUSTOM -> """
STYLE: Photorealistic Fantasy
Create a crystal-clear, ultra-realistic photograph in this fantasy setting.
            """.trimIndent()
        }

        val basePrompt = """
FANTASY MAP GENERATION

MAP NAME: ${fantasyMap.name}
Position: ${xPercent}% from left, ${yPercent}% from top
VIEWING DIRECTION: Looking $directionName ($direction degrees from North)

$worldContext

Using the fantasy map provided, analyze:
- Terrain features at the marked position
- Settlements, structures, landmarks nearby
- Geographic features (mountains, forests, rivers, coastlines)
- Overall aesthetic and era of the map
- The type of fantasy world depicted (medieval, high fantasy, dark fantasy, etc.)

$stylePrompt

CAMERA PERSPECTIVE:
- First-person ground-level view at eye height (1.7 meters)
- Natural field of view as seen by human eyes
- Ground-level perspective showing the landscape/street ahead
        """.trimIndent()

        return if (!customPrompt.isNullOrBlank()) {
            """
$basePrompt

IMPORTANT - USER'S SPECIFIC REQUEST (prioritize this):
$customPrompt

Generate the image following the user's specific instructions above while maintaining the chosen style.
            """.trimIndent()
        } else {
            "$basePrompt\n\nGenerate the image now."
        }
    }

    private fun generateWithModel(
        apiKey: String,
        latitude: Double,
        longitude: Double,
        direction: Int,
        mapBitmap: Bitmap,
        customPrompt: String?,
        isSatelliteView: Boolean,
        style: GenerationStyle,
        customStylePrompt: String?,
        aspectRatio: AspectRatio,
        imageSize: ImageSize
    ): Bitmap {
        val prompt = buildPrompt(latitude, longitude, direction, customPrompt, isSatelliteView, style, customStylePrompt)
        val mapBase64 = bitmapToBase64(mapBitmap)

        val requestBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", prompt)
                        })
                        put(JSONObject().apply {
                            put("inline_data", JSONObject().apply {
                                put("mime_type", "image/png")
                                put("data", mapBase64)
                            })
                        })
                    })
                })
            })
            put("generation_config", JSONObject().apply {
                put("response_modalities", JSONArray().apply {
                    put("TEXT")
                    put("IMAGE")
                })
                put("image_config", JSONObject().apply {
                    put("aspect_ratio", aspectRatio.value)
                    put("image_size", imageSize.value)
                })
            })
        }

        val url = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("x-goog-api-key", apiKey)
            .post(requestBody.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            val errorBody = response.body?.string() ?: "Unknown error"
            val errorJson = try {
                JSONObject(errorBody)
            } catch (e: Exception) {
                null
            }
            val errorMessage = errorJson?.optJSONObject("error")?.optString("message") ?: errorBody

            when {
                response.code == 429 || errorMessage.contains("quota") ->
                    throw Exception("Quota exceeded. Get a key from aistudio.google.com")
                response.code == 400 && errorMessage.contains("API key") ->
                    throw Exception("Invalid API key")
                else -> throw Exception(errorMessage)
            }
        }

        val responseBody = response.body?.string() ?: throw Exception("Empty response")
        val responseJson = JSONObject(responseBody)

        val candidates = responseJson.optJSONArray("candidates")
            ?: throw Exception("No candidates in response")

        if (candidates.length() == 0) {
            throw Exception("Empty candidates array")
        }

        val content = candidates.getJSONObject(0).optJSONObject("content")
            ?: throw Exception("No content in candidate")

        val parts = content.optJSONArray("parts")
            ?: throw Exception("No parts in content")

        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            val inlineData = part.optJSONObject("inlineData")
            if (inlineData != null) {
                val imageData = inlineData.optString("data")
                if (imageData.isNotEmpty()) {
                    return base64ToBitmap(imageData)
                }
            }
        }

        throw Exception("No image was generated")
    }

    private fun buildPrompt(
        latitude: Double,
        longitude: Double,
        direction: Int,
        customPrompt: String?,
        isSatelliteView: Boolean,
        style: GenerationStyle,
        customStylePrompt: String?
    ): String {
        val directionName = getDirectionName(direction)
        val coords = "${String.format("%.6f", latitude)}, ${String.format("%.6f", longitude)}"

        val mapAnalysis = if (isSatelliteView) {
            """
Using the satellite imagery provided, analyze:
- Building footprints, rooftop colors and materials visible from above
- Vegetation patterns, trees, and landscaping
- Road surfaces, parking areas, and infrastructure
- Terrain features and shadows indicating building heights
            """.trimIndent()
        } else {
            """
Using the street map provided, analyze:
- Street layout and road patterns
- Building density and neighborhood character
- Green spaces and vegetation areas
            """.trimIndent()
        }

        val stylePrompt = if (style == GenerationStyle.CUSTOM && !customStylePrompt.isNullOrBlank()) {
            """
STYLE: Custom User Style
$customStylePrompt
            """.trimIndent()
        } else when (style) {
            GenerationStyle.REALISTIC -> """
STYLE: Photorealistic Street View
Create a crystal-clear, ultra-realistic photograph as if captured by a professional street-level camera.
- Perfect exposure and white balance
- Sharp details on buildings, textures, and surfaces
- Natural daylight conditions (clear sky, soft shadows)
- Accurate architectural details and materials
- Realistic depth of field
The image should be indistinguishable from a real Google Street View photograph.
            """.trimIndent()

            GenerationStyle.CINEMATIC -> """
STYLE: Cinematic Golden Hour
Create a breathtaking cinematic shot during the magical golden hour.
- Warm amber and orange sunlight washing over the scene
- Long, dramatic shadows stretching across the street
- Subtle lens flares where sunlight peeks through
- Rich, saturated colors with a slight orange/teal color grade
- Atmospheric haze adding depth and mystery
- The kind of frame that belongs in an Oscar-winning film
Make it look like a scene from a Denis Villeneuve or Roger Deakins masterpiece.
            """.trimIndent()

            GenerationStyle.RAINY -> """
STYLE: Moody Rainy Day
Create an atmospheric scene on a rainy day with that cozy, contemplative mood.
- Wet, glistening streets reflecting lights and colors
- Puddles creating mirror-like reflections of buildings
- Soft, diffused lighting from overcast skies
- Slight mist or light rain visible in the air
- Neon signs and lights bleeding beautifully into the wet surfaces
- That special ambiance of watching rain from inside a warm cafe
Channel the vibes of a Wong Kar-wai film or Blade Runner's quieter moments.
            """.trimIndent()

            GenerationStyle.VINTAGE -> """
STYLE: Retro 1970s Throwback
Transform this location into a vintage photograph from the 1970s.
- Faded, slightly desaturated colors with warm yellow/brown tint
- Visible film grain adding nostalgic texture
- Soft vignette darkening the corners
- Slightly soft focus typical of old lenses
- Colors that feel like they've aged beautifully over decades
- The aesthetic of a treasured Polaroid or Kodachrome slide
Make it feel like a photograph your parents might have taken on a road trip.
            """.trimIndent()

            GenerationStyle.ANIME -> """
STYLE: Studio Ghibli Anime World
Reimagine this location in the beautiful style of Japanese anime, specifically Studio Ghibli.
- Vibrant, saturated colors that pop with life
- Dreamy skies with fluffy, painterly cumulus clouds
- Clean linework with soft cel-shading
- That magical, whimsical atmosphere Ghibli is famous for
- Lush vegetation rendered in rich greens
- Warm, inviting lighting that makes everything feel alive
- Small details that add charm (birds, floating particles, gentle wind effects)
Channel the spirit of Spirited Away, Howl's Moving Castle, or My Neighbor Totoro.
            """.trimIndent()

            GenerationStyle.CUSTOM -> """
STYLE: Photorealistic Street View
Create a crystal-clear, ultra-realistic photograph.
            """.trimIndent()
        }

        val basePrompt = """
LOCATION: Standing at coordinates ($coords), looking $directionName ($direction degrees from North)

$mapAnalysis

$stylePrompt

CAMERA PERSPECTIVE:
- First-person street-level view at eye height (1.7 meters)
- Natural field of view as seen by human eyes
- Ground-level perspective showing the street ahead
        """.trimIndent()

        return if (!customPrompt.isNullOrBlank()) {
            """
$basePrompt

IMPORTANT - USER'S SPECIFIC REQUEST (prioritize this):
$customPrompt

Generate the image following the user's specific instructions above while maintaining the chosen style.
            """.trimIndent()
        } else {
            "$basePrompt\n\nGenerate the image now."
        }
    }

    private fun getDirectionName(degrees: Int): String {
        val directions = listOf(
            "North", "North-Northeast", "Northeast", "East-Northeast",
            "East", "East-Southeast", "Southeast", "South-Southeast",
            "South", "South-Southwest", "Southwest", "West-Southwest",
            "West", "West-Northwest", "Northwest", "North-Northwest"
        )
        val index = ((degrees + 11.25) / 22.5).toInt() % 16
        return directions[index]
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }

    private fun base64ToBitmap(base64String: String): Bitmap {
        val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            ?: throw Exception("Failed to decode image")
    }

    companion object {
        @Volatile
        private var instance: GeminiApiService? = null

        fun getInstance(): GeminiApiService {
            return instance ?: synchronized(this) {
                instance ?: GeminiApiService().also { instance = it }
            }
        }
    }
}
