package com.example.data.model

data class LyricLine(
    val timestampMs: Long,
    val text: String
)

data class Song(
    val id: String,
    val songTitle: String,
    val versionName: String,
    val fullTitle: String,
    val artist: String = "Elyzareth",
    val album: String = "Elyzareth Collection",
    val durationMs: Long = 0L,
    val filePath: String? = null,
    val contentUri: String? = null,
    val isAsset: Boolean = false,
    val assetPath: String? = null,
    val customArtworkUri: String? = null,
    val lyricsText: String? = null,
    val syncedLyrics: List<LyricLine> = emptyList()
) {
    val displaySubtitle: String
        get() = "$artist • $versionName"
}

data class SongGroup(
    val baseTitle: String,
    val artist: String,
    val versions: List<Song>
)
