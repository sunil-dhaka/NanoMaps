package com.example.nanomaps.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class FantasyMapStorage(private val context: Context) {

    private val fantasyMapsDir: File
        get() = File(context.filesDir, FANTASY_MAPS_DIR).also {
            if (!it.exists()) it.mkdirs()
        }

    fun saveMapImage(uri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream.close()

            if (bitmap == null) return null

            val filename = "${UUID.randomUUID()}.png"
            val file = File(fantasyMapsDir, filename)

            FileOutputStream(file).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
            }
            bitmap.recycle()

            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun loadMapBitmap(path: String): Bitmap? {
        return try {
            val file = File(path)
            if (!file.exists()) return null
            BitmapFactory.decodeFile(path)
        } catch (e: Exception) {
            null
        }
    }

    fun deleteMapImage(path: String): Boolean {
        return try {
            val file = File(path)
            if (file.exists()) file.delete() else true
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val FANTASY_MAPS_DIR = "fantasy_maps"

        @Volatile
        private var instance: FantasyMapStorage? = null

        fun getInstance(context: Context): FantasyMapStorage {
            return instance ?: synchronized(this) {
                instance ?: FantasyMapStorage(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
