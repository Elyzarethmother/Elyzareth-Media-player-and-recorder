package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

class ArtworkManager(private val context: Context) {
    private val prefs = context.getSharedPreferences("elyzareth_artwork_prefs", Context.MODE_PRIVATE)

    fun getArtworkUriForSong(songId: String, baseTitle: String): String? {
        // Try exact song id first, then fallback to base song title
        return prefs.getString("artwork_song_$songId", null)
            ?: prefs.getString("artwork_base_$baseTitle", null)
    }

    fun saveArtworkForSong(songId: String, baseTitle: String, sourceUri: Uri): String? {
        return try {
            val artworkDir = File(context.filesDir, "custom_artwork").apply { mkdirs() }
            val cleanKey = songId.replace("[^a-zA-Z0-9_]".toRegex(), "_")
            val targetFile = File(artworkDir, "art_${cleanKey}.jpg")

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                val bitmap = BitmapFactory.decodeStream(input) ?: return null
                FileOutputStream(targetFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                }
            }

            val savedUri = Uri.fromFile(targetFile).toString()
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

    fun loadBitmap(uriString: String?): Bitmap? {
        if (uriString == null) return null
        return try {
            if (uriString.startsWith("file://")) {
                val path = Uri.parse(uriString).path ?: return null
                BitmapFactory.decodeFile(path)
            } else {
                val uri = Uri.parse(uriString)
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                }
            }
        } catch (e: Exception) {
            null
        }
    }
}
