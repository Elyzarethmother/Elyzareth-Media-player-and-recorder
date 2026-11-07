package com.example.recorder

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExportedVideo(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val formattedDate: String,
    val uri: Uri
)

class ExportManager(private val context: Context) {
    val exportDir: File
        get() {
            val dir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES)
                ?: File(context.filesDir, "exported_videos")
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
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, exportedVideo.uri)
            putExtra(Intent.EXTRA_SUBJECT, "Elyzareth Music Video: ${exportedVideo.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(shareIntent, "Share Elyzareth Video").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun playVideo(exportedVideo: ExportedVideo) {
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(exportedVideo.uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(viewIntent)
    }
}
