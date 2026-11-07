package com.example.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.example.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

object AudioPermissionHelper {
    fun getRequiredAudioStoragePermission(sdkInt: Int = Build.VERSION.SDK_INT): String {
        return if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    }

    fun hasAudioStoragePermission(context: Context, sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
        val perm = getRequiredAudioStoragePermission(sdkInt)
        return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
    }

    fun hasRecordAudioPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}

class MusicLibraryManager(
    private val context: Context,
    private val artworkManager: ArtworkManager
) {
    private val importedPrefs = context.getSharedPreferences(
        "elyzareth_imported_tracks_prefs",
        Context.MODE_PRIVATE
    )

    suspend fun loadFullLibrary(): List<Song> = withContext(Dispatchers.IO) {
        val songsList = mutableListOf<Song>()
        val seenKeys = mutableSetOf<String>()

        // 1. Load persisted manually imported songs so rescanning never loses manual selections
        val persistedImports = loadPersistedImportedSongs()
        for (song in persistedImports) {
            val dedupeKey = song.filePath ?: song.contentUri ?: song.id
            if (seenKeys.add(dedupeKey)) {
                songsList.add(song)
            }
        }

        // 2. Scan MediaStore for local audio files (MP3s) when permission is granted
        if (AudioPermissionHelper.hasAudioStoragePermission(context)) {
            try {
                val scannedSongs = scanMediaStoreAudio()
                for (song in scannedSongs) {
                    val dedupeKey = song.filePath ?: song.contentUri ?: song.id
                    if (seenKeys.add(dedupeKey)) {
                        songsList.add(song)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        songsList
    }

    private fun scanMediaStoreAudio(): List<Song> {
        val list = mutableListOf<Song>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DISPLAY_NAME
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val displayNameCol = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val displayName = if (displayNameCol >= 0) cursor.getString(displayNameCol) else null
                val path = if (dataCol >= 0) cursor.getString(dataCol) else null

                // Filter for supported MP3 files when filename/path is known
                val nameToCheck = displayName ?: path ?: ""
                if (nameToCheck.isNotEmpty() && !nameToCheck.endsWith(".mp3", ignoreCase = true)) {
                    continue
                }

                val rawTitle = cursor.getString(titleCol)
                    ?: displayName?.substringBeforeLast(".")
                    ?: "Unknown Track"
                val rawArtist = cursor.getString(artistCol) ?: "Elyzareth"
                val album = cursor.getString(albumCol) ?: "Unknown Album"
                val duration = cursor.getLong(durationCol)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                ).toString()

                val (baseTitle, versionName) = SongGrouper.parseSongInfo(rawTitle, rawArtist)
                val songId = "mediastore_$id"
                val customArt = artworkManager.getArtworkUriForSong(songId, baseTitle)
                val (syncedLyrics, plainLyrics) = LyricsManager.loadSidecarLyricsForFile(path)

                list.add(
                    Song(
                        id = songId,
                        songTitle = baseTitle,
                        versionName = versionName,
                        fullTitle = rawTitle,
                        artist = if (rawArtist == "<unknown>") "Elyzareth" else rawArtist,
                        album = album,
                        durationMs = duration,
                        filePath = path,
                        contentUri = contentUri,
                        isAsset = false,
                        customArtworkUri = customArt,
                        lyricsText = plainLyrics,
                        syncedLyrics = syncedLyrics
                    )
                )
            }
        }

        return list
    }

    suspend fun importAudioUri(uri: Uri): Song? = withContext(Dispatchers.IO) {
        // 1. Attempt to take persistable URI permission when supported by the document provider
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Not all URI providers grant persistable permissions; app-private copy below ensures persistence
        } catch (_: Exception) {
        }

        // 2. Copy audio stream into app-private storage so access is guaranteed across restarts & rescans
        val localCopiedFile = copyUriToPrivateStorage(uri)

        val retriever = MediaMetadataRetriever()
        try {
            var metadataExtracted = false
            try {
                if (localCopiedFile != null && localCopiedFile.exists()) {
                    retriever.setDataSource(localCopiedFile.absolutePath)
                    metadataExtracted = true
                } else {
                    retriever.setDataSource(context, uri)
                    metadataExtracted = true
                }
            } catch (_: Exception) {
            }

            val fallbackFileName = queryFileName(uri)
                ?: localCopiedFile?.nameWithoutExtension
                ?: "Imported Song"

            val rawTitle = if (metadataExtracted) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: fallbackFileName
            } else {
                fallbackFileName
            }
            val rawArtist = if (metadataExtracted) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Elyzareth"
            } else {
                "Elyzareth"
            }
            val album = if (metadataExtracted) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: "Imported"
            } else {
                "Imported"
            }
            val durationStr = if (metadataExtracted) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            } else {
                null
            }
            val duration = durationStr?.toLongOrNull() ?: 0L

            // Verify the source is accessible (either copied file exists or URI stream can be opened)
            val isAccessible = (localCopiedFile != null && localCopiedFile.exists() && localCopiedFile.length() > 0L) ||
                canOpenUri(uri)
            if (!isAccessible) {
                return@withContext null
            }

            val (baseTitle, versionName) = SongGrouper.parseSongInfo(rawTitle, rawArtist)
            val songId = "imported_${uri.toString().hashCode()}"
            val customArt = artworkManager.getArtworkUriForSong(songId, baseTitle)
            val (syncedLyrics, plainLyrics) = LyricsManager.loadSidecarLyricsForFile(
                localCopiedFile?.absolutePath ?: uri.path
            )

            val song = Song(
                id = songId,
                songTitle = baseTitle,
                versionName = versionName,
                fullTitle = rawTitle,
                artist = rawArtist,
                album = album,
                durationMs = duration,
                filePath = localCopiedFile?.absolutePath,
                contentUri = uri.toString(),
                isAsset = false,
                customArtworkUri = customArt,
                lyricsText = plainLyrics,
                syncedLyrics = syncedLyrics
            )

            persistImportedSong(song)
            song
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun canOpenUri(uri: Uri): Boolean {
        return try {
            if (uri.scheme == "file") {
                val f = uri.path?.let { File(it) }
                f != null && f.exists() && f.canRead()
            } else {
                context.contentResolver.openInputStream(uri)?.use { true } ?: false
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun copyUriToPrivateStorage(uri: Uri): File? {
        return try {
            val dir = File(context.filesDir, "imported_audio").apply { mkdirs() }
            val displayName = queryFileName(uri) ?: "track_${uri.toString().hashCode()}"
            val safeName = displayName.replace("[^a-zA-Z0-9._ -]".toRegex(), "_")
            val fileName = if (safeName.endsWith(".mp3", ignoreCase = true)) safeName else "$safeName.mp3"
            val targetFile = File(dir, "${uri.toString().hashCode()}_$fileName")

            val inputStream = if (uri.scheme == "file") {
                val srcFile = uri.path?.let { File(it) } ?: return null
                if (!srcFile.exists()) return null
                srcFile.inputStream()
            } else {
                context.contentResolver.openInputStream(uri) ?: return null
            }

            inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (targetFile.exists() && targetFile.length() > 0L) targetFile else null
        } catch (_: Exception) {
            null
        }
    }

    private fun persistImportedSong(song: Song) {
        val currentJson = importedPrefs.getString("imported_songs_json", "[]") ?: "[]"
        val array = try {
            JSONArray(currentJson)
        } catch (_: Exception) {
            JSONArray()
        }

        val updatedArray = JSONArray()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            if (obj.optString("id") != song.id &&
                obj.optString("contentUri") != (song.contentUri ?: "")
            ) {
                updatedArray.put(obj)
            }
        }

        val songObj = JSONObject().apply {
            put("id", song.id)
            put("songTitle", song.songTitle)
            put("versionName", song.versionName)
            put("fullTitle", song.fullTitle)
            put("artist", song.artist)
            put("album", song.album)
            put("durationMs", song.durationMs)
            put("filePath", song.filePath ?: "")
            put("contentUri", song.contentUri ?: "")
        }
        updatedArray.put(songObj)

        importedPrefs.edit().putString("imported_songs_json", updatedArray.toString()).apply()
    }

    fun loadPersistedImportedSongs(): List<Song> {
        val currentJson = importedPrefs.getString("imported_songs_json", "[]") ?: "[]"
        val array = try {
            JSONArray(currentJson)
        } catch (_: Exception) {
            return emptyList()
        }

        val list = mutableListOf<Song>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val id = obj.optString("id")
            val songTitle = obj.optString("songTitle")
            val versionName = obj.optString("versionName", "Original")
            val fullTitle = obj.optString("fullTitle", songTitle)
            val artist = obj.optString("artist", "Elyzareth")
            val album = obj.optString("album", "Imported")
            val durationMs = obj.optLong("durationMs", 0L)
            val filePath = obj.optString("filePath").takeIf { it.isNotEmpty() }
            val contentUri = obj.optString("contentUri").takeIf { it.isNotEmpty() }

            // Verify physical file or URI is still accessible
            val fileExists = filePath != null && File(filePath).exists()
            val uriAccessible = contentUri != null && canOpenUri(Uri.parse(contentUri))
            if (!fileExists && !uriAccessible) continue

            val customArt = artworkManager.getArtworkUriForSong(id, songTitle)
            val (syncedLyrics, plainLyrics) = LyricsManager.loadSidecarLyricsForFile(filePath)

            list.add(
                Song(
                    id = id,
                    songTitle = songTitle,
                    versionName = versionName,
                    fullTitle = fullTitle,
                    artist = artist,
                    album = album,
                    durationMs = durationMs,
                    filePath = if (fileExists) filePath else null,
                    contentUri = contentUri,
                    isAsset = false,
                    customArtworkUri = customArt,
                    lyricsText = plainLyrics,
                    syncedLyrics = syncedLyrics
                )
            )
        }
        return list
    }

    private fun queryFileName(uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val index = it.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                        if (index >= 0) {
                            name = it.getString(index)?.substringBeforeLast(".")
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        if (name == null) {
            name = uri.path?.let { File(it).nameWithoutExtension }
        }
        return name
    }
}
