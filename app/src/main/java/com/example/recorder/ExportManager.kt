package com.example.recorder

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.data.model.Song
import com.example.visualizer.SynthwaveCompositionRenderer
import com.example.visualizer.VisualCompositionConfig
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExportedVideo(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val formattedDate: String,
    val uri: Uri,
    val mediaStoreUri: Uri? = null
)

data class ExportedImage(
    val file: File,
    val name: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val formattedDate: String,
    val uri: Uri,
    val mediaStoreUri: Uri? = null,
    val displayLocation: String = "Pictures/Elyzareth"
)

class ExportManager(private val context: Context) {
    val exportDir: File
        get() {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?: File(context.filesDir, "exported_videos")
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    val imageExportDir: File
        get() {
            val dir = File(
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
                    ?: File(context.filesDir, "exported_pictures"),
                "Elyzareth"
            )
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    fun createOutputFile(songTitle: String, isPortrait: Boolean): File {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val cleanTitle = songTitle.replace("[^a-zA-Z0-9_]".toRegex(), "_").take(24)
        val formatSuffix = if (isPortrait) "Shorts_720x1280" else "Standard_1280x720"
        val fileName = "Elyzareth_${cleanTitle}_${formatSuffix}_$timestamp.mp4"
        return File(exportDir, fileName)
    }

    /**
     * Renders and exports a PNG frame using the shared SynthwaveCompositionRenderer.
     * Saves both to Android MediaStore (Pictures/Elyzareth, visible in device Gallery/Files)
     * and to local file storage for instant preview/sharing and deterministic verification.
     */
    fun exportPngFrame(
        song: Song?,
        songs: List<Song>,
        config: VisualCompositionConfig,
        artworkBitmap: Bitmap?,
        wallpaperBitmap: Bitmap?,
        songTimeMs: Long,
        frequencies: FloatArray,
        waveform: FloatArray = FloatArray(0),
        isPortrait: Boolean = false,
        showLyrics: Boolean = true
    ): ExportedImage? {
        val width = if (isPortrait) 720 else 1280
        val height = if (isPortrait) 1280 else 720
        return try {
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            SynthwaveCompositionRenderer.renderFrame(
                canvas = canvas,
                width = width,
                height = height,
                song = song,
                songs = songs,
                config = config,
                artworkBitmap = artworkBitmap,
                wallpaperBitmap = wallpaperBitmap,
                songTimeMs = songTimeMs,
                frequencies = frequencies,
                waveform = waveform,
                showLyrics = showLyrics
            )

            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val rawTitle = song?.songTitle ?: "Open_Sky"
            val cleanTitle = rawTitle.replace("[^a-zA-Z0-9_]".toRegex(), "_").take(24)
            val formatSuffix = if (isPortrait) "720x1280" else "1280x720"
            val fileName = "Elyzareth_Frame_${cleanTitle}_${formatSuffix}_$timestamp.png"

            // 1. Always write local file mirror
            val localFile = File(imageExportDir, fileName)
            FileOutputStream(localFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.flush()
            }

            // 2. Publish to public Android MediaStore (Pictures/Elyzareth) so it is visible in Gallery/File Manager
            val mediaStoreUri = publishPngToMediaStore(localFile, fileName, width, height)
            val shareUri = mediaStoreUri ?: getShareableUri(localFile)

            ExportedImage(
                file = localFile,
                name = fileName,
                width = width,
                height = height,
                sizeBytes = localFile.length(),
                formattedDate = "Just now",
                uri = shareUri,
                mediaStoreUri = mediaStoreUri,
                displayLocation = "Pictures/Elyzareth/$fileName"
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun publishPngToMediaStore(
        sourceFile: File,
        fileName: String,
        width: Int,
        height: Int
    ): Uri? {
        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.WIDTH, width)
                put(MediaStore.Images.Media.HEIGHT, height)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_PICTURES}/Elyzareth"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val itemUri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(itemUri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val completeValues = ContentValues().apply {
                    put(MediaStore.Images.Media.IS_PENDING, 0)
                }
                resolver.update(itemUri, completeValues, null, null)
            }
            itemUri
        } catch (_: Exception) {
            null
        }
    }

    fun publishVideoToMediaStore(sourceFile: File): Uri? {
        if (!sourceFile.exists() || sourceFile.length() <= 0L) return null
        return try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, sourceFile.name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        "${Environment.DIRECTORY_MOVIES}/Elyzareth"
                    )
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }
            val itemUri = resolver.insert(collection, values) ?: return null
            resolver.openOutputStream(itemUri)?.use { out ->
                FileInputStream(sourceFile).use { input ->
                    input.copyTo(out)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val completeValues = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                resolver.update(itemUri, completeValues, null, null)
            }
            itemUri
        } catch (_: Exception) {
            null
        }
    }

    fun listExportedVideos(): List<ExportedVideo> {
        val files = exportDir.listFiles { f -> f.isFile && f.name.endsWith(".mp4", ignoreCase = true) }
            ?: return emptyList()

        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
        return files.sortedByDescending { it.lastModified() }.map { file ->
            val uri = getShareableUri(file)
            ExportedVideo(
                file = file,
                name = file.name,
                sizeBytes = file.length(),
                formattedDate = dateFormat.format(Date(file.lastModified())),
                uri = uri
            )
        }
    }

    fun getShareableUri(file: File): Uri {
        return try {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
        } catch (e: Exception) {
            Uri.fromFile(file)
        }
    }

    fun shareVideo(exportedVideo: ExportedVideo) {
        val targetUri = exportedVideo.mediaStoreUri ?: getShareableUri(exportedVideo.file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, targetUri)
            putExtra(Intent.EXTRA_SUBJECT, "Elyzareth Music Video: ${exportedVideo.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Elyzareth Video").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun playVideo(exportedVideo: ExportedVideo) {
        val targetUri = exportedVideo.mediaStoreUri ?: getShareableUri(exportedVideo.file)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(targetUri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(viewIntent)
    }

    fun shareImage(exportedImage: ExportedImage) {
        val targetUri = exportedImage.mediaStoreUri ?: getShareableUri(exportedImage.file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, targetUri)
            putExtra(Intent.EXTRA_SUBJECT, "Elyzareth Frame: ${exportedImage.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Elyzareth PNG Frame").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun viewImage(exportedImage: ExportedImage) {
        val targetUri = exportedImage.mediaStoreUri ?: getShareableUri(exportedImage.file)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(targetUri, "image/png")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(viewIntent)
    }
}
