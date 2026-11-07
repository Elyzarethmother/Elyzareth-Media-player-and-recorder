package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

class ArtworkManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("elyzareth_artwork_prefs", Context.MODE_PRIVATE)
    private val bitmapCache = ConcurrentHashMap<String, Bitmap>()

    fun getArtworkUriForSong(songId: String, baseTitle: String): String? {
        return prefs.getString("artwork_song_$songId", null)
            ?: prefs.getString("artwork_base_$baseTitle", null)
    }

    fun saveArtworkForSong(songId: String, baseTitle: String, sourceUri: Uri): String? {
        return try {
            val artworkDir = File(context.filesDir, "custom_artwork").apply { mkdirs() }
            val cleanKey = songId.replace("[^a-zA-Z0-9_]".toRegex(), "_")
            val targetFile = File(artworkDir, "art_${cleanKey}.jpg")

            val decoded = decodeSampledBitmap(sourceUri) ?: return null
            FileOutputStream(targetFile).use { out ->
                decoded.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }

            val savedUri = Uri.fromFile(targetFile).toString()
            bitmapCache[savedUri] = decoded
            prefs.edit()
                .putString("artwork_song_$songId", savedUri)
                .putString("artwork_base_$baseTitle", savedUri)
                .apply()
            savedUri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun clearArtworkForSong(songId: String, baseTitle: String) {
        val prev = getArtworkUriForSong(songId, baseTitle)
        if (prev != null) {
            bitmapCache.remove(prev)
        }
        prefs.edit()
            .remove("artwork_song_$songId")
            .remove("artwork_base_$baseTitle")
            .apply()
    }

    fun getWallpaperUri(): String? {
        return prefs.getString("custom_stage_wallpaper_uri", null)
    }

    fun saveWallpaper(sourceUri: Uri): String? {
        return try {
            val artworkDir = File(context.filesDir, "custom_artwork").apply { mkdirs() }
            val targetFile = File(artworkDir, "stage_wallpaper_${System.currentTimeMillis()}.jpg")

            val decoded = decodeSampledBitmap(sourceUri) ?: return null
            FileOutputStream(targetFile).use { out ->
                decoded.compress(Bitmap.CompressFormat.JPEG, 92, out)
            }

            val savedUri = Uri.fromFile(targetFile).toString()
            bitmapCache[savedUri] = decoded
            prefs.edit()
                .putString("custom_stage_wallpaper_uri", savedUri)
                .apply()
            savedUri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun clearWallpaper() {
        val prev = getWallpaperUri()
        if (prev != null) {
            bitmapCache.remove(prev)
        }
        prefs.edit()
            .remove("custom_stage_wallpaper_uri")
            .apply()
    }

    fun loadBitmap(uriString: String?): Bitmap? {
        if (uriString.isNullOrBlank()) return null
        val cached = bitmapCache[uriString]
        if (cached != null && !cached.isRecycled) {
            return cached
        }
        return try {
            val bmp = if (uriString.startsWith("file://")) {
                val path = Uri.parse(uriString).path ?: return null
                BitmapFactory.decodeFile(path)
            } else {
                val uri = Uri.parse(uriString)
                decodeSampledBitmap(uri)
            }
            if (bmp != null) {
                bitmapCache[uriString] = bmp
            }
            bmp
        } catch (e: Exception) {
            null
        }
    }

    private fun decodeSampledBitmap(uri: Uri): Bitmap? {
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it)
        }
    }
}
